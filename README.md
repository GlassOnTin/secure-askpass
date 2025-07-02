# SSH Askpass Helper

A secure askpass implementation using SSH key encryption for non-interactive sudo operations.

## Installation

1. Clone this repository
2. Ensure you have SSH keys (supports ed25519, ecdsa, rsa, or dsa)
3. Set the `SUDO_ASKPASS` environment variable in your shell configuration:
   ```bash
   export SUDO_ASKPASS="/path/to/secure-askpass/askpass"
   ```
4. Store your sudo password:
   ```bash
   ./askpass-manager set
   ```

## Usage

```bash
export SUDO_ASKPASS="/path/to/askpass"
sudo -A command
```

Or use the test command:
```bash
./askpass-manager test
```

## Security

- Passwords are encrypted with your SSH public key
- Supports multiple SSH key types (ed25519, ecdsa, rsa, dsa) with automatic detection
- Keys are checked in order of preference: ed25519 > ecdsa > rsa > dsa
- Stored in `~/.sudo_askpass.ssh` with 600 permissions
- Falls back to system keyring if available
- Refuses plain text storage

## Commands

```bash
./askpass-manager set   # Store password
./askpass-manager get   # Check if password exists
./askpass-manager clear # Remove password
./askpass-manager test  # Test sudo integration
```

## License

MIT License - see LICENSE file