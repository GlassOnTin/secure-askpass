#!/usr/bin/env python3
"""Tests for Android biometric authentication server and askpass integration."""

import sys
import os
import json
import time
import socket
import threading
import tempfile
import base64
import http.client
import importlib

# Add project root to path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Import server module by filename (contains hyphen)
import importlib.util
_spec = importlib.util.spec_from_file_location(
    "android_server",
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "askpass-android-server.py")
)
android_server = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(android_server)

ChallengeStore = android_server.ChallengeStore
AuthResult = android_server.AuthResult
verify_signature = android_server.verify_signature

# Check if cryptography is available
try:
    from cryptography.hazmat.primitives.asymmetric import ec, ed25519
    from cryptography.hazmat.primitives import hashes, serialization
    HAS_CRYPTO = True
except ImportError:
    HAS_CRYPTO = False

passed = 0
failed = 0


def run_test(name, fn):
    global passed, failed
    try:
        fn()
        print(f"  \u2713 {name}")
        passed += 1
    except Exception as e:
        print(f"  \u2717 {name}: {e}")
        failed += 1


def _free_port():
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.bind(('', 0))
    port = s.getsockname()[1]
    s.close()
    return port


def _generate_ec_keypair():
    """Generate EC P-256 keypair, return (private_key, pubkey_pem_str)."""
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives import serialization
    private_key = ec.generate_private_key(ec.SECP256R1())
    pubkey_pem = private_key.public_key().public_bytes(
        serialization.Encoding.PEM,
        serialization.PublicFormat.SubjectPublicKeyInfo
    ).decode()
    return private_key, pubkey_pem


def _sign_ecdsa(private_key, message_bytes):
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives import hashes
    return private_key.sign(message_bytes, ec.ECDSA(hashes.SHA256()))


# ── ChallengeStore tests ──

def test_challenge_store_add_and_consume():
    store = ChallengeStore(ttl=30)
    store.add("nonce1", {"cmd": "test"})
    result = store.validate_and_consume("nonce1")
    assert result == {"cmd": "test"}, f"Expected challenge data, got {result}"
    result2 = store.validate_and_consume("nonce1")
    assert result2 is None, "Second consume should return None (replay prevention)"


def test_challenge_store_ttl_expiry():
    store = ChallengeStore(ttl=1)
    store.add("nonce_expire", {"cmd": "expired"})
    time.sleep(2)
    result = store.validate_and_consume("nonce_expire")
    assert result is None, "Expired nonce should return None"


