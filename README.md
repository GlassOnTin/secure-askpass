# SSH Askpass Helper

A secure askpass implementation using SSH key encryption for non-interactive sudo operations.

## Installation

1. Clone this repository
2. Ensure you have SSH keys (supports ed25519, ecdsa, rsa, or dsa)
3. **For Ed25519 keys**: Install the `age` encryption tool
   - Arch Linux: `sudo pacman -S age`
   - Ubuntu/Debian: `sudo apt install age`
   - macOS: `brew install age`
   - Or build from source: https://github.com/FiloSottile/age#installation
4. Set the `SUDO_ASKPASS` environment variable in your shell configuration:
   ```bash
   export SUDO_ASKPASS="/path/to/secure-askpass/askpass"
   ```
5. Store your sudo password:
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

- Passwords are encrypted with your SSH keys using one of two methods:
  - **Ed25519 keys**: Uses `age` encryption (requires age tool)
  - **RSA/ECDSA/DSA keys**: Uses OpenSSL asymmetric encryption
- Supports multiple SSH key types (ed25519, ecdsa, rsa, dsa) with automatic detection
- Keys are checked in order of preference: ed25519 > ecdsa > rsa > dsa
- Encrypted files stored with 600 permissions:
  - Ed25519: `~/.sudo_askpass.age`
  - RSA/ECDSA/DSA: `~/.sudo_askpass.ssh`
- Falls back to system keyring if available
- Refuses plain text storage

### Why age for Ed25519?

Ed25519 is a signing algorithm (EdDSA), not an encryption algorithm. While OpenSSL can handle RSA encryption directly, Ed25519 keys cannot be used for asymmetric encryption. The `age` tool was specifically designed to work with SSH keys including Ed25519, providing a secure and modern encryption solution.

### Automatic SSH Key Management

If your SSH key is password-protected (recommended!), the askpass tool will:
1. Automatically start ssh-agent if not running
2. Check if your SSH key is loaded
3. Prompt for your SSH key passphrase via GUI dialog if needed
4. Load the key into ssh-agent for the session

**Workflow:** You only enter your SSH key passphrase once per session (via GUI), then sudo commands only require the confirmation dialog. No terminal interaction needed!

## Commands

```bash
./askpass-manager set   # Store password
./askpass-manager get   # Check if password exists
./askpass-manager clear # Remove password
./askpass-manager test  # Test sudo integration
```

## License

MIT License - see LICENSE file