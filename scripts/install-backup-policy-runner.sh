#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER="${SCRIPT_DIR}/run-backup-policy.sh"
MARKER="# Iguana Backup Policy Runner"

echo "[INFO] cron runner is deprecated."
echo "[INFO] Backup runner now starts in the background with the panel lifecycle."

command -v crontab >/dev/null 2>&1 || {
  echo "[INFO] crontab is unavailable; nothing to migrate."
  exit 0
}

current="$(crontab -l 2>/dev/null || true)"
filtered="$(printf '%s\n' "${current}" | grep -vF "${MARKER}" | grep -vF "${RUNNER}" || true)"

if [[ "${filtered}" == "${current}" ]]; then
  echo "[INFO] Legacy cron entry is not installed."
  exit 0
fi

printf '%s\n' "${filtered}" | crontab -
echo "[GREEN] Removed legacy Iguana backup cron entry."
echo "[INFO] No periodic OS scheduler is required anymore."
