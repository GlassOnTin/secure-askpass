#!/bin/bash
cd /opt
echo "Current directory: $(pwd)"
echo "Testing sudo from /opt..."
sudo -A whoami