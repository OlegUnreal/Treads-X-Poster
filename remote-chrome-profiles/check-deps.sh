#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$BASE_DIR/profiles.env"

if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_FILE"
fi

export DISPLAY="${DISPLAY:-${VNC_DISPLAY:-}}"

if command -v google-chrome >/dev/null 2>&1; then
  echo "Chrome: $(command -v google-chrome)"
elif command -v chromium >/dev/null 2>&1; then
  echo "Chromium: $(command -v chromium)"
elif command -v chromium-browser >/dev/null 2>&1; then
  echo "Chromium: $(command -v chromium-browser)"
else
  echo "Missing Chrome/Chromium. Install google-chrome-stable or chromium." >&2
  exit 1
fi

if [[ -z "${DISPLAY:-}" ]]; then
  echo "DISPLAY is not set. Set VNC_DISPLAY in profiles.env or start from an active VNC/X11 session." >&2
  exit 1
fi

echo "DISPLAY=$DISPLAY"
echo "OK"
