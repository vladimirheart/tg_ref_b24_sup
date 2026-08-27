#!/usr/bin/env bash
set -euo pipefail

DETACH_FROM_PARENT=0
IDLE_SECONDS=5

while [[ $# -gt 0 ]]; do
  case "$1" in
    --detach-from-parent) DETACH_FROM_PARENT=1; shift ;;
    --idle-seconds) IDLE_SECONDS="${2:-5}"; shift 2 ;;
    *) echo "[ERROR] Unknown argument: $1" >&2; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUNNER="${SCRIPT_DIR}/run-backup-policy.sh"
PID_FILE="${REPO_ROOT}/run/backup-policy-runner.pid"
OUT_LOG="${REPO_ROOT}/logs/backup-policy-runner.out.log"
ERR_LOG="${REPO_ROOT}/logs/backup-policy-runner.err.log"

source "${SCRIPT_DIR}/lib/backup-config.sh"
shared_dir="$(iguana_resolve_shared_config_dir "${REPO_ROOT}")"
stop_file="${shared_dir}/backup-policy-runner.stop"

mkdir -p "${REPO_ROOT}/run" "${REPO_ROOT}/logs" "${shared_dir}"
rm -f "${stop_file}"

if [[ -f "${PID_FILE}" ]]; then
  existing_pid="$(grep -E '^pid=' "${PID_FILE}" | tail -n 1 | cut -d= -f2- || true)"
  if [[ "${existing_pid}" =~ ^[0-9]+$ ]] && kill -0 "${existing_pid}" 2>/dev/null; then
    args="$(ps -p "${existing_pid}" -o args= 2>/dev/null || true)"
    if [[ "${args}" == *"run-backup-policy.sh"* && "${args}" == *"--daemon"* ]]; then
      echo "[INFO] Backup policy runner daemon already active. pid=${existing_pid}"
      exit 0
    fi
  fi
  rm -f "${PID_FILE}"
fi

runner_args=(--daemon --idle-seconds "${IDLE_SECONDS}")
if [[ "${DETACH_FROM_PARENT}" != "1" ]]; then
  runner_args+=(--parent-pid "$PPID")
fi

nohup bash "${RUNNER}" "${runner_args[@]}" >>"${OUT_LOG}" 2>>"${ERR_LOG}" </dev/null &
runner_pid=$!

sleep 0.2
kill -0 "${runner_pid}" 2>/dev/null || {
  echo "[ERROR] Backup policy runner exited immediately. Check ${ERR_LOG}" >&2
  exit 1
}

printf 'pid=%s\nstarted_at=%s\n' "${runner_pid}" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "${PID_FILE}"
echo "[GREEN] Hidden backup policy runner started. pid=${runner_pid}"
echo "[INFO] Logs: ${OUT_LOG} / ${ERR_LOG}"
