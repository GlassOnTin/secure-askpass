#!/usr/bin/env python3
"""
HTTP callback server for Android biometric authentication.
Launched as a subprocess by askpass, listens for signed challenge responses
from the Android companion app.
"""

import sys
import os
import json
import time
import threading
import signal
import base64
from http.server import HTTPServer, BaseHTTPRequestHandler

# Ed25519 signature verification
try:
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
    from cryptography.hazmat.primitives.serialization import load_pem_public_key
    HAS_CRYPTO = True
except ImportError:
    HAS_CRYPTO = False


class ChallengeStore:
    """Thread-safe store for pending challenges with TTL."""

    def __init__(self, ttl=30):
        self.ttl = ttl
        self._challenges = {}
        self._lock = threading.Lock()

    def add(self, nonce, challenge_data):
        with self._lock:
            self._challenges[nonce] = {
                "data": challenge_data,
                "created": time.time()
            }

    def validate_and_consume(self, nonce):
        """Return challenge data if nonce exists and not expired, then remove it."""
        with self._lock:
            entry = self._challenges.pop(nonce, None)
            if entry is None:
                return None
            if time.time() - entry["created"] > self.ttl:
                return None
            return entry["data"]


class AuthResult:
    """Thread-safe container for the authentication result."""

    def __init__(self):
        self._result = None
        self._event = threading.Event()

    def set(self, value):
        self._result = value
        self._event.set()

    def wait(self, timeout):
        self._event.wait(timeout)
        return self._result


# Module-level state (set before server starts)
challenge_store = ChallengeStore()
auth_result = AuthResult()
device_pubkey_pem = None


def verify_signature(pubkey_pem, message_bytes, signature_bytes):
    """Verify a signature using Ed25519 or ECDSA depending on key type."""
    if not HAS_CRYPTO:
        return False
    try:
        from cryptography.hazmat.primitives.asymmetric import ec, ed25519
        from cryptography.hazmat.primitives import hashes
        pubkey = load_pem_public_key(pubkey_pem.encode())
        if isinstance(pubkey, ed25519.Ed25519PublicKey):
            pubkey.verify(signature_bytes, message_bytes)
        elif isinstance(pubkey, ec.EllipticCurvePublicKey):
            pubkey.verify(signature_bytes, message_bytes, ec.ECDSA(hashes.SHA256()))
        else:
            return False
        return True
    except Exception:
        return False


class CallbackHandler(BaseHTTPRequestHandler):

    def log_message(self, format, *args):
        # Silence default HTTP logging
        pass

    def do_POST(self):
        if self.path != "/auth/response":
            self.send_error(404)
            return

        try:
            content_length = int(self.headers.get("Content-Length", 0))
            if content_length > 4096:
                self.send_error(413)
                return

            body = self.rfile.read(content_length)
            payload = json.loads(body)

            nonce = payload.get("nonce")
            signature_b64 = payload.get("signature")

            if not nonce or not signature_b64:
                self.send_error(400, "Missing nonce or signature")
                return

            # Validate challenge exists and hasn't expired
            challenge_data = challenge_store.validate_and_consume(nonce)
            if challenge_data is None:
                self.send_error(403, "Invalid or expired challenge")
                return

            # Verify signature over the nonce
            signature_bytes = base64.b64decode(signature_b64)
            if not verify_signature(device_pubkey_pem, nonce.encode(), signature_bytes):
                self.send_error(403, "Invalid signature")
                return

            # Auth succeeded
            self._send_json(200, {"status": "ok"})
            auth_result.set(True)

        except (json.JSONDecodeError, KeyError, ValueError):
            self.send_error(400, "Invalid request")

    def _send_json(self, code, obj):
        data = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def run_server(port, nonce, challenge_json, pubkey_pem, timeout=30):
    """
    Start the callback server, register a challenge, and wait for a response.

    Returns True if authenticated, False on timeout/failure.
    """
    global device_pubkey_pem
    device_pubkey_pem = pubkey_pem

    challenge_store.add(nonce, challenge_json)

    server = HTTPServer(("0.0.0.0", port), CallbackHandler)
    server.timeout = 1  # check every second

    # Run server in a thread so we can enforce overall timeout
    def serve():
        deadline = time.time() + timeout
        while time.time() < deadline and auth_result._result is None:
            server.handle_request()

    server_thread = threading.Thread(target=serve, daemon=True)
    server_thread.start()
    server_thread.join(timeout=timeout + 2)

    server.server_close()
    result = auth_result._result
    return result is True


def main():
    """CLI entry point: receives challenge info via argv, prints result."""
    if len(sys.argv) < 5:
        print("Usage: askpass-android-server.py <port> <nonce> <challenge_json> <pubkey_pem_file> [timeout]",
              file=sys.stderr)
        sys.exit(1)

    port = int(sys.argv[1])
    nonce = sys.argv[2]
    challenge_json = sys.argv[3]
    pubkey_pem_file = sys.argv[4]
    timeout = int(sys.argv[5]) if len(sys.argv) > 5 else 30

    with open(pubkey_pem_file, "r") as f:
        pubkey_pem = f.read()

    success = run_server(port, nonce, challenge_json, pubkey_pem, timeout)
    # Print result for parent process to read
    print("AUTH_SUCCESS" if success else "AUTH_TIMEOUT")
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
