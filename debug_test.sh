#!/bin/bash
# Debug test for path restriction

echo "=== Debug Path Restriction Test ==="
echo

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set up the askpass environment
export SUDO_ASKPASS="$SCRIPT_DIR/askpass"

# Show current environment
echo "Current user: $(whoami)"
echo "Current directory: $(pwd)"
echo "SUDO_ASKPASS: $SUDO_ASKPASS"
echo

# Test from /opt with debug
echo "Testing from /opt directory..."
cd /opt
echo "Changed to: $(pwd)"
echo "Running sudo -A with strace to see what happens..."
strace -e trace=exec,openat sudo -A echo "test from /opt" 2>&1 | grep -E "(askpass|exec)" || true
echo

# Check if askpass was even called
echo "Checking if askpass was called..."
sudo journalctl -t sudo-askpass -n 5 --no-pager | grep "/opt" || echo "No /opt entries found in logs"
echo

# Test direct askpass execution
echo "Testing direct askpass execution from /opt..."
cd /opt
python3 "$SCRIPT_DIR/askpass" 2>&1 || echo "Direct askpass execution blocked as expected"
echo

echo "=== Debug Complete ==="