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
PROFILE_LABEL_VAR="PROFILE_LABEL_${PROFILE_NAME}"
PROFILE_LABEL="${!PROFILE_LABEL_VAR:-$PROFILE_NAME}"
WINDOW_POSITION_VAR="WINDOW_POSITION_${PROFILE_NAME}"
WINDOW_POSITION="${!WINDOW_POSITION_VAR:-}"

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
STATE_DIR="$BASE_DIR/state"
mkdir -p "$PROFILE_DIR"
mkdir -p "$STATE_DIR"

URL="${LAUNCH_URL:-${START_URL:-about:blank}}"
if [[ "$URL" == "profile-home" ]]; then
  HOME_FILE="$BASE_DIR/${PROFILE_NAME}-home.html"
  SAFE_UPSTREAM="$(echo "${UPSTREAM_PROXY:-$PROXY}" | sed -E 's#(https?://)[^:@/]+:[^@/]+@#\1***:***@#')"
  cat > "$HOME_FILE" <<EOF
<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <title>${PROFILE_LABEL}</title>
  <style>
    body { margin: 0; font-family: Arial, sans-serif; background: #111827; color: #f9fafb; }
    main { padding: 44px; }
    h1 { font-size: 72px; margin: 0 0 16px; }
    .line { font-size: 28px; margin: 14px 0; }
    .muted { color: #cbd5e1; }
    a { color: #67e8f9; font-size: 24px; display: inline-block; margin-top: 24px; }
  </style>
</head>
<body>
  <main>
    <h1>${PROFILE_LABEL}</h1>
    <div class="line">Profile: <strong>${PROFILE_NAME}</strong></div>
    <div class="line">Chrome proxy: <strong>${PROXY}</strong></div>
    <div class="line muted">Upstream: ${SAFE_UPSTREAM}</div>
    <div class="line muted">Started: $(date -Iseconds)</div>
    <a href="https://browserleaks.com/ip">Open IP leak check</a>
  </main>
</body>
</html>
EOF
  URL="file://$HOME_FILE"
fi

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

is_youtube_url() {
  local value="$1"
  [[ "$value" == *"youtube.com"* || "$value" == *"youtu.be"* ]]
}

add_youtube_quality_params() {
  local value="$1"
  local quality="${VIDEO_QUALITY:-auto}"
  if [[ "$quality" == "auto" ]] || ! is_youtube_url "$value"; then
    echo "$value"
    return 0
  fi
  python3 - "$value" "$quality" <<'PY'
import sys
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

url, quality = sys.argv[1], sys.argv[2]
parts = urlsplit(url)
query = dict(parse_qsl(parts.query, keep_blank_values=True))
query["vq"] = quality
if quality not in {"tiny", "small"}:
    query["hd"] = "1"
print(urlunsplit((parts.scheme, parts.netloc, parts.path, urlencode(query), parts.fragment)))
PY
}

create_playback_extension() {
  local extension_dir="$BASE_DIR/extensions/$PROFILE_NAME"
  local referer="${LAUNCH_REFERER:-}"
  local quality="${VIDEO_QUALITY:-auto}"
  if [[ -z "$referer" && "$quality" == "auto" ]]; then
    return 1
  fi

  mkdir -p "$extension_dir"
  python3 - "$extension_dir" "$referer" "$quality" <<'PY'
import json
import os
import sys

extension_dir, referer, quality = sys.argv[1], sys.argv[2], sys.argv[3]
manifest = {
    "manifest_version": 3,
    "name": "Behind The Smile Playback Controls",
    "version": "1.0.0",
    "host_permissions": ["<all_urls>"],
}
permissions = []
if referer:
    permissions.append("declarativeNetRequest")
    manifest["declarative_net_request"] = {
        "rule_resources": [{
            "id": "referer_rules",
            "enabled": True,
            "path": "rules.json",
        }]
    }
    rules = [{
        "id": 1,
        "priority": 1,
        "action": {
            "type": "modifyHeaders",
            "requestHeaders": [{
                "header": "Referer",
                "operation": "set",
                "value": referer,
            }],
        },
        "condition": {
            "urlFilter": "|http",
            "resourceTypes": ["main_frame", "sub_frame", "xmlhttprequest", "media"],
        },
    }]
    with open(os.path.join(extension_dir, "rules.json"), "w", encoding="utf-8") as file:
        json.dump(rules, file)
if quality != "auto":
    manifest["content_scripts"] = [{
        "matches": ["*://*.youtube.com/*", "*://youtube.com/*"],
        "js": ["youtube-quality.js"],
        "run_at": "document_idle",
    }]
    with open(os.path.join(extension_dir, "youtube-quality.js"), "w", encoding="utf-8") as file:
        file.write(f"""(() => {{
  const quality = {json.dumps(quality)};
  let attempts = 0;
  const applyQuality = () => {{
    attempts += 1;
    const player = document.getElementById('movie_player');
    try {{
      if (player && typeof player.setPlaybackQualityRange === 'function') {{
        player.setPlaybackQualityRange(quality);
      }}
      if (player && typeof player.setPlaybackQuality === 'function') {{
        player.setPlaybackQuality(quality);
      }}
      localStorage.setItem('yt-player-quality', quality);
    }} catch (_) {{}}
    if (attempts < 30) {{
      setTimeout(applyQuality, 1500);
    }}
  }};
  applyQuality();
}})();
""")
if permissions:
    manifest["permissions"] = permissions
with open(os.path.join(extension_dir, "manifest.json"), "w", encoding="utf-8") as file:
    json.dump(manifest, file)
PY
  echo "$extension_dir"
}

should_use_incognito() {
  local value="$1"
  if is_youtube_url "$value"; then
    return 1
  fi
  if [[ "${INCOGNITO_MODE:-false}" == "true" ]]; then
    return 0
  fi
  for domain in ${INCOGNITO_DOMAINS:-}; do
    if [[ "$value" == *"$domain"* ]]; then
      return 0
    fi
  done
  return 1
}

WINDOW_ARGS=()
if [[ -n "${WINDOW_SIZE:-}" ]]; then
  WINDOW_ARGS+=(--window-size="$WINDOW_SIZE")
fi
if [[ -n "$WINDOW_POSITION" ]]; then
  WINDOW_ARGS+=(--window-position="$WINDOW_POSITION")
fi
if should_use_incognito "$URL"; then
  WINDOW_ARGS+=(--incognito)
fi
if [[ "$URL" != file://* ]]; then
  URL="$(add_youtube_quality_params "$URL")"
fi
PLAYBACK_EXTENSION="$(create_playback_extension || true)"
if [[ -n "$PLAYBACK_EXTENSION" ]]; then
  WINDOW_ARGS+=(--load-extension="$PLAYBACK_EXTENSION")
  WINDOW_ARGS+=(--disable-extensions-except="$PLAYBACK_EXTENSION")
fi

should_auto_click() {
  local value="$1"
  for domain in ${AUTO_CLICK_DOMAINS:-}; do
    if [[ "$value" == *"$domain"* ]]; then
      return 0
    fi
  done
  return 1
}

schedule_auto_click() {
  local chrome_pid="$1"
  local delay="${AUTO_CLICK_DELAY_SECONDS:-8}"
  if ! [[ "$delay" =~ ^[0-9]+$ ]]; then
    delay=8
  fi
  if ! command -v xdotool >/dev/null 2>&1; then
    echo "xdotool is missing; auto-click skipped."
    return 0
  fi

  (
    export DISPLAY="${DISPLAY:-${VNC_DISPLAY:-:1}}"
    export XAUTHORITY="${XAUTHORITY:-/root/.Xauthority}"
    sleep "$delay"
    for _ in 1 2 3 4 5; do
      window="$(xdotool search --onlyvisible --pid "$chrome_pid" 2>/dev/null | head -n 1 || true)"
      if [[ -z "$window" ]]; then
        window="$(xdotool search --onlyvisible --class 'google-chrome|chromium|Chromium' 2>/dev/null | tail -n 1 || true)"
      fi
      if [[ -n "$window" ]]; then
        eval "$(xdotool getwindowgeometry --shell "$window" 2>/dev/null || true)"
        if [[ -n "${WIDTH:-}" && -n "${HEIGHT:-}" ]]; then
          xdotool windowactivate --sync "$window" >/dev/null 2>&1 || true
          xdotool mousemove "$((X + WIDTH / 2))" "$((Y + HEIGHT / 2))" click 1 >/dev/null 2>&1 || true
          echo "Auto-clicked $PROFILE_NAME window for $URL"
          exit 0
        fi
      fi
      sleep 2
    done
    echo "Auto-click could not find a visible $PROFILE_NAME window."
  ) >>"$BASE_DIR/${PROFILE_NAME}.log" 2>&1 &
}

nohup "$CHROME_BIN" \
  --user-data-dir="$PROFILE_DIR" \
  --proxy-server="$PROXY" \
  --no-first-run \
  --no-default-browser-check \
  --autoplay-policy=no-user-gesture-required \
  --force-webrtc-ip-handling-policy=disable_non_proxied_udp \
  "${WINDOW_ARGS[@]}" \
  --new-window "$URL" \
  >"$BASE_DIR/${PROFILE_NAME}.log" 2>&1 &

CHROME_PID="$!"
cat >"$STATE_DIR/${PROFILE_NAME}.env" <<EOF
PID="$CHROME_PID"
LAST_URL="$URL"
LAST_OPENED_AT="$(date -Iseconds)"
PROFILE_DIR="$PROFILE_DIR"
MODE="open"
REFERER="${LAUNCH_REFERER:-}"
VIDEO_QUALITY="${VIDEO_QUALITY:-auto}"
EOF
echo "PID: $CHROME_PID"
echo "Log: $BASE_DIR/${PROFILE_NAME}.log"

if should_auto_click "$URL"; then
  schedule_auto_click "$CHROME_PID"
fi
