#!/bin/bash
echo "=== Testing SSH Askpass GUI Dialog ==="
echo

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# Set up the askpass environment
export SUDO_ASKPASS="$SCRIPT_DIR/askpass"

# Test with GUI enabled (default)
echo "Test 1: With GUI confirmation enabled"
sudo -A echo "GUI test successful" && echo "✓ User approved the dialog" || echo "✗ User denied the dialog"
echo

# Temporarily disable GUI for automation
echo "Test 2: Without GUI confirmation (for automation)"
cat > "$SCRIPT_DIR/askpass-config-nogui.json" << EOF
{
    "require_user_confirmation": false,
    "allowed_paths": ["/home/ian/", "/tmp/"],
    "expiration_hours": 24,
    "allowed_processes": ["sudo", "claude-code", "code", "bash", "sh"]
}
EOF

# Backup current config
cp "$SCRIPT_DIR/askpass-config.json" "$SCRIPT_DIR/askpass-config.json.bak"
cp "$SCRIPT_DIR/askpass-config-nogui.json" "$SCRIPT_DIR/askpass-config.json"

sudo -A echo "No GUI test successful" && echo "✓ Command executed without dialog" || echo "✗ Command failed"

# Restore original config
cp "$SCRIPT_DIR/askpass-config.json.bak" "$SCRIPT_DIR/askpass-config.json"
rm "$SCRIPT_DIR/askpass-config-nogui.json"
rm "$SCRIPT_DIR/askpass-config.json.bak"

echo
echo "=== GUI Dialog Test Complete ==="
echo
echo "Configuration file: askpass-config.json"
echo "Set 'require_user_confirmation' to false to disable GUI prompts"