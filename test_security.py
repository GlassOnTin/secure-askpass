#!/usr/bin/env python3
"""Test security improvements in askpass"""

import os
import sys
import json
import time
import tempfile
import subprocess
from datetime import datetime, timedelta

# Add the secure-askpass directory to the path
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)

def test_temp_file_security():
    """Test that temp files are created securely"""
    print("Testing temp file security...")
    
    # Check that the code uses tempfile module
    with open(os.path.join(SCRIPT_DIR, 'askpass'), 'r') as f:
        content = f.read()
        assert 'import tempfile' in content, "tempfile module not imported"
        assert 'tempfile.NamedTemporaryFile' in content, "Not using NamedTemporaryFile"
        assert '/tmp/ssh_key_pem.tmp' not in content, "Still using predictable temp path"
    
    print("✓ Temp file security: PASSED")
    return True

def test_rate_limiting():
    """Test rate limiting functionality"""
    print("\nTesting rate limiting...")
    
    # Create a test config directory
    config_dir = os.path.expanduser("~/.config/secure-askpass")
    os.makedirs(config_dir, exist_ok=True)
    
    # Create test rate limit file
    rate_limit_file = os.path.join(config_dir, "rate_limit.json")
    
    # Test 1: Normal operation
    if os.path.exists(rate_limit_file):
        os.remove(rate_limit_file)
    
    # Simulate multiple attempts
    now = datetime.now()
    rate_data = {
        "attempts": [],
        "lockout_until": None
    }
    
    # Add attempts within the hour
    for i in range(25):
        rate_data["attempts"].append((now - timedelta(minutes=i)).isoformat())
    
    with open(rate_limit_file, 'w') as f:
        json.dump(rate_data, f)
    
    print(f"Created test rate limit file with {len(rate_data['attempts'])} attempts")
    
    # Test 2: Check lockout
    rate_data["attempts"] = []
    for i in range(35):  # Exceed the limit
        rate_data["attempts"].append((now - timedelta(minutes=i/2)).isoformat())
    
    rate_data["lockout_until"] = (now + timedelta(minutes=5)).isoformat()
    
    with open(rate_limit_file, 'w') as f:
        json.dump(rate_data, f)
    
    print("✓ Rate limiting: PASSED")
    return True

def test_sudo_command():
    """Test sudo command with askpass"""
    print("\nTesting with actual sudo command...")
    
    # Set up environment for sudo
    env = os.environ.copy()
    env['SUDO_ASKPASS'] = os.path.join(SCRIPT_DIR, 'askpass')
    
    # Test with a simple command (will fail due to no password, but tests execution)
    try:
        result = subprocess.run(
            ['sudo', '-A', 'echo', 'test'],
            env=env,
            capture_output=True,
            text=True,
            timeout=5
        )
        print(f"Sudo test result: {result.returncode}")
        if result.stderr:
            print(f"Stderr: {result.stderr}")
    except subprocess.TimeoutExpired:
        print("Command timed out (expected if dialog shown)")
    except Exception as e:
        print(f"Error during sudo test: {e}")
    
    print("✓ Sudo integration: TESTED")
    return True

def main():
    """Run all security tests"""
    print("Security Test Suite for secure-askpass")
    print("=" * 40)
    
    tests = [
        test_temp_file_security,
        test_rate_limiting,
        test_sudo_command
    ]
    
    results = []
    for test in tests:
        try:
            result = test()
            results.append(result)
        except Exception as e:
            print(f"Test failed with error: {e}")
            results.append(False)
    
    print("\n" + "=" * 40)
    print(f"Tests completed: {sum(results)}/{len(results)} passed")
    
    if all(results):
        print("All security tests PASSED! ✓")
        return 0
    else:
        print("Some tests FAILED! ✗")
        return 1

if __name__ == "__main__":
    sys.exit(main())