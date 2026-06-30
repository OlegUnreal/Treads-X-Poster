#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$BASE_DIR/profiles.env"

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <profile-name>" >&2
  echo "Example: $0 ip1" >&2
  exit 2
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Copy profiles.env.example to profiles.env and edit it." >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

export DISPLAY="${DISPLAY:-${VNC_DISPLAY:-}}"

if [[ -z "${DISPLAY:-}" ]]; then
  echo "DISPLAY is not set. Set VNC_DISPLAY in profiles.env or start from an active VNC/X11 session." >&2
  exit 1
fi

PROFILE_NAME="$1"
PROXY_VAR="PROXY_${PROFILE_NAME}"
PROXY="${!PROXY_VAR:-}"
UPSTREAM_PROXY_VAR="UPSTREAM_PROXY_${PROFILE_NAME}"
UPSTREAM_PROXY="${!UPSTREAM_PROXY_VAR:-}"

if [[ -z "$PROXY" ]]; then
  echo "Missing proxy variable $PROXY_VAR in $ENV_FILE" >&2
  exit 1
fi

if command -v google-chrome >/dev/null 2>&1; then
  CHROME_BIN="google-chrome"
elif command -v chromium >/dev/null 2>&1; then
  CHROME_BIN="chromium"
elif command -v chromium-browser >/dev/null 2>&1; then
  CHROME_BIN="chromium-browser"
else
  echo "Missing Chrome/Chromium." >&2
  exit 1
fi

PROFILE_DIR="$BASE_DIR/data/$PROFILE_NAME"
mkdir -p "$PROFILE_DIR"

URL="${START_URL:-about:blank}"

if [[ "$PROXY" == http://127.0.0.1:* && -n "$UPSTREAM_PROXY" ]]; then
  LISTEN="${PROXY#http://}"
  LOG_SAFE_UPSTREAM="$(echo "$UPSTREAM_PROXY" | sed -E 's#(https?://)[^:@/]+:[^@/]+@#\1***:***@#')"
  if ! pgrep -f "proxy-forwarder.py $LISTEN " >/dev/null 2>&1; then
    echo "Starting local proxy $LISTEN -> $LOG_SAFE_UPSTREAM"
    nohup python3 "$BASE_DIR/proxy-forwarder.py" "$LISTEN" "$UPSTREAM_PROXY" \
      >"$BASE_DIR/${PROFILE_NAME}-proxy.log" 2>&1 &
    sleep 1
  fi
fi

echo "Starting $PROFILE_NAME through $PROXY"

nohup "$CHROME_BIN" \
  --user-data-dir="$PROFILE_DIR" \
  --proxy-server="$PROXY" \
  --no-sandbox \
  --force-webrtc-ip-handling-policy=disable_non_proxied_udp \
  --disable-features=WebRtcHideLocalIpsWithMdns \
  --incognito \
  --new-window "$URL" \
  >"$BASE_DIR/${PROFILE_NAME}.log" 2>&1 &

echo "PID: $!"
echo "Log: $BASE_DIR/${PROFILE_NAME}.log"
