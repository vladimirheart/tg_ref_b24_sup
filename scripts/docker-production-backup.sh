#!/usr/bin/env bash
set -euo pipefail

ACTION="backup"
MODE=""
COMPONENTS=""
RESTORE_COMPONENTS=""
VALIDATE_ONLY=0
ALLOW_LOCAL_DESTINATION=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --action) ACTION="${2:-}"; shift 2 ;;
    --mode) MODE="${2:-}"; shift 2 ;;
    --components) COMPONENTS="${2:-}"; shift 2 ;;
    --restore-components) RESTORE_COMPONENTS="${2:-}"; shift 2 ;;
    --validate-only) VALIDATE_ONLY=1; shift ;;
    --allow-local-destination) ALLOW_LOCAL_DESTINATION=1; shift ;;
    *) echo "[ERROR] Unknown argument: $1" >&2; exit 1 ;;
  esac
done

case "${ACTION}" in backup|restore|full) ;; *) echo "[ERROR] --action must be backup, restore or full" >&2; exit 1 ;; esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BASE_COMPOSE="${REPO_ROOT}/docker-compose.production-contour.yml"
BACKUP_COMPOSE="${REPO_ROOT}/docker-compose.production-backup.yml"
ENV_FILE="${REPO_ROOT}/.env"
BACKUP_CONFIG_LIB="${SCRIPT_DIR}/lib/backup-config.sh"
[[ -f "${BACKUP_CONFIG_LIB}" ]] || { echo "[ERROR] Backup config library is missing: ${BACKUP_CONFIG_LIB}" >&2; exit 1; }
source "${BACKUP_CONFIG_LIB}"
iguana_import_backup_settings "${REPO_ROOT}"

get_setting_value() {
  local name="$1"
  if [[ -n "${!name-}" ]]; then printf '%s' "${!name}"; return 0; fi
  if [[ -f "${ENV_FILE}" ]]; then
    local line
    line="$(grep -E "^${name}=" "${ENV_FILE}" | tail -n 1 || true)"
    [[ -n "${line}" ]] && { printf '%s' "${line#*=}"; return 0; }
  fi
  printf ''
}

normalize_components() {
  local raw="$1"
  local label="$2"
  local output=""
  local item
  IFS=',' read -r -a items <<< "${raw}"
  for item in "${items[@]}"; do
    item="$(printf '%s' "${item}" | tr -d '[:space:]' | tr '[:upper:]' '[:lower:]')"
    [[ -n "${item}" ]] || continue
    case "${item}" in postgres|minio|shared-config|templates|static-js|static-css) ;; *)
      echo "[ERROR] ${label} contains unsupported component: ${item}" >&2; exit 1 ;;
    esac
    case ",${output}," in *",${item},"*) ;; *)
      [[ -z "${output}" ]] && output="${item}" || output="${output},${item}" ;;
    esac
  done
  [[ -n "${output}" ]] || { echo "[ERROR] ${label} must contain at least one component." >&2; exit 1; }
  printf '%s' "${output}"
}

if [[ -z "${MODE}" ]]; then MODE="$(get_setting_value IGUANA_BACKUP_MANUAL_MODE)"; fi
[[ -n "${MODE}" ]] || MODE="critical"
MODE="$(printf '%s' "${MODE}" | tr '[:upper:]' '[:lower:]')"
case "${MODE}" in critical|full|custom) ;; *) echo "[ERROR] Backup mode must be critical, full or custom." >&2; exit 1 ;; esac

case "${MODE}" in
  critical) BACKUP_COMPONENTS="postgres,minio,shared-config" ;;
  full) BACKUP_COMPONENTS="postgres,minio,shared-config,templates,static-js,static-css" ;;
  custom)
    [[ -n "${COMPONENTS}" ]] || COMPONENTS="$(get_setting_value IGUANA_BACKUP_CUSTOM_COMPONENTS)"
    BACKUP_COMPONENTS="$(normalize_components "${COMPONENTS}" "Custom backup components")"
    ;;
esac

if [[ -z "${RESTORE_COMPONENTS}" ]]; then RESTORE_COMPONENTS="$(get_setting_value IGUANA_BACKUP_RESTORE_COMPONENTS)"; fi
[[ -n "${RESTORE_COMPONENTS}" ]] || RESTORE_COMPONENTS="postgres,minio,shared-config"
RESTORE_COMPONENTS="$(normalize_components "${RESTORE_COMPONENTS}" "Restore components")"

DESTINATION="$(get_setting_value IGUANA_BACKUP_DESTINATION_DIR)"
[[ -n "${DESTINATION}" ]] || { echo "[ERROR] Backup destination is not configured." >&2; exit 1; }
if [[ "${ALLOW_LOCAL_DESTINATION}" != "1" ]]; then
  ack="$(get_setting_value IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN)"
  iguana_is_truthy "${ack}" || { echo "[ERROR] Production backup requires external failure-domain acknowledgement." >&2; exit 1; }
fi

