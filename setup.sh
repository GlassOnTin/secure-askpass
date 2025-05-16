#!/bin/bash
# Setup script for secure-askpass

echo "=== Secure Askpass Setup ==="
echo

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "1. Detected installation directory: $SCRIPT_DIR"
echo

# Check for SSH keys
echo "2. Checking for SSH keys..."
if [ -f ~/.ssh/id_rsa ] && [ -f ~/.ssh/id_rsa.pub ]; then
    echo "   ✓ SSH keys found"
else
    echo "   ✗ SSH keys not found. Please generate SSH keys first:"
    echo "     ssh-keygen -t rsa"
    exit 1
fi
echo

# Make scripts executable
echo "3. Setting executable permissions..."
chmod +x "$SCRIPT_DIR/askpass"
chmod +x "$SCRIPT_DIR/askpass-manager"
chmod +x "$SCRIPT_DIR"/test_*.sh
chmod +x "$SCRIPT_DIR/final_test.sh"
chmod +x "$SCRIPT_DIR/debug_test.sh"
echo "   ✓ Permissions set"
echo

# Suggest environment variable setup
echo "4. Environment setup"
echo "   Add the following to your ~/.bashrc or ~/.bash_profile:"
echo
echo "   export SUDO_ASKPASS=\"$SCRIPT_DIR/askpass\""
echo
echo "   Then reload your shell configuration:"
echo "   source ~/.bashrc"
echo

# Initial password setup
echo "5. Would you like to set up your sudo password now? (y/n)"
read -r response
if [[ "$response" =~ ^[Yy]$ ]]; then
    "$SCRIPT_DIR/askpass-manager" set
fi

echo
echo "=== Setup Complete ==="
echo "To test your setup, run: ./askpass-manager test"