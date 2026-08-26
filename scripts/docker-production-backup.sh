#!/usr/bin/env bash
set -euo pipefail

ACTION="backup"
VALIDATE_ONLY=0
ALLOW_LOCAL_DESTINATION=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --action) ACTION="${2:-}"; shift 2 ;;
    --validate-only) VALIDATE_ONLY=1; shift ;;
    --allow-local-destination) ALLOW_LOCAL_DESTINATION=1; shift ;;
    *) echo "[ERROR] Unknown argument: $1" >&2; exit 1 ;;
  esac
done

case "${ACTION}" in
  backup|restore|full) ;;
  *) echo "[ERROR] --action must be backup, restore or full" >&2; exit 1 ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
BASE_COMPOSE="${REPO_ROOT}/docker-compose.production-contour.yml"
BACKUP_COMPOSE="${REPO_ROOT}/docker-compose.production-backup.yml"
ENV_FILE="${REPO_ROOT}/.env"

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

DESTINATION="$(get_setting_value IGUANA_BACKUP_DESTINATION_DIR)"
[[ -n "${DESTINATION}" ]] || { echo "[ERROR] IGUANA_BACKUP_DESTINATION_DIR is required." >&2; exit 1; }

if [[ "${DESTINATION}" != /* ]]; then
  if [[ "${ALLOW_LOCAL_DESTINATION}" != "1" ]]; then
    echo "[ERROR] Backup destination must be an absolute off-host path in production." >&2
    exit 1
  fi
  DESTINATION="${REPO_ROOT}/${DESTINATION}"
fi

if [[ "${ALLOW_LOCAL_DESTINATION}" != "1" && "${DESTINATION}/" == "${REPO_ROOT}/"* ]]; then
  echo "[ERROR] Backup destination is inside the repository failure domain: ${DESTINATION}" >&2
  exit 1
fi

if [[ ! -d "${DESTINATION}" ]]; then
  if [[ "${ALLOW_LOCAL_DESTINATION}" == "1" ]]; then mkdir -p "${DESTINATION}"; else
    echo "[ERROR] Backup destination is not mounted: ${DESTINATION}" >&2; exit 1
  fi
fi

probe="${DESTINATION}/.iguana-write-probe-$$"
printf 'probe' > "${probe}"
rm -f "${probe}"

command -v docker >/dev/null 2>&1 || { echo "[ERROR] Docker is unavailable." >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "[ERROR] docker compose is unavailable." >&2; exit 1; }

export IGUANA_BACKUP_DESTINATION_DIR="${DESTINATION}"
BASE_ARGS=(compose --project-directory "${REPO_ROOT}")
[[ -f "${ENV_FILE}" ]] && BASE_ARGS+=(--env-file "${ENV_FILE}")
BASE_ARGS+=(-f "${BASE_COMPOSE}" -f "${BACKUP_COMPOSE}" --profile backup)

docker "${BASE_ARGS[@]}" config -q
if [[ "${VALIDATE_ONLY}" == "1" ]]; then
  echo "[GREEN] Backup compose validation succeeded."
  exit 0
fi

if [[ "${ACTION}" == "backup" || "${ACTION}" == "full" ]]; then
  docker "${BASE_ARGS[@]}" run --rm postgres-backup
  docker "${BASE_ARGS[@]}" run --rm --build minio-backup
fi

if [[ "${ACTION}" == "restore" || "${ACTION}" == "full" ]]; then
  docker "${BASE_ARGS[@]}" up -d postgres-restore-target minio-restore-target
  cleanup_restore_targets() {
    docker "${BASE_ARGS[@]}" rm -s -f postgres-restore-target minio-restore-target >/dev/null 2>&1 || true
  }
  trap cleanup_restore_targets EXIT
  docker "${BASE_ARGS[@]}" run --rm postgres-restore-rehearsal
  docker "${BASE_ARGS[@]}" run --rm --build minio-restore-rehearsal
  cleanup_restore_targets
  trap - EXIT
fi

echo "[GREEN] Iguana production backup action completed: ${ACTION}"
