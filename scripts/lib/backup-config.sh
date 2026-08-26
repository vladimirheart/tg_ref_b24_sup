#!/usr/bin/env bash

iguana_read_dotenv_value() {
  local repo_root="$1"
  local name="$2"
  local env_file="${repo_root}/.env"
  if [[ ! -f "${env_file}" ]]; then
    printf ''
    return 0
  fi
  local line
  line="$(grep -E "^${name}=" "${env_file}" | tail -n 1 || true)"
  [[ -n "${line}" ]] && { printf '%s' "${line#*=}"; return 0; }
  printf ''
}

iguana_resolve_shared_config_dir() {
  local repo_root="$1"
  local configured="${IGUANA_SHARED_CONFIG_DIR:-}"
  if [[ -z "${configured}" ]]; then
    configured="$(iguana_read_dotenv_value "${repo_root}" "IGUANA_SHARED_CONFIG_DIR")"
  fi
  [[ -n "${configured}" ]] || configured="config/shared"
  if [[ "${configured}" = /* ]]; then
    printf '%s' "${configured}"
  else
    configured="${configured#./}"
    printf '%s/%s' "${repo_root}" "${configured}"
  fi
}

iguana_is_truthy() {
  local value="${1:-}"
  case "$(printf '%s' "${value}" | tr '[:upper:]' '[:lower:]')" in
    1|true|yes|on) return 0 ;;
    *) return 1 ;;
  esac
}

iguana_import_backup_settings() {
  local repo_root="$1"
  local shared_dir settings_file
  shared_dir="$(iguana_resolve_shared_config_dir "${repo_root}")"
  settings_file="${shared_dir}/backup.properties"
  [[ -f "${settings_file}" ]] || return 0

  local line key value
  while IFS= read -r line || [[ -n "${line}" ]]; do
    line="${line%$'\r'}"
    [[ -n "${line}" ]] || continue
    [[ "${line}" != \#* && "${line}" != \!* ]] || continue
    [[ "${line}" == *=* ]] || continue
    key="${line%%=*}"
    value="${line#*=}"
    case "${key}" in
      IGUANA_BACKUP_DESTINATION_DIR|\
      IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN|\
      IGUANA_BACKUP_RETENTION_DAYS|\
      IGUANA_MINIO_BACKUP_RETENTION_DAYS|\
      IGUANA_BACKUP_ARCHIVE_FORMAT|\
      IGUANA_BACKUP_MANUAL_MODE|\
      IGUANA_BACKUP_CUSTOM_COMPONENTS|\
      IGUANA_BACKUP_RESTORE_COMPONENTS|\
      IGUANA_BACKUP_CRITICAL_ENABLED|\
      IGUANA_BACKUP_CRITICAL_FREQUENCY|\
      IGUANA_BACKUP_CRITICAL_TIME|\
      IGUANA_BACKUP_CRITICAL_WEEKDAY|\
      IGUANA_BACKUP_FULL_ENABLED|\
      IGUANA_BACKUP_FULL_FREQUENCY|\
      IGUANA_BACKUP_FULL_TIME|\
      IGUANA_BACKUP_FULL_WEEKDAY)
        [[ -n "${!key:-}" ]] || export "${key}=${value}"
        ;;
    esac
  done < "${settings_file}"

  echo "[INFO] Backup policy loaded from ${settings_file}"
}
