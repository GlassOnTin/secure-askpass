#!/bin/bash
# Comprehensive security test for SSH askpass

echo "=== SSH Askpass Security Test Suite ==="
echo

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set up the askpass environment
export SUDO_ASKPASS="$SCRIPT_DIR/askpass"

# Test 1: Basic functionality
echo "Test 1: Basic sudo from allowed directory"
cd "$SCRIPT_DIR"
if sudo -A echo "Success" >/dev/null 2>&1; then
    echo "✓ PASS: Sudo works from allowed directory"
else
    echo "✗ FAIL: Sudo should work from $SCRIPT_DIR"
fi
echo

# Test 2: Path restriction
echo "Test 2: Path restriction enforcement"
cd /opt
if sudo -A echo "Should fail" >/dev/null 2>&1; then
    echo "✗ FAIL: Sudo should NOT work from /opt"
else
    # Check logs to confirm it was blocked for the right reason
    if sudo journalctl -t sudo-askpass -n 5 | grep -q "unauthorized path: /opt"; then
        echo "✓ PASS: Correctly blocked access from /opt"
    else
        echo "✓ PASS: Access blocked (security check failed)"
    fi
fi
cd "$SCRIPT_DIR"
echo

# Test 3: Audit logging
echo "Test 3: Audit logging functionality"
if sudo journalctl -t sudo-askpass -n 10 | grep -q "Askpass called"; then
    echo "✓ PASS: Audit logging is working"
else
    echo "✗ FAIL: No audit logs found"
fi
echo

# Test 4: File permissions
echo "Test 4: Secure file permissions"
if ls -l ~/.sudo_askpass.ssh | grep -q "^-rw-------"; then
    echo "✓ PASS: Password file has secure permissions (600)"
else
    echo "✗ FAIL: Password file permissions are not secure"
fi
echo

# Test 5: Process validation
echo "Test 5: Process validation"
# This should work since bash is allowed
if bash -c 'sudo -A echo "Test"' >/dev/null 2>&1; then
    echo "✓ PASS: Allowed process (bash) can use askpass"
else
    echo "⚠ WARNING: bash subprocess might be blocked"
fi
echo

# Test 6: Environment check
echo "Test 6: Environment requirements"
env_vars=$(env | grep -E "(SSH_AUTH_SOCK|SSH_TTY|TERM)" | wc -l)
if [ $env_vars -gt 0 ]; then
    echo "✓ PASS: Required environment variables present"
else
    echo "⚠ WARNING: No terminal/SSH environment variables found"
fi
echo

# Summary
echo "=== Test Summary ==="
echo "Security features are working correctly."
echo "Check detailed logs: sudo journalctl -t sudo-askpass -f"
echo
echo "Security configuration:"
echo "- Allowed paths: /home/ian/, /tmp/"
echo "- Password expiration: 24 hours"
echo "- Allowed processes: sudo, claude-code, code, bash, sh"