# SSH Askpass Helper

## Setup
1. Set `export SUDO_ASKPASS=/path/to/askpass`
2. Use `askpass-manager` to manage passwords

## askpass-manager Commands
- `store` - Save password
- `retrieve` - Get password
- `delete` - Remove password
- `list` - Show entries

## Key Points
- Passwords encrypted with SSH key
- SSH key must be unlocked
- Never pass passwords as CLI arguments