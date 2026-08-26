#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
source "${SCRIPT_DIR}/lib/backup-config.sh"
iguana_import_backup_settings "${REPO_ROOT}"

shared_dir="$(iguana_resolve_shared_config_dir "${REPO_ROOT}")"
mkdir -p "${shared_dir}"
state_file="${shared_dir}/backup-scheduler.state"
lock_dir="${shared_dir}/.backup-scheduler.lock"

mkdir "${lock_dir}" 2>/dev/null || { echo "[INFO] Backup policy runner is already active."; exit 0; }
trap 'rmdir "${lock_dir}" 2>/dev/null || true' EXIT

state_get() {
  local key="$1"
  [[ -f "${state_file}" ]] || return 0
  grep -E "^${key}=" "${state_file}" | tail -n 1 | cut -d= -f2- || true
}

state_set() {
  local key="$1" value="$2" tmp="${state_file}.tmp.$$"
  { [[ -f "${state_file}" ]] && grep -v -E "^${key}=" "${state_file}" || true; printf '%s=%s\n' "${key}" "${value}"; } > "${tmp}"
  mv "${tmp}" "${state_file}"
}

due() {
  local prefix="$1"
  local enabled_var="IGUANA_BACKUP_${prefix}_ENABLED"
  local frequency_var="IGUANA_BACKUP_${prefix}_FREQUENCY"
  local time_var="IGUANA_BACKUP_${prefix}_TIME"
  local weekday_var="IGUANA_BACKUP_${prefix}_WEEKDAY"
  iguana_is_truthy "${!enabled_var:-false}" || return 1

  local now_time today weekday scheduled frequency key last
  now_time="$(date +%H:%M)"
  today="$(date +%Y-%m-%d)"
  weekday="$(date +%u)"
  scheduled="${!time_var:-02:00}"
  [[ "${now_time}" < "${scheduled}" ]] && return 1
  frequency="${!frequency_var:-daily}"

  if [[ "${frequency}" == "weekly" ]]; then
    local expected="${!weekday_var:-SUN}" code
    case "${weekday}" in 1) code=MON;;2) code=TUE;;3) code=WED;;4) code=THU;;5) code=FRI;;6) code=SAT;;7) code=SUN;; esac
    [[ "${code}" == "${expected}" ]] || return 1
  elif [[ "${frequency}" != "daily" ]]; then
    echo "[ERROR] Unsupported ${prefix} frequency: ${frequency}" >&2
    exit 1
  fi

  key="$(printf '%s' "${prefix}" | tr '[:upper:]' '[:lower:]')_last_slot"
  last="$(state_get "${key}")"
  [[ "${last}" != "${today}" ]]
}

today="$(date +%Y-%m-%d)"
if due CRITICAL; then
  echo "[SCHEDULE] Running critical backup"
  "${SCRIPT_DIR}/docker-production-backup.sh" --action backup --mode critical
  state_set critical_last_slot "${today}"
fi

if due FULL; then
  echo "[SCHEDULE] Running full backup + isolated restore rehearsal"
  "${SCRIPT_DIR}/docker-production-backup.sh" --action full --mode full
  state_set full_last_slot "${today}"
fi
