#!/usr/bin/env python3
import os
import subprocess

# Debug script to understand sudo behavior

print("=== Sudo Askpass Debug ===")
print(f"Current directory: {os.getcwd()}")
print(f"SUDO_ASKPASS: {os.environ.get('SUDO_ASKPASS', 'Not set')}")
print()

# Change to restricted directory
os.chdir('/opt')
print(f"Changed to: {os.getcwd()}")
print()

# Try running sudo with askpass
print("Attempting sudo -A command...")
result = subprocess.run(['sudo', '-A', 'echo', 'test'], 
                       capture_output=True, text=True)
print(f"Return code: {result.returncode}")
print(f"Stdout: {result.stdout}")
print(f"Stderr: {result.stderr}")
print()

# Check what directory askpass sees
print("Checking askpass perspective...")
test_script = """#!/usr/bin/env python3
import os
print(f"Askpass sees directory: {os.getcwd()}")
"""
with open('/tmp/test_askpass.py', 'w') as f:
    f.write(test_script)
os.chmod('/tmp/test_askpass.py', 0o755)

# Temporarily use test askpass
os.environ['SUDO_ASKPASS'] = '/tmp/test_askpass.py'
result = subprocess.run(['sudo', '-A', 'echo', 'test'], 
                       capture_output=True, text=True)
print(result.stderr)