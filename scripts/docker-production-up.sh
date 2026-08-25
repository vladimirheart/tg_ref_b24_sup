#!/usr/bin/env bash
set -euo pipefail

TELEGRAM=0
VK=0
MAX=0
BUILD=0
DETACH=1
VALIDATE_ONLY=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --telegram)
      TELEGRAM=1
      shift
      ;;
    --vk)
      VK=1
      shift
      ;;
    --max)
      MAX=1
      shift
      ;;
    --build)
      BUILD=1
      shift
      ;;
    --no-detach)
      DETACH=0
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

PROFILES=()
if [[ "${TELEGRAM}" == "1" ]]; then
  PROFILES+=("telegram")
fi
if [[ "${VK}" == "1" ]]; then
  PROFILES+=("vk")
fi
if [[ "${MAX}" == "1" ]]; then
  PROFILES+=("max")
fi

mkdir -p \
  "${REPO_ROOT}/attachments" \
  "${REPO_ROOT}/attachments/knowledge_base" \
  "${REPO_ROOT}/attachments/forms" \
  "${REPO_ROOT}/attachments/avatars" \
  "${REPO_ROOT}/logs" \
  "${REPO_ROOT}/bot_databases"

if [[ "${VALIDATE_ONLY}" == "1" ]]; then
  echo "[INFO] Validation succeeded."
  echo "[INFO] Compose file: ${COMPOSE_FILE}"
  if [[ "${#PROFILES[@]}" -gt 0 ]]; then
    echo "[INFO] Profiles: ${PROFILES[*]}"
  else
    echo "[INFO] Profiles: none (infra + panel only)"
  fi
  exit 0
fi

command -v docker >/dev/null 2>&1 || {
  echo "[ERROR] Docker is not installed or not available in PATH." >&2
  exit 1
}

docker compose version >/dev/null 2>&1 || {
  echo "[ERROR] docker compose is unavailable." >&2
  exit 1
}

ARGS=(compose -f "${COMPOSE_FILE}")
for profile in "${PROFILES[@]}"; do
  ARGS+=(--profile "${profile}")
done
ARGS+=(up)
if [[ "${BUILD}" == "1" ]]; then
  ARGS+=(--build)
fi
if [[ "${DETACH}" == "1" ]]; then
  ARGS+=(-d)
fi

echo "[INFO] Starting Iguana docker production contour"
if [[ "${#PROFILES[@]}" -gt 0 ]]; then
  echo "[INFO] Profiles: ${PROFILES[*]}"
else
  echo "[INFO] Profiles: none (infra + panel only)"
fi

docker "${ARGS[@]}"

echo "[INFO] Iguana docker production contour started."