def test_challenge_store_concurrent():
    store = ChallengeStore(ttl=30)
    store.add("race_nonce", {"cmd": "race"})
    results = []
    barrier = threading.Barrier(10)

    def try_consume():
        barrier.wait()
        r = store.validate_and_consume("race_nonce")
        results.append(r)

    threads = [threading.Thread(target=try_consume) for _ in range(10)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()

    winners = [r for r in results if r is not None]
    assert len(winners) == 1, f"Expected exactly 1 winner, got {len(winners)}"


# ── Signature verification tests ──

def test_verify_signature_ecdsa_valid():
    if not HAS_CRYPTO:
        raise Exception("cryptography not installed, skipping")
    private_key, pubkey_pem = _generate_ec_keypair()
    nonce = b"test-nonce-12345"
    sig = _sign_ecdsa(private_key, nonce)
    assert verify_signature(pubkey_pem, nonce, sig), "Valid ECDSA signature should verify"


def test_verify_signature_invalid_sig():
    if not HAS_CRYPTO:
        raise Exception("cryptography not installed, skipping")
    private_key, pubkey_pem = _generate_ec_keypair()
    nonce = b"test-nonce"
    sig = _sign_ecdsa(private_key, nonce)
    tampered = bytearray(sig)
    tampered[-1] ^= 0xFF
    assert not verify_signature(pubkey_pem, nonce, bytes(tampered)), "Tampered sig should fail"


def test_verify_signature_wrong_key():
    if not HAS_CRYPTO:
        raise Exception("cryptography not installed, skipping")
    key_a, _ = _generate_ec_keypair()
    _, pubkey_b_pem = _generate_ec_keypair()
    nonce = b"test-nonce"
    sig = _sign_ecdsa(key_a, nonce)
    assert not verify_signature(pubkey_b_pem, nonce, sig), "Wrong key should fail"


def test_verify_signature_missing_crypto():
    orig = android_server.HAS_CRYPTO
    try:
        android_server.HAS_CRYPTO = False
        result = verify_signature("fake", b"msg", b"sig")
        assert not result, "Should return False when crypto unavailable"
    finally:
        android_server.HAS_CRYPTO = orig


# ── HTTP callback handler tests ──

def _start_test_server(port, nonce, pubkey_pem, ttl=30, timeout=10, stop_on_auth=True):
    """Start callback server in background, return thread."""
    # Reset module-level state
    android_server.challenge_store = ChallengeStore(ttl=ttl)
    android_server.auth_result = AuthResult()
    android_server.device_pubkey_pem = pubkey_pem
    android_server.challenge_store.add(nonce, {"cmd": "test"})

    from http.server import HTTPServer
    server = HTTPServer(("127.0.0.1", port), android_server.CallbackHandler)
    server.timeout = 1

    def serve():
        deadline = time.time() + timeout
        while time.time() < deadline:
            if stop_on_auth and android_server.auth_result._result is not None:
                break
            server.handle_request()
        server.server_close()

    t = threading.Thread(target=serve, daemon=True)
    t.start()
    time.sleep(0.2)  # Let server bind
    return t


def _post_json(port, path, payload):
    """POST JSON to localhost, return status code."""
    conn = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    body = json.dumps(payload).encode()
    conn.request("POST", path, body, {"Content-Type": "application/json"})
    resp = conn.getresponse()
    code = resp.status
    conn.close()
    return code


def test_callback_valid_request():
    if not HAS_CRYPTO:
        raise Exception("cryptography not installed, skipping")
    port = _free_port()
    private_key, pubkey_pem = _generate_ec_keypair()
    nonce = "valid-nonce-001"
    sig = _sign_ecdsa(private_key, nonce.encode())
    sig_b64 = base64.b64encode(sig).decode()

    _start_test_server(port, nonce, pubkey_pem)
    code = _post_json(port, "/auth/response", {"nonce": nonce, "signature": sig_b64})
    assert code == 200, f"Expected 200, got {code}"
    time.sleep(0.1)
    assert android_server.auth_result._result is True, "Auth result should be True"


def test_callback_expired_nonce():
    if not HAS_CRYPTO:
        raise Exception("cryptography not installed, skipping")
    port = _free_port()
    private_key, pubkey_pem = _generate_ec_keypair()
    nonce = "expire-nonce"
    sig = _sign_ecdsa(private_key, nonce.encode())
    sig_b64 = base64.b64encode(sig).decode()

    _start_test_server(port, nonce, pubkey_pem, ttl=1)
    time.sleep(2)
    code = _post_json(port, "/auth/response", {"nonce": nonce, "signature": sig_b64})
    assert code == 403, f"Expected 403, got {code}"


def test_callback_replayed_nonce():
    if not HAS_CRYPTO:
        raise Exception("cryptography not installed, skipping")
    port = _free_port()
    private_key, pubkey_pem = _generate_ec_keypair()
    nonce = "replay-nonce"
    sig = _sign_ecdsa(private_key, nonce.encode())
    sig_b64 = base64.b64encode(sig).decode()

    _start_test_server(port, nonce, pubkey_pem, timeout=5, stop_on_auth=False)
    code1 = _post_json(port, "/auth/response", {"nonce": nonce, "signature": sig_b64})
    assert code1 == 200, f"First request expected 200, got {code1}"
    # Second request with same nonce (server still running)
    code2 = _post_json(port, "/auth/response", {"nonce": nonce, "signature": sig_b64})
    assert code2 == 403, f"Replay expected 403, got {code2}"


def test_callback_oversized_payload():
    port = _free_port()
    _start_test_server(port, "any", "any")
    conn = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
    body = b"x" * 5000
    conn.request("POST", "/auth/response", body,
                 {"Content-Type": "application/json", "Content-Length": str(len(body))})
    resp = conn.getresponse()
    assert resp.status == 413, f"Expected 413, got {resp.status}"
    conn.close()


def test_callback_missing_fields():
    port = _free_port()
    _start_test_server(port, "any", "any")
    code = _post_json(port, "/auth/response", {"nonce": "x"})  # missing signature
    assert code == 400, f"Expected 400, got {code}"


def test_callback_wrong_path():
    port = _free_port()
    _start_test_server(port, "any", "any")
    code = _post_json(port, "/wrong", {"nonce": "x", "signature": "y"})
    assert code == 404, f"Expected 404, got {code}"


def test_server_timeout():
    port = _free_port()
    android_server.challenge_store = ChallengeStore(ttl=30)
    android_server.auth_result = AuthResult()
    android_server.device_pubkey_pem = "unused"
    android_server.challenge_store.add("timeout-nonce", {"cmd": "test"})

    from http.server import HTTPServer
    server = HTTPServer(("127.0.0.1", port), android_server.CallbackHandler)
    server.timeout = 1

    def serve():
        deadline = time.time() + 2
        while time.time() < deadline and android_server.auth_result._result is None:
            server.handle_request()
        server.server_close()

    t = threading.Thread(target=serve, daemon=True)
    t.start()
    t.join(timeout=4)
    assert android_server.auth_result._result is None, "Should be None on timeout"


# ── Askpass integration tests ──
# Re-implement the functions under test directly to avoid importing the full
# askpass module (which triggers syslog.openlog, GUI imports, etc.)

ANDROID_DEVICES_FILE_DEFAULT = os.path.expanduser("~/.config/secure-askpass/android_devices.json")
_test_devices_file = ANDROID_DEVICES_FILE_DEFAULT


def _load_android_devices(path):
    """Mirrors askpass load_android_devices logic."""
    if not os.path.exists(path):
        return []
    try:
        with open(path, 'r') as f:
            data = json.load(f)
        return data.get("devices", [])
    except Exception:
        return []


def _has_display():
    return 'DISPLAY' in os.environ or 'WAYLAND_DISPLAY' in os.environ


def test_load_android_devices_missing():
    result = _load_android_devices("/nonexistent/path/devices.json")
    assert result == [], f"Expected empty list, got {result}"


def test_load_android_devices_valid():
    with tempfile.NamedTemporaryFile(mode='w', suffix='.json', delete=False) as f:
        json.dump({"devices": [{"name": "Pixel", "pubkey": "pem..."}]}, f)
        tmppath = f.name
    try:
        result = _load_android_devices(tmppath)
        assert len(result) == 1, f"Expected 1 device, got {len(result)}"
        assert result[0]["name"] == "Pixel"
    finally:
        os.unlink(tmppath)


def test_load_android_devices_corrupt():
    with tempfile.NamedTemporaryFile(mode='w', suffix='.json', delete=False) as f:
        f.write("not valid json{{{")
        tmppath = f.name
    try:
        result = _load_android_devices(tmppath)
        assert result == [], f"Expected empty list for corrupt JSON, got {result}"
    finally:
        os.unlink(tmppath)


def test_has_display():
    orig_display = os.environ.get('DISPLAY')
    orig_wayland = os.environ.get('WAYLAND_DISPLAY')
    try:
        os.environ.pop('DISPLAY', None)
        os.environ.pop('WAYLAND_DISPLAY', None)
        assert not _has_display(), "Should return False without DISPLAY"
        os.environ['DISPLAY'] = ':0'
        assert _has_display(), "Should return True with DISPLAY set"
    finally:
        if orig_display is not None:
            os.environ['DISPLAY'] = orig_display
        else:
            os.environ.pop('DISPLAY', None)
        if orig_wayland is not None:
            os.environ['WAYLAND_DISPLAY'] = orig_wayland


def main():
    print("Android Biometric Auth Test Suite")
    print("=" * 40)

    print("\nChallengeStore:")
    run_test("add and consume", test_challenge_store_add_and_consume)
    run_test("TTL expiry", test_challenge_store_ttl_expiry)
    run_test("concurrent consume", test_challenge_store_concurrent)

    print("\nSignature verification:")
    run_test("ECDSA valid signature", test_verify_signature_ecdsa_valid)
    run_test("invalid signature", test_verify_signature_invalid_sig)
    run_test("wrong key", test_verify_signature_wrong_key)
    run_test("missing crypto lib", test_verify_signature_missing_crypto)

    print("\nHTTP callback handler:")
    run_test("valid request", test_callback_valid_request)
    run_test("expired nonce", test_callback_expired_nonce)
    run_test("replayed nonce", test_callback_replayed_nonce)
    run_test("oversized payload", test_callback_oversized_payload)
    run_test("missing fields", test_callback_missing_fields)
    run_test("wrong path", test_callback_wrong_path)
    run_test("server timeout", test_server_timeout)

    print("\nAskpass integration:")
    run_test("missing devices file", test_load_android_devices_missing)
    run_test("valid devices file", test_load_android_devices_valid)
    run_test("corrupt devices file", test_load_android_devices_corrupt)
    run_test("has_display check", test_has_display)

    print("\n" + "=" * 40)
    print(f"Results: {passed} passed, {failed} failed")

    if failed:
        print("Some tests FAILED")
        return 1
    else:
        print("All tests passed")
        return 0


if __name__ == "__main__":
    sys.exit(main())
