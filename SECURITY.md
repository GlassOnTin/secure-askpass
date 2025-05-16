# Security Enhancements for SSH Askpass Helper

## Overview
The askpass script has been enhanced with multiple security layers including a Windows-style GUI confirmation dialog to prevent unauthorized access while maintaining usability for legitimate tools like Claude.

## Security Features

### 1. GUI Confirmation Dialog (NEW)
- Shows a Windows-style administrator confirmation dialog
- Displays the command being executed, user, and hostname
- Requires explicit user approval for each sudo request
- Falls back through multiple GUI methods: Tkinter → GTK → zenity
- Can be disabled for automation via configuration

### 2. Path-based Restrictions
- Only allows execution from trusted directories: `/home/ian/` and `/tmp/`
- Prevents malicious scripts from arbitrary locations accessing passwords

### 3. Time-based Expiration
- Passwords automatically expire after 24 hours
- Expired password files are automatically deleted
- Configurable via `expiration_hours` in config

### 4. Process Validation
- Verifies the calling process is from allowed list: `sudo`, `claude-code`, `code`, `bash`, `sh`
- Prevents unauthorized processes from retrieving passwords

### 5. Environment Verification
- Requires proper terminal or SSH environment variables
- Ensures askpass is called from legitimate user sessions

### 6. Audit Logging
- All askpass usage is logged to syslog
- Records calling process, PID, command, and working directory
- Failed security checks and user denials are logged with warnings

## Configuration
Security settings can be configured via `askpass-config.json`:
```json
{
    "require_user_confirmation": true,
    "allowed_paths": ["/home/ian/", "/tmp/"],
    "expiration_hours": 24,
    "allowed_processes": ["sudo", "claude-code", "code", "bash", "sh"]
}
```

The configuration file is loaded from (in order of priority):
1. `~/.config/secure-askpass/config.json`
2. `./askpass-config.json` (in the same directory as the script)

To disable GUI prompts for automation:
```json
{
    "require_user_confirmation": false
}
```

## Monitoring
Check audit logs with:
```bash
sudo journalctl -t sudo-askpass -f
```

## Testing
Test the setup:
```bash
# From within the project directory
./askpass-manager test
```

## Backup
Original askpass script backed up to: `askpass.backup`