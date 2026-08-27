#!/usr/bin/env bash
set -euo pipefail

DAEMON=0
FORCE=0
IDLE_SECONDS=5
PARENT_PID=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --daemon) DAEMON=1; shift ;;
    --force) FORCE=1; shift ;;
    --idle-seconds) IDLE_SECONDS="${2:-5}"; shift 2 ;;
    --parent-pid) PARENT_PID="${2:-0}"; shift 2 ;;
    *) echo "[ERROR] Unknown argument: $1" >&2; exit 1 ;;
  esac
done

case "${IDLE_SECONDS}" in
  ''|*[!0-9]*) echo "[ERROR] --idle-seconds must be a positive integer." >&2; exit 1 ;;
esac
[[ "${IDLE_SECONDS}" -ge 1 && "${IDLE_SECONDS}" -le 60 ]] || {
  echo "[ERROR] --idle-seconds must be 1..60." >&2
  exit 1
}

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
source "${SCRIPT_DIR}/lib/backup-config.sh"

shared_dir="$(iguana_resolve_shared_config_dir "${REPO_ROOT}")"
mkdir -p "${shared_dir}"

policy_file="${shared_dir}/backup.properties"
state_file="${shared_dir}/backup-scheduler.state"
request_file="${shared_dir}/backup-manual-request.properties"
running_file="${shared_dir}/backup-manual-request.running"
manual_status="${shared_dir}/backup-manual-status.properties"
runner_status="${shared_dir}/backup-policy-runner.status"
stop_file="${shared_dir}/backup-policy-runner.stop"
lock_dir="${shared_dir}/.backup-scheduler.lock"

policy_names=(
  IGUANA_BACKUP_DESTINATION_DIR
  IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN
  IGUANA_BACKUP_RETENTION_DAYS
  IGUANA_MINIO_BACKUP_RETENTION_DAYS
  IGUANA_BACKUP_ARCHIVE_FORMAT
  IGUANA_BACKUP_MANUAL_MODE
  IGUANA_BACKUP_CUSTOM_COMPONENTS
  IGUANA_BACKUP_RESTORE_COMPONENTS
  IGUANA_BACKUP_CRITICAL_ENABLED
  IGUANA_BACKUP_CRITICAL_FREQUENCY
  IGUANA_BACKUP_CRITICAL_TIME
  IGUANA_BACKUP_CRITICAL_WEEKDAY
  IGUANA_BACKUP_FULL_ENABLED
  IGUANA_BACKUP_FULL_FREQUENCY
  IGUANA_BACKUP_FULL_TIME
  IGUANA_BACKUP_FULL_WEEKDAY
)

flat_get() {
  local file="$1" key="$2"
  [[ -f "${file}" ]] || return 0
  grep -E "^${key}=" "${file}" | tail -n 1 | cut -d= -f2- || true
}

write_status() {
  local file="$1"; shift
  local tmp="${file}.tmp.$$"
  : > "${tmp}"
  while [[ $# -gt 1 ]]; do
    local key="$1" value="$2"
    shift 2
    value="$(printf '%s' "${value}" | tr '\r\n=' '   ')"
    printf '%s=%s\n' "${key}" "${value}" >> "${tmp}"
  done
  mv "${tmp}" "${file}"
}

refresh_policy() {
  local name line key value
  for name in "${policy_names[@]}"; do
    unset "${name}" || true
  done

  if [[ -f "${policy_file}" ]]; then
    while IFS= read -r line || [[ -n "${line}" ]]; do
      line="${line%$'\r'}"
      [[ -n "${line}" ]] || continue
      [[ "${line}" != \#* && "${line}" != \!* ]] || continue
      [[ "${line}" == *=* ]] || continue
      key="${line%%=*}"
      value="${line#*=}"
      case "${key}" in
        IGUANA_BACKUP_DESTINATION_DIR|IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN|IGUANA_BACKUP_RETENTION_DAYS|IGUANA_MINIO_BACKUP_RETENTION_DAYS|IGUANA_BACKUP_ARCHIVE_FORMAT|IGUANA_BACKUP_MANUAL_MODE|IGUANA_BACKUP_CUSTOM_COMPONENTS|IGUANA_BACKUP_RESTORE_COMPONENTS|IGUANA_BACKUP_CRITICAL_ENABLED|IGUANA_BACKUP_CRITICAL_FREQUENCY|IGUANA_BACKUP_CRITICAL_TIME|IGUANA_BACKUP_CRITICAL_WEEKDAY|IGUANA_BACKUP_FULL_ENABLED|IGUANA_BACKUP_FULL_FREQUENCY|IGUANA_BACKUP_FULL_TIME|IGUANA_BACKUP_FULL_WEEKDAY)
          export "${key}=${value}"
          ;;
      esac
    done < "${policy_file}"
  else
    iguana_import_backup_settings "${REPO_ROOT}"
  fi
}

write_heartbeat() {
  local status="${1:-online}" message="${2:-}"
  local schedule_ready=false
  iguana_is_truthy "${IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN:-false}" && schedule_ready=true
  write_status "${runner_status}" \
    status "${status}" \
    last_seen_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    platform unix \
    mode "$([[ "${DAEMON}" == "1" ]] && printf daemon || printf oneshot)" \
    schedule_ready "${schedule_ready}" \
    runner_version panel-lifecycle-v1 \
    process_id "$$" \
    message "${message}"
}

state_get() {
  flat_get "${state_file}" "$1"
}

state_set() {
  local key="$1" value="$2" tmp="${state_file}.tmp.$$"
  {
    [[ -f "${state_file}" ]] && grep -v -E "^${key}=" "${state_file}" || true
    printf '%s=%s\n' "${key}" "${value}"
  } > "${tmp}"
  mv "${tmp}" "${state_file}"
}

manual_restore_components() {
  case "$1" in
    critical) printf '%s' "postgres,minio,shared-config" ;;
    full) printf '%s' "postgres,minio,shared-config,templates,static-js,static-css" ;;
    custom) printf '%s' "${IGUANA_BACKUP_CUSTOM_COMPONENTS:-postgres,minio,shared-config}" ;;
    *) echo "[ERROR] Unsupported manual backup mode: $1" >&2; return 1 ;;
  esac
}

process_manual_request() {
  if [[ -f "${running_file}" ]]; then
    if find "${running_file}" -mmin +120 -print -quit 2>/dev/null | grep -q .; then
      write_status "${manual_status}" \
        request_id "$(flat_get "${running_file}" request_id)" \
        status error \
        mode "$(flat_get "${running_file}" mode)" \
        finished_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
        message "Stale manual backup claim detected after runner interruption."
      rm -f "${running_file}"
    else
      return 0
    fi
  fi

  [[ -f "${request_file}" ]] || return 0
  mv "${request_file}" "${running_file}" 2>/dev/null || return 0

  local request_id mode verify_restore allow_local requested_at requested_by started_at
  request_id="$(flat_get "${running_file}" request_id)"
  mode="$(flat_get "${running_file}" mode)"
  verify_restore="$(flat_get "${running_file}" verify_restore)"
  allow_local="$(flat_get "${running_file}" allow_local_test)"
  requested_at="$(flat_get "${running_file}" requested_at)"
  requested_by="$(flat_get "${running_file}" requested_by)"
  started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  case "${mode}" in
    critical|full|custom) ;;
    *)
      write_status "${manual_status}" \
        request_id "${request_id}" status error mode "${mode}" \
        finished_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
        message "Unsupported manual backup mode."
      rm -f "${running_file}"
      return 0
      ;;
  esac

  write_status "${manual_status}" \
    request_id "${request_id}" status running mode "${mode}" \
    verify_restore "${verify_restore}" allow_local_test "${allow_local}" \
    requested_at "${requested_at}" requested_by "${requested_by}" \
    started_at "${started_at}" message "Manual backup is running on Docker host."

  local args=(--action backup --mode "${mode}")
  if iguana_is_truthy "${verify_restore}"; then
    args=(--action full --mode "${mode}" --restore-components "$(manual_restore_components "${mode}")")
  fi
  if iguana_is_truthy "${allow_local}"; then
    args+=(--allow-local-destination)
  fi

  echo "[MANUAL] request=${request_id} mode=${mode} local_test=${allow_local}"
  set +e
  "${SCRIPT_DIR}/docker-production-backup.sh" "${args[@]}"
  local code=$?
  set -e

  if [[ "${code}" -eq 0 ]]; then
    write_status "${manual_status}" \
      request_id "${request_id}" status success mode "${mode}" \
      verify_restore "${verify_restore}" allow_local_test "${allow_local}" \
      requested_at "${requested_at}" requested_by "${requested_by}" \
      started_at "${started_at}" finished_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
      message "Manual backup completed successfully."
    echo "[GREEN] Manual backup request completed: ${request_id}"
  else
    write_status "${manual_status}" \
      request_id "${request_id}" status error mode "${mode}" \
      verify_restore "${verify_restore}" allow_local_test "${allow_local}" \
      requested_at "${requested_at}" requested_by "${requested_by}" \
      started_at "${started_at}" finished_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
      message "Manual backup failed with exit code ${code}."
    echo "[ERROR] Manual backup request failed: ${request_id}; exit=${code}" >&2
  fi

  rm -f "${running_file}"
}

