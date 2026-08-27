#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER="${SCRIPT_DIR}/run-backup-policy.sh"
MARKER="# Iguana Backup Policy Runner"
command -v crontab >/dev/null 2>&1 || { echo "[ERROR] crontab is unavailable." >&2; exit 1; }
chmod +x "${RUNNER}" "${SCRIPT_DIR}/docker-production-backup.sh"
mkdir -p "${SCRIPT_DIR}/../logs"
current="$(crontab -l 2>/dev/null || true)"
filtered="$(printf '%s\n' "${current}" | grep -vF "${MARKER}" | grep -vF "${RUNNER}" || true)"
{ printf '%s\n' "${filtered}"; printf '%s\n' "${MARKER}"; printf '* * * * * %s >> %s/backup-policy-runner.log 2>&1\n' "${RUNNER}" "${SCRIPT_DIR}/../logs"; } | crontab -
echo "[GREEN] cron runner installed; admin schedule and manual queue are evaluated every minute."
