#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$BASE_DIR/profiles.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing $ENV_FILE. Copy profiles.env.example to profiles.env and edit it." >&2
  exit 1
fi

# shellcheck disable=SC1090
source "$ENV_FILE"

STAGGER_MIN_SECONDS="${STAGGER_MIN_SECONDS:-20}"
STAGGER_MAX_SECONDS="${STAGGER_MAX_SECONDS:-90}"
PROFILE_LIMIT="${PROFILE_LIMIT:-0}"
PROFILE_SOURCE="${LAUNCH_PROFILE_NAMES:-${PROFILE_NAMES:-}}"

if ! [[ "$STAGGER_MIN_SECONDS" =~ ^[0-9]+$ ]] || ! [[ "$STAGGER_MAX_SECONDS" =~ ^[0-9]+$ ]] || ! [[ "$PROFILE_LIMIT" =~ ^[0-9]+$ ]]; then
  echo "STAGGER_MIN_SECONDS, STAGGER_MAX_SECONDS, and PROFILE_LIMIT must be whole numbers." >&2
  exit 2
fi

if (( STAGGER_MAX_SECONDS < STAGGER_MIN_SECONDS )); then
  STAGGER_MAX_SECONDS="$STAGGER_MIN_SECONDS"
fi

started=0
for profile in ${PROFILE_SOURCE}; do
  if (( PROFILE_LIMIT > 0 && started >= PROFILE_LIMIT )); then
    break
  fi
  "$BASE_DIR/start-profile.sh" "$profile"
  started=$((started + 1))
  if (( PROFILE_LIMIT > 0 && started >= PROFILE_LIMIT )); then
    break
  fi
  if (( STAGGER_MAX_SECONDS > 0 )); then
    delay="$STAGGER_MIN_SECONDS"
    if (( STAGGER_MAX_SECONDS > STAGGER_MIN_SECONDS )); then
      range=$((STAGGER_MAX_SECONDS - STAGGER_MIN_SECONDS + 1))
      delay=$((STAGGER_MIN_SECONDS + RANDOM % range))
    fi
    echo "Waiting ${delay}s before the next profile."
    sleep "$delay"
  fi
done

echo "Started ${started} profile(s)."
