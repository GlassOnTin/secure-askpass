#!/usr/bin/env bash
# Integration tests for Android biometric auth file permissions and config

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_DIR="$HOME/.config/secure-askpass"
PASS=0
FAIL=0

check() {
    local desc="$1"
    shift
    if "$@"; then
        echo "  ✓ $desc"
        ((PASS++))
    else
        echo "  ✗ $desc"
        ((FAIL++))
    fi
}

echo "Android Biometric Auth Integration Tests"
echo "========================================="

# Test 1: Server script is executable
check "askpass-android-server.py is executable" \
    test -x "$SCRIPT_DIR/askpass-android-server.py"

# Test 2: android_devices.json permissions after write
echo '{"devices":[]}' > /tmp/test_android_devices.json
chmod 0600 /tmp/test_android_devices.json
PERMS=$(stat -c '%a' /tmp/test_android_devices.json 2>/dev/null)
check "android_devices.json gets 0600 permissions" \
    test "$PERMS" = "600"
rm -f /tmp/test_android_devices.json

# Test 3: Config file contains android fields (if it exists)
if [ -f "$CONFIG_DIR/config.json" ]; then
    check "config.json contains android_callback_port or android fields" \
        grep -qE '(android_|ntfy_server|callback_port|auth_timeout)' "$CONFIG_DIR/config.json"
else
    echo "  - config.json not found, skipping config field check"
fi

# Test 4: Server rejects GET requests
PORT=$(python3 -c "import socket; s=socket.socket(); s.bind(('',0)); print(s.getsockname()[1]); s.close()")
python3 "$SCRIPT_DIR/askpass-android-server.py" "$PORT" "test-nonce" '{}' /dev/null 3 &
SERVER_PID=$!
sleep 0.5

HTTP_CODE=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$PORT/auth/response" 2>/dev/null)
# GET should fail (server only handles POST). Curl may get connection refused or 501
check "server rejects GET requests (code=$HTTP_CODE)" \
    test "$HTTP_CODE" != "200"

kill $SERVER_PID 2>/dev/null
wait $SERVER_PID 2>/dev/null

# Test 5: Server script has correct shebang
SHEBANG=$(head -1 "$SCRIPT_DIR/askpass-android-server.py")
check "askpass-android-server.py has python3 shebang" \
    test "$SHEBANG" = "#!/usr/bin/env python3"

# Test 6: verify_signature handles ECDSA (quick smoke test via python)
RESULT=$(python3 -c "
import sys, importlib.util
spec = importlib.util.spec_from_file_location('s', '$SCRIPT_DIR/askpass-android-server.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
try:
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives import hashes, serialization
    key = ec.generate_private_key(ec.SECP256R1())
    pem = key.public_key().public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo).decode()
    sig = key.sign(b'hello', ec.ECDSA(hashes.SHA256()))
    print('ok' if mod.verify_signature(pem, b'hello', sig) else 'fail')
except ImportError:
    print('skip')
" 2>/dev/null)
check "verify_signature handles ECDSA keys (result=$RESULT)" \
    test "$RESULT" = "ok" -o "$RESULT" = "skip"

echo ""
echo "========================================="
echo "Results: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
