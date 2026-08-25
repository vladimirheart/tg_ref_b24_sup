#!/usr/bin/env bash
set -euo pipefail

EDGE=0
REMOVE_VOLUMES=0
VALIDATE_ONLY=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --edge)
      EDGE=1
      shift
      ;;
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
EDGE_COMPOSE_FILE="${REPO_ROOT}/docker-compose.production-edge.yml"

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "[ERROR] Compose file not found: ${COMPOSE_FILE}" >&2
  exit 1
fi
if [[ "${EDGE}" == "1" && ! -f "${EDGE_COMPOSE_FILE}" ]]; then
  echo "[ERROR] Edge compose file not found: ${EDGE_COMPOSE_FILE}" >&2
  exit 1
fi

if [[ "${VALIDATE_ONLY}" == "1" ]]; then
  echo "[INFO] Validation succeeded."
  echo "[INFO] Compose file: ${COMPOSE_FILE}"
  if [[ "${EDGE}" == "1" ]]; then
    echo "[INFO] Edge compose file: ${EDGE_COMPOSE_FILE}"
  fi
  echo "[INFO] Edge enabled: ${EDGE}"
  echo "[INFO] Remove volumes: ${REMOVE_VOLUMES}"
  exit 0
fi

command -v docker >/dev/null 2>&1 || {
  echo "[ERROR] Docker is not installed or not available in PATH." >&2
  exit 1
}

ARGS=(compose -f "${COMPOSE_FILE}")
if [[ "${EDGE}" == "1" ]]; then
  ARGS+=(-f "${EDGE_COMPOSE_FILE}")
fi
ARGS+=(down)
if [[ "${REMOVE_VOLUMES}" == "1" ]]; then
  ARGS+=(-v)
fi

echo "[INFO] Stopping Iguana docker production contour"
echo "[INFO] Edge enabled: ${EDGE}"

docker "${ARGS[@]}"

echo "[INFO] Iguana docker production contour stopped."
