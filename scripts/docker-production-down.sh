#!/usr/bin/env bash
set -euo pipefail

REMOVE_VOLUMES=0
VALIDATE_ONLY=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --remove-volumes)
      REMOVE_VOLUMES=1
      shift
      ;;
    --validate-only)
      VALIDATE_ONLY=1
      shift
      ;;
    *)
      echo "[ERROR] Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
COMPOSE_FILE="${REPO_ROOT}/docker-compose.production-contour.yml"

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "[ERROR] Compose file not found: ${COMPOSE_FILE}" >&2
  exit 1
fi

if [[ "${VALIDATE_ONLY}" == "1" ]]; then
  echo "[INFO] Validation succeeded."
  echo "[INFO] Compose file: ${COMPOSE_FILE}"
  echo "[INFO] Remove volumes: ${REMOVE_VOLUMES}"
  exit 0
fi

command -v docker >/dev/null 2>&1 || {
  echo "[ERROR] Docker is not installed or not available in PATH." >&2
  exit 1
}

ARGS=(compose -f "${COMPOSE_FILE}" down)
if [[ "${REMOVE_VOLUMES}" == "1" ]]; then
  ARGS+=(-v)
fi

echo "[INFO] Stopping Iguana docker production contour"

docker "${ARGS[@]}"

echo "[INFO] Iguana docker production contour stopped."
