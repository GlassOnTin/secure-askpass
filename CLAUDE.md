# SSH Askpass Helper Project Guidelines

## Project Overview
This is an SSH askpass helper service for secure sudo password storage using SSH key encryption. The project consists of:
- `askpass` - Primary askpass script
- `askpass-manager` - Management utility for the askpass service

## Development Guidelines

1. **Tool Usage**:
   - Use `bash` commands directly for basic operations
   - For complex searches, use the `Task` tool
   - When making multiple bash calls, use `Batch` for parallel execution

2. **Code Style**:
   - Follow existing conventions in the askpass scripts
   - Keep scripts minimal and focused on single responsibilities
   - No unnecessary comments unless explicitly requested

3. **Testing**:
   - Test both `askpass` and `askpass-manager` components
   - Verify SSH key encryption/decryption functionality
   - Ensure secure password handling

4. **Security**:
   - Never log or expose passwords in plain text
   - Verify SSH key permissions are properly restricted
   - Follow secure coding practices for password handling

5. **Commits**:
   - Only commit when explicitly requested
   - Follow existing commit style from git history