#!/usr/bin/env bash
set -euo pipefail

EDGE=0
REMOVE_VOLUMES=0
VALIDATE_ONLY=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --edge) EDGE=1; shift ;;
    --remove-volumes) REMOVE_VOLUMES=1; shift ;;
    --validate-only) VALIDATE_ONLY=1; shift ;;
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
ENV_FILE="${REPO_ROOT}/.env"

[[ -f "${COMPOSE_FILE}" ]] || { echo "[ERROR] Compose file not found: ${COMPOSE_FILE}" >&2; exit 1; }
if [[ "${EDGE}" == "1" && ! -f "${EDGE_COMPOSE_FILE}" ]]; then
  echo "[ERROR] Edge compose file not found: ${EDGE_COMPOSE_FILE}" >&2
  exit 1
fi

command -v docker >/dev/null 2>&1 || {
  if [[ "${VALIDATE_ONLY}" == "1" ]]; then
    echo "[INFO] File validation succeeded; Docker is not available, compose config was not executed."
    exit 0
  fi
  echo "[ERROR] Docker is not installed or not available in PATH." >&2
  exit 1
}

BASE_ARGS=(compose --project-directory "${REPO_ROOT}")
[[ -f "${ENV_FILE}" ]] && BASE_ARGS+=(--env-file "${ENV_FILE}")
BASE_ARGS+=(-f "${COMPOSE_FILE}")
[[ "${EDGE}" == "1" ]] && BASE_ARGS+=(-f "${EDGE_COMPOSE_FILE}")

if [[ "${VALIDATE_ONLY}" == "1" ]]; then
  docker "${BASE_ARGS[@]}" config -q
  echo "[INFO] Validation succeeded."
  exit 0
fi

ARGS=("${BASE_ARGS[@]}" down --remove-orphans)
[[ "${REMOVE_VOLUMES}" == "1" ]] && ARGS+=(-v)

echo "[INFO] Stopping Iguana docker production contour (panel-web / ops-worker / db-migrate)"
docker "${ARGS[@]}"
echo "[INFO] Iguana docker production contour stopped."
