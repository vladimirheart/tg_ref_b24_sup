#!/usr/bin/env bash
set -euo pipefail

WAIT_SECONDS=5
FORCE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --wait-seconds) WAIT_SECONDS="${2:-5}"; shift 2 ;;
    --force) FORCE=1; shift ;;
    *) echo "[ERROR] Unknown argument: $1" >&2; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PID_FILE="${REPO_ROOT}/run/backup-policy-runner.pid"

source "${SCRIPT_DIR}/lib/backup-config.sh"
shared_dir="$(iguana_resolve_shared_config_dir "${REPO_ROOT}")"
stop_file="${shared_dir}/backup-policy-runner.stop"
status_file="${shared_dir}/backup-policy-runner.status"

mkdir -p "${shared_dir}"

write_offline() {
  cat > "${status_file}" <<EOF
status=offline
last_seen_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
platform=unix
mode=daemon
schedule_ready=false
message=$1
EOF
}

runner_pid=""
[[ -f "${PID_FILE}" ]] && runner_pid="$(grep -E '^pid=' "${PID_FILE}" | tail -n 1 | cut -d= -f2- || true)"

if [[ ! "${runner_pid}" =~ ^[0-9]+$ ]] || ! kill -0 "${runner_pid}" 2>/dev/null; then
  rm -f "${PID_FILE}" "${stop_file}"
  write_offline "Panel lifecycle runner is not active."
  echo "[INFO] Backup policy runner is not active."
  exit 0
fi

printf 'stop\n' > "${stop_file}"

deadline=$((SECONDS + WAIT_SECONDS))
while (( SECONDS < deadline )); do
  kill -0 "${runner_pid}" 2>/dev/null || break
  sleep 0.25
done

if kill -0 "${runner_pid}" 2>/dev/null; then
  if [[ "${FORCE}" == "1" ]]; then
    kill "${runner_pid}" 2>/dev/null || true
    sleep 0.2
  fi
fi

if kill -0 "${runner_pid}" 2>/dev/null; then
  echo "[WARN] Backup policy runner is finishing an active backup and will stop after the current cycle. pid=${runner_pid}" >&2
  exit 0
fi

rm -f "${PID_FILE}" "${stop_file}"
write_offline "Panel lifecycle runner stopped."
echo "[GREEN] Backup policy runner stopped."
