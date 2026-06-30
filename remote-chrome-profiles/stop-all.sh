#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

pkill -f "user-data-dir=$BASE_DIR/data/" || true
pkill -f "proxy-forwarder.py 127.0.0.1:11" || true
echo "Stopped Chrome/Chromium instances started from $BASE_DIR"
