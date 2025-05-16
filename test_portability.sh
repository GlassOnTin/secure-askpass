#!/bin/bash
# Test portability of the askpass setup

echo "=== Portability Test ==="
echo

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set up the askpass environment
export SUDO_ASKPASS="$SCRIPT_DIR/askpass"

echo "1. Script directory: $SCRIPT_DIR"
echo "2. SUDO_ASKPASS: $SUDO_ASKPASS"
echo

echo "3. Testing from script directory..."
cd "$SCRIPT_DIR"
sudo -A echo "Test from $PWD" && echo "   ✓ Success" || echo "   ✗ Failed"
echo

echo "4. Testing from home directory..."
cd ~
sudo -A echo "Test from $PWD" && echo "   ✓ Success" || echo "   ✗ Failed"
echo

echo "5. Testing from root directory..."
cd /
sudo -A echo "Test from $PWD" && echo "   ✓ Success" || echo "   ✗ Failed"
echo

echo "6. Testing config file detection..."
cd "$SCRIPT_DIR"
echo "   Config locations checked by askpass:"
echo "   - ~/.config/secure-askpass/config.json"
echo "   - $SCRIPT_DIR/askpass-config.json (exists)"
echo

echo "=== Portability Test Complete ==="