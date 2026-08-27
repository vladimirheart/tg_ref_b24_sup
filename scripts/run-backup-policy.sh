#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
source "${SCRIPT_DIR}/lib/backup-config.sh"
iguana_import_backup_settings "${REPO_ROOT}"
shared_dir="$(iguana_resolve_shared_config_dir "${REPO_ROOT}")"
mkdir -p "${shared_dir}"
state_file="${shared_dir}/backup-scheduler.state"
request_file="${shared_dir}/backup-manual-request.properties"
running_file="${shared_dir}/backup-manual-request.running"
manual_status="${shared_dir}/backup-manual-status.properties"
runner_status="${shared_dir}/backup-policy-runner.status"
lock_dir="${shared_dir}/.backup-scheduler.lock"
flat_get(){ local file="$1" key="$2"; [[ -f "${file}" ]] || return 0; grep -E "^${key}=" "${file}" | tail -n 1 | cut -d= -f2- || true; }
write_status(){ local file="$1"; shift; local tmp="${file}.tmp.$$"; : > "${tmp}"; while [[ $# -gt 1 ]]; do local key="$1" value="$2"; shift 2; value="$(printf '%s' "${value}" | tr '\r\n=' '   ')"; printf '%s=%s\n' "${key}" "${value}" >> "${tmp}"; done; mv "${tmp}" "${file}"; }
write_heartbeat(){ local ready=false; iguana_is_truthy "${IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN:-false}" && ready=true; write_status "${runner_status}" status online last_seen_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" platform unix schedule_ready "${ready}" runner_version manual-backup-v1; }
state_get(){ flat_get "${state_file}" "$1"; }
state_set(){ local key="$1" value="$2" tmp="${state_file}.tmp.$$"; { [[ -f "${state_file}" ]] && grep -v -E "^${key}=" "${state_file}" || true; printf '%s=%s\n' "${key}" "${value}"; } > "${tmp}"; mv "${tmp}" "${state_file}"; }
manual_restore_components(){ case "$1" in critical) printf '%s' 'postgres,minio,shared-config';; full) printf '%s' 'postgres,minio,shared-config,templates,static-js,static-css';; custom) printf '%s' "${IGUANA_BACKUP_CUSTOM_COMPONENTS:-postgres,minio,shared-config}";; *) return 1;; esac; }
process_manual_request(){
  [[ -f "${running_file}" ]] && { echo "[INFO] Manual backup claim already exists."; return 0; }
  [[ -f "${request_file}" ]] || return 0
  mv "${request_file}" "${running_file}" 2>/dev/null || return 0
  local id mode verify local_test req_at req_by started action code
  id="$(flat_get "${running_file}" request_id)"; mode="$(flat_get "${running_file}" mode)"; verify="$(flat_get "${running_file}" verify_restore)"; local_test="$(flat_get "${running_file}" allow_local_test)"; req_at="$(flat_get "${running_file}" requested_at)"; req_by="$(flat_get "${running_file}" requested_by)"; started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  case "${mode}" in critical|full|custom) ;; *) write_status "${manual_status}" request_id "${id}" status error mode "${mode}" finished_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" message "Unsupported manual backup mode."; rm -f "${running_file}"; return 0;; esac
  write_status "${manual_status}" request_id "${id}" status running mode "${mode}" verify_restore "${verify}" allow_local_test "${local_test}" requested_at "${req_at}" requested_by "${req_by}" started_at "${started}" message "Manual backup is running on Docker host."
  ARGS=(--action backup --mode "${mode}"); action=backup
  if iguana_is_truthy "${verify}"; then action=full; ARGS=(--action full --mode "${mode}" --restore-components "$(manual_restore_components "${mode}")"); fi
  iguana_is_truthy "${local_test}" && ARGS+=(--allow-local-destination)
  set +e; "${SCRIPT_DIR}/docker-production-backup.sh" "${ARGS[@]}"; code=$?; set -e
  if [[ "${code}" -eq 0 ]]; then write_status "${manual_status}" request_id "${id}" status success mode "${mode}" verify_restore "${verify}" allow_local_test "${local_test}" requested_at "${req_at}" requested_by "${req_by}" started_at "${started}" finished_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" message "Manual backup completed successfully."; else write_status "${manual_status}" request_id "${id}" status error mode "${mode}" verify_restore "${verify}" allow_local_test "${local_test}" requested_at "${req_at}" requested_by "${req_by}" started_at "${started}" finished_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" message "Manual backup failed with exit code ${code}."; fi
  rm -f "${running_file}"
}
due(){ local prefix="$1" enabled_var="IGUANA_BACKUP_${prefix}_ENABLED" frequency_var="IGUANA_BACKUP_${prefix}_FREQUENCY" time_var="IGUANA_BACKUP_${prefix}_TIME" weekday_var="IGUANA_BACKUP_${prefix}_WEEKDAY"; iguana_is_truthy "${!enabled_var:-false}" || return 1; local now today wd scheduled freq key last code expected; now="$(date +%H:%M)"; today="$(date +%Y-%m-%d)"; wd="$(date +%u)"; scheduled="${!time_var:-02:00}"; [[ "${now}" < "${scheduled}" ]] && return 1; freq="${!frequency_var:-daily}"; if [[ "${freq}" == weekly ]]; then expected="${!weekday_var:-SUN}"; case "${wd}" in 1)code=MON;;2)code=TUE;;3)code=WED;;4)code=THU;;5)code=FRI;;6)code=SAT;;7)code=SUN;; esac; [[ "${code}" == "${expected}" ]] || return 1; elif [[ "${freq}" != daily ]]; then return 1; fi; key="$(printf '%s' "${prefix}" | tr '[:upper:]' '[:lower:]')_last_slot"; last="$(state_get "${key}")"; [[ "${last}" != "${today}" ]]; }
write_heartbeat
mkdir "${lock_dir}" 2>/dev/null || { echo "[INFO] Backup policy runner is already active."; exit 0; }
trap 'write_heartbeat || true; rmdir "${lock_dir}" 2>/dev/null || true' EXIT
process_manual_request
if ! iguana_is_truthy "${IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN:-false}"; then echo "[INFO] Scheduled backup plans skipped: external failure domain is not acknowledged."; exit 0; fi
today="$(date +%Y-%m-%d)"
if due CRITICAL; then "${SCRIPT_DIR}/docker-production-backup.sh" --action backup --mode critical; state_set critical_last_slot "${today}"; fi
if due FULL; then "${SCRIPT_DIR}/docker-production-backup.sh" --action full --mode full --restore-components "postgres,minio,shared-config,templates,static-js,static-css"; state_set full_last_slot "${today}"; fi