if [[ "${DESTINATION}" != /* ]]; then
  [[ "${ALLOW_LOCAL_DESTINATION}" == "1" ]] || { echo "[ERROR] Backup destination must be an absolute off-host path." >&2; exit 1; }
  DESTINATION="${REPO_ROOT}/${DESTINATION}"
fi
if [[ "${ALLOW_LOCAL_DESTINATION}" != "1" && "${DESTINATION}/" == "${REPO_ROOT}/"* ]]; then
  echo "[ERROR] Backup destination is inside repository failure domain." >&2
  exit 1
fi
if [[ ! -d "${DESTINATION}" ]]; then
  [[ "${ALLOW_LOCAL_DESTINATION}" == "1" ]] && mkdir -p "${DESTINATION}" || { echo "[ERROR] Backup destination is not mounted: ${DESTINATION}" >&2; exit 1; }
fi
probe="${DESTINATION}/.iguana-write-probe-$$"; printf probe > "${probe}"; rm -f "${probe}"

export IGUANA_BACKUP_DESTINATION_DIR="${DESTINATION}"
export IGUANA_BACKUP_MODE="${MODE}"
export IGUANA_BACKUP_COMPONENTS="${BACKUP_COMPONENTS}"
export IGUANA_BACKUP_RESTORE_COMPONENTS="${RESTORE_COMPONENTS}"

has_component() { case ",$1," in *",$2,"*) return 0 ;; *) return 1 ;; esac; }
file_components() {
  local list="$1" out="" c
  for c in shared-config templates static-js static-css; do
    if has_component "${list}" "${c}"; then [[ -z "${out}" ]] && out="${c}" || out="${out},${c}"; fi
  done
  printf '%s' "${out}"
}

FILE_BACKUP_COMPONENTS="$(file_components "${BACKUP_COMPONENTS}")"
FILE_RESTORE_COMPONENTS="$(file_components "${RESTORE_COMPONENTS}")"

command -v docker >/dev/null 2>&1 || { echo "[ERROR] Docker is unavailable." >&2; exit 1; }
BASE_ARGS=(compose --project-directory "${REPO_ROOT}")
[[ -f "${ENV_FILE}" ]] && BASE_ARGS+=(--env-file "${ENV_FILE}")
BASE_ARGS+=(-f "${BASE_COMPOSE}" -f "${BACKUP_COMPOSE}" --profile backup)

docker "${BASE_ARGS[@]}" config -q
if [[ "${VALIDATE_ONLY}" == "1" ]]; then echo "[GREEN] Backup compose/policy validation succeeded."; exit 0; fi

echo "[INFO] Backup destination: ${DESTINATION}"
echo "[INFO] Action: ${ACTION}; mode=${MODE}"
echo "[INFO] Backup components: ${BACKUP_COMPONENTS}"
echo "[INFO] Restore components: ${RESTORE_COMPONENTS}"

if [[ "${ACTION}" == "backup" || "${ACTION}" == "full" ]]; then
  has_component "${BACKUP_COMPONENTS}" postgres && docker "${BASE_ARGS[@]}" run --rm postgres-backup
  has_component "${BACKUP_COMPONENTS}" minio && docker "${BASE_ARGS[@]}" run --rm --build minio-backup
  if [[ -n "${FILE_BACKUP_COMPONENTS}" ]]; then
    export IGUANA_BACKUP_COMPONENTS="${FILE_BACKUP_COMPONENTS}"
    docker "${BASE_ARGS[@]}" run --rm files-backup
  fi
fi

if [[ "${ACTION}" == "restore" || "${ACTION}" == "full" ]]; then
  TARGETS=()
  has_component "${RESTORE_COMPONENTS}" postgres && TARGETS+=(postgres-restore-target)
  has_component "${RESTORE_COMPONENTS}" minio && TARGETS+=(minio-restore-target)
  if [[ "${#TARGETS[@]}" -gt 0 ]]; then docker "${BASE_ARGS[@]}" up -d "${TARGETS[@]}"; fi
  cleanup() { [[ "${#TARGETS[@]}" -eq 0 ]] || docker "${BASE_ARGS[@]}" rm -s -f "${TARGETS[@]}" >/dev/null 2>&1 || true; }
  trap cleanup EXIT
  has_component "${RESTORE_COMPONENTS}" postgres && docker "${BASE_ARGS[@]}" run --rm postgres-restore-rehearsal
  has_component "${RESTORE_COMPONENTS}" minio && docker "${BASE_ARGS[@]}" run --rm --build minio-restore-rehearsal
  if [[ -n "${FILE_RESTORE_COMPONENTS}" ]]; then
    export IGUANA_BACKUP_RESTORE_COMPONENTS="${FILE_RESTORE_COMPONENTS}"
    docker "${BASE_ARGS[@]}" run --rm files-restore-rehearsal
  fi
  cleanup
  trap - EXIT
fi

echo "[GREEN] Iguana production backup action completed: ${ACTION}; mode=${MODE}"
