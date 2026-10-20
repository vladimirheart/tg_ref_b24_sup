#!/usr/bin/env bash
set -euo pipefail

CLEAN=0
IF_DUE=0
PROJECT_NAME=""
RETENTION_HOURS=168
INTERVAL_HOURS=168
STATE_DIRECTORY=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --clean) CLEAN=1; shift ;;
    --if-due) IF_DUE=1; shift ;;
    --project-name|--retention-hours|--interval-hours|--state-directory)
      [[ $# -ge 2 && -n "$2" ]] || { echo "[ERROR] Missing value for $1" >&2; exit 1; }
      case "$1" in
        --project-name) PROJECT_NAME="$2" ;;
        --retention-hours) RETENTION_HOURS="$2" ;;
        --interval-hours) INTERVAL_HOURS="$2" ;;
        --state-directory) STATE_DIRECTORY="$2" ;;
      esac
      shift 2
      ;;
    *) echo "[ERROR] Unknown argument: $1" >&2; exit 1 ;;
  esac
done

[[ "$IF_DUE" != "1" || "$CLEAN" == "1" ]] || { echo "[ERROR] --if-due requires --clean." >&2; exit 1; }
for value in "$RETENTION_HOURS" "$INTERVAL_HOURS"; do
  [[ "$value" =~ ^[1-9][0-9]{0,4}$ ]] && (( value <= 87600 )) || {
    echo "[ERROR] Hours must be an integer between 1 and 87600." >&2; exit 1;
  }
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
DAEMON_ID="$(docker info --format '{{.ID}}')"
DAEMON_ID="${DAEMON_ID%$'\r'}"
[[ -n "$DAEMON_ID" && "$DAEMON_ID" != *$'\n'* ]] || { echo "[ERROR] Invalid Docker daemon identity." >&2; exit 1; }
if [[ -z "$PROJECT_NAME" ]]; then
  PROJECT_NAME="$(docker compose --project-directory "$REPO_ROOT" -f "$REPO_ROOT/docker-compose.production-contour.yml" config --environment | sed -n 's/^COMPOSE_PROJECT_NAME=//p')"
fi
PROJECT_NAME="${PROJECT_NAME%$'\r'}"
[[ "$PROJECT_NAME" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || { echo "[ERROR] Invalid Compose project name." >&2; exit 1; }
PROJECT_FILTER="label=io.iguana.project=$PROJECT_NAME"

show_report() {
  echo "[INFO] Docker storage (whole daemon):"
  docker system df
  echo "[INFO] Images labelled for project '$PROJECT_NAME':"
  docker image ls --filter "$PROJECT_FILTER"
}

if [[ "$CLEAN" != "1" ]]; then
  show_report
  echo "[INFO] Report only. Use --clean to remove dangling project images older than $RETENTION_HOURS hours."
  exit 0
fi

[[ -n "$STATE_DIRECTORY" ]] || STATE_DIRECTORY="$REPO_ROOT/run/docker-maintenance"
DAEMON_KEY="${DAEMON_ID//[^a-zA-Z0-9_.-]/-}"
STAMP_FILE="$STATE_DIRECTORY/$DAEMON_KEY-$PROJECT_NAME.last-clean"
LOCK_DIRECTORY="$STAMP_FILE.lock"
mkdir -p "$STATE_DIRECTORY"
if ! mkdir "$LOCK_DIRECTORY" 2>/dev/null; then
  [[ -d "$LOCK_DIRECTORY" ]] || { echo "[ERROR] Cannot create maintenance lock." >&2; exit 1; }
  echo "[WARN] Docker maintenance is already running (or a stale lock exists): $LOCK_DIRECTORY" >&2
  exit 0
fi
trap 'rmdir "$LOCK_DIRECTORY"' EXIT

now="$(date +%s)"
if [[ "$IF_DUE" == "1" && -f "$STAMP_FILE" ]]; then
  last_clean="$(< "$STAMP_FILE")"
  last_clean="${last_clean%$'\r'}"
  [[ "$last_clean" =~ ^[0-9]{1,11}$ ]] && (( 10#$last_clean <= now + 60 )) || {
    echo "[ERROR] Invalid maintenance timestamp: $STAMP_FILE" >&2; exit 1;
  }
  if (( now - 10#$last_clean < INTERVAL_HOURS * 3600 )); then
    echo "[INFO] Project image cleanup is not due (interval: $INTERVAL_HOURS hours)."
    exit 0
  fi
fi

show_report
echo "[INFO] Removing dangling project images created more than $RETENTION_HOURS hours ago; tagged images are preserved."
# Intentionally no --all: named releases and rollback tags must survive.
docker image prune --force --filter "$PROJECT_FILTER" --filter "until=${RETENTION_HOURS}h"
printf '%s\n' "$now" > "$STAMP_FILE"
show_report