due() {
  local prefix="$1"
  local enabled_var="IGUANA_BACKUP_${prefix}_ENABLED"
  local frequency_var="IGUANA_BACKUP_${prefix}_FREQUENCY"
  local time_var="IGUANA_BACKUP_${prefix}_TIME"
  local weekday_var="IGUANA_BACKUP_${prefix}_WEEKDAY"

  [[ "${FORCE}" == "1" ]] || iguana_is_truthy "${!enabled_var:-false}" || return 1

  local now_time today weekday scheduled frequency key last
  now_time="$(date +%H:%M)"
  today="$(date +%Y-%m-%d)"
  weekday="$(date +%u)"
  scheduled="${!time_var:-02:00}"
  [[ "${FORCE}" == "1" || "${now_time}" > "${scheduled}" || "${now_time}" == "${scheduled}" ]] || return 1
  frequency="${!frequency_var:-daily}"

  if [[ "${FORCE}" != "1" && "${frequency}" == "weekly" ]]; then
    local expected="${!weekday_var:-SUN}" code
    case "${weekday}" in
      1) code=MON ;; 2) code=TUE ;; 3) code=WED ;; 4) code=THU ;;
      5) code=FRI ;; 6) code=SAT ;; 7) code=SUN ;;
    esac
    [[ "${code}" == "${expected}" ]] || return 1
  elif [[ "${FORCE}" != "1" && "${frequency}" != "daily" ]]; then
    echo "[ERROR] Unsupported ${prefix} frequency: ${frequency}" >&2
    return 1
  fi

  key="$(printf '%s' "${prefix}" | tr '[:upper:]' '[:lower:]')_last_slot"
  last="$(state_get "${key}")"
  [[ "${FORCE}" == "1" || "${last}" != "${today}" ]]
}

run_scheduled_plan() {
  local prefix="$1" action="$2" mode="$3" restore_components="$4"
  local today key
  today="$(date +%Y-%m-%d)"
  key="$(printf '%s' "${prefix}" | tr '[:upper:]' '[:lower:]')_last_slot"

  state_set "${key}" "${today}"

  local args=(--action "${action}" --mode "${mode}")
  [[ -n "${restore_components}" ]] && args+=(--restore-components "${restore_components}")

  echo "[SCHEDULE] Running $(printf '%s' "${prefix}" | tr '[:upper:]' '[:lower:]') backup plan"
  set +e
  "${SCRIPT_DIR}/docker-production-backup.sh" "${args[@]}"
  local code=$?
  set -e
  if [[ "${code}" -ne 0 ]]; then
    echo "[ERROR] ${prefix} scheduled backup failed with exit ${code}. This schedule slot will not be retried automatically." >&2
  fi
}

invoke_cycle() {
  refresh_policy
  write_heartbeat online

  process_manual_request

  iguana_is_truthy "${IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN:-false}" || return 0

  if due CRITICAL; then
    run_scheduled_plan CRITICAL backup critical ""
  fi
  if due FULL; then
    run_scheduled_plan FULL full full "postgres,minio,shared-config,templates,static-js,static-css"
  fi
}

parent_alive() {
  [[ "${PARENT_PID}" -le 0 ]] && return 0
  kill -0 "${PARENT_PID}" 2>/dev/null
}

rm -f "${stop_file}"
mkdir "${lock_dir}" 2>/dev/null || {
  echo "[INFO] Backup policy runner is already active."
  exit 0
}

cleanup() {
  refresh_policy || true
  write_heartbeat offline "Panel lifecycle runner stopped." || true
  rm -f "${stop_file}"
  rmdir "${lock_dir}" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 0' INT TERM

if [[ "${DAEMON}" == "1" ]]; then
  echo "[INFO] Backup policy runner daemon started. pid=$$ idle=${IDLE_SECONDS}s parent=${PARENT_PID}"
  while true; do
    [[ ! -f "${stop_file}" ]] || break
    parent_alive || {
      echo "[INFO] Panel launcher parent process exited. Stopping backup policy runner."
      break
    }

    if ! invoke_cycle; then
      echo "[ERROR] Backup policy cycle failed; daemon stays alive." >&2
    fi

    sleep "${IDLE_SECONDS}"
  done
else
  invoke_cycle
fi
