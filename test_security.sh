#!/bin/bash
# Test script for enhanced askpass security features

echo "=== Testing SSH Askpass Security Features ==="
echo

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set up the askpass environment
export SUDO_ASKPASS="$SCRIPT_DIR/askpass"

# Test 1: Basic functionality from allowed directory
echo "Test 1: Basic sudo command from allowed directory"
cd "$SCRIPT_DIR"
sudo -A echo "✓ Test 1 passed: Basic sudo command works"
echo

# Test 2: Try from restricted directory
echo "Test 2: Attempt from restricted directory (should fail)"
cd /opt
if sudo -A echo "Test from /opt" 2>/dev/null; then
    echo "✗ Test 2 failed: Should not work from /opt"
else
    echo "✓ Test 2 passed: Correctly blocked from /opt"
fi
cd "$SCRIPT_DIR"
echo

# Test 3: Check audit logs
echo "Test 3: Verify audit logging"
sudo journalctl -t sudo-askpass -n 5 --no-pager | grep -q "Askpass called" && echo "✓ Test 3 passed: Audit logs working" || echo "✗ Test 3 failed: No audit logs found"
echo

# Test 4: Check environment requirements
echo "Test 4: Environment variable check"
cd "$SCRIPT_DIR"
env | grep -E "(SSH_AUTH_SOCK|SSH_TTY|TERM)" > /dev/null && echo "✓ Test 4 passed: Required environment present" || echo "⚠ Test 4 warning: Limited environment"
echo

# Test 5: Verify askpass manager functionality
echo "Test 5: Askpass manager test"
"$SCRIPT_DIR/askpass-manager" test && echo "✓ Test 5 passed: Manager test successful" || echo "✗ Test 5 failed: Manager test failed"
echo

# Test 6: Check file permissions
echo "Test 6: Security file permissions"
ls -la ~/.sudo_askpass.ssh | grep -q "^-rw-------" && echo "✓ Test 6 passed: Secure file permissions" || echo "✗ Test 6 failed: Insecure permissions"
echo

# Test 7: Process validation
echo "Test 7: Process validation (simulated)"
python3 -c "import subprocess; subprocess.run(['sudo', '-A', 'true'])" && echo "✓ Test 7 passed: Python subprocess allowed" || echo "✗ Test 7 failed: Python subprocess blocked"
echo

echo "=== Security Test Complete ==="
echo
echo "Check detailed logs with: sudo journalctl -t sudo-askpass -f"