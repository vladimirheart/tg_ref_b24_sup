#!/usr/bin/env bash
set -euo pipefail

ACTION="status"
BOT="telegram"
CONFIRM_EMERGENCY=0
BUILD=0
PROJECT_NAME=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --action) ACTION="${2:-}"; shift 2 ;;
    --bot) BOT="${2:-}"; shift 2 ;;
    --confirm-emergency-mode) CONFIRM_EMERGENCY=1; shift ;;
    --build) BUILD=1; shift ;;
    --project-name) PROJECT_NAME="${2:-}"; shift 2 ;;
    *) echo "[ERROR] Unknown argument: $1" >&2; exit 1 ;;
  esac
done

case "${ACTION}" in
  start|stop|status) ;;
  *) echo "[ERROR] --action must be start, stop or status." >&2; exit 1 ;;
esac
case "${BOT}" in
  telegram|vk|max) ;;
  *) echo "[ERROR] --bot must be telegram, vk or max." >&2; exit 1 ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${REPO_ROOT}/.env"
MAIN_COMPOSE="${REPO_ROOT}/docker-compose.production-contour.yml"
LEGACY_COMPOSE="${REPO_ROOT}/docker-compose.production-legacy-bots.yml"
SERVICE_NAME="bot-${BOT}"

[[ -f "${MAIN_COMPOSE}" ]] || { echo "[ERROR] Main production compose is missing: ${MAIN_COMPOSE}" >&2; exit 1; }
[[ -f "${LEGACY_COMPOSE}" ]] || { echo "[ERROR] Emergency legacy compose is missing: ${LEGACY_COMPOSE}" >&2; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "[ERROR] Docker is not installed or not available in PATH." >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "[ERROR] docker compose is unavailable." >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "[ERROR] docker daemon is unavailable." >&2; exit 1; }

read_dotenv_value() {
  local name="$1"
  [[ -f "${ENV_FILE}" ]] || { printf ''; return 0; }
  local line
  line="$(grep -E "^${name}=" "${ENV_FILE}" | tail -n 1 || true)"
  [[ -n "${line}" ]] && printf '%s' "${line#*=}" || printf ''
}

resolve_project_name() {
  if [[ -n "${PROJECT_NAME}" ]]; then
    printf '%s' "${PROJECT_NAME}"
    return 0
  fi
  if [[ -n "${COMPOSE_PROJECT_NAME:-}" ]]; then
    printf '%s' "${COMPOSE_PROJECT_NAME}"
    return 0
  fi
  local from_file
  from_file="$(read_dotenv_value COMPOSE_PROJECT_NAME)"
  if [[ -n "${from_file}" ]]; then
    printf '%s' "${from_file}"
    return 0
  fi
  basename "${REPO_ROOT}"
}

PROJECT="$(resolve_project_name)"

service_ids() {
  local service="$1"
  docker ps -q \
    --filter "label=com.docker.compose.project=${PROJECT}" \
    --filter "label=com.docker.compose.service=${service}" | tr -d '\r'
}

service_is_running() {
  [[ -n "$(service_ids "$1")" ]]
}

legacy_running_services() {
  local result=()
  local service
  for service in bot-telegram bot-vk bot-max; do
    service_is_running "${service}" && result+=("${service}")
  done
  if [[ ${#result[@]} -gt 0 ]]; then
    local IFS=,
    printf '%s' "${result[*]}"
  else
    printf 'none'
  fi
}

assert_bot_runner_stopped() {
  if service_is_running bot-runner; then
    echo "[ERROR] Emergency legacy bot start blocked: bot-runner is running. Stop bot-runner first; mixed runtime ownership is forbidden." >&2
    exit 20
  fi
}

assert_base_runtime_available() {
  local missing=()
  local service
  for service in postgres rabbitmq redis minio panel-web; do
    service_is_running "${service}" || missing+=("${service}")
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    local IFS=', '
    echo "[ERROR] Emergency legacy bot start blocked: required base services are not running: ${missing[*]}" >&2
    exit 20
  fi
}

COMPOSE_ARGS=(compose --project-directory "${REPO_ROOT}")
[[ -f "${ENV_FILE}" ]] && COMPOSE_ARGS+=(--env-file "${ENV_FILE}")
COMPOSE_ARGS+=(-f "${MAIN_COMPOSE}" -f "${LEGACY_COMPOSE}" -p "${PROJECT}")

if [[ "${ACTION}" == "status" ]]; then
  runner=false
  service_is_running bot-runner && runner=true
  legacy="$(legacy_running_services)"
  echo "PROJECT=${PROJECT}"
  echo "BOT_RUNNER_RUNNING=${runner}"
  echo "LEGACY_RUNNING=${legacy}"
  if [[ "${runner}" == "true" && "${legacy}" != "none" ]]; then
    echo "MIXED_OWNERSHIP=BLOCK"
    exit 20
  fi
  echo "MIXED_OWNERSHIP=GREEN"
  exit 0
fi

if [[ "${ACTION}" == "stop" ]]; then
  docker "${COMPOSE_ARGS[@]}" stop "${SERVICE_NAME}" >/dev/null 2>&1 || true
  docker "${COMPOSE_ARGS[@]}" rm -f -s "${SERVICE_NAME}" >/dev/null 2>&1 || true
  service_is_running "${SERVICE_NAME}" && { echo "[ERROR] Emergency legacy bot stop failed: ${SERVICE_NAME} is still running." >&2; exit 1; }
  echo "LEGACY_BOT_STOP=GREEN | service=${SERVICE_NAME}"
  exit 0
fi

(( CONFIRM_EMERGENCY == 1 )) || { echo "[ERROR] Emergency legacy bot start requires --confirm-emergency-mode." >&2; exit 20; }
assert_bot_runner_stopped
assert_base_runtime_available

docker "${COMPOSE_ARGS[@]}" config -q
UP_ARGS=("${COMPOSE_ARGS[@]}" up -d --no-deps)
(( BUILD == 1 )) && UP_ARGS+=(--build)
UP_ARGS+=("${SERVICE_NAME}")
docker "${UP_ARGS[@]}"

if service_is_running bot-runner; then
  docker "${COMPOSE_ARGS[@]}" stop "${SERVICE_NAME}" >/dev/null 2>&1 || true
  docker "${COMPOSE_ARGS[@]}" rm -f -s "${SERVICE_NAME}" >/dev/null 2>&1 || true
  echo "[ERROR] Emergency legacy bot start rolled back because bot-runner appeared during startup. Mixed runtime ownership was prevented." >&2
  exit 20
fi

service_is_running "${SERVICE_NAME}" || { echo "[ERROR] Emergency legacy bot start failed: ${SERVICE_NAME} is not running." >&2; exit 1; }
echo "LEGACY_BOT_START=GREEN | service=${SERVICE_NAME}; project=${PROJECT}"
echo "BOT_RUNNER_RUNNING=false"
echo "MIXED_OWNERSHIP=GREEN"
