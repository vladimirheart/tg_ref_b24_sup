#!/usr/bin/env bash
set -euo pipefail

TELEGRAM=0
VK=0
MAX=0
EDGE=0
OBSERVABILITY=0
BUILD=0
DETACH=1
VALIDATE_ONLY=0
ALLOW_INSECURE_DEFAULTS=0
WEB_REPLICAS=0
WORKER_REPLICAS=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --telegram) TELEGRAM=1; shift ;;
    --vk) VK=1; shift ;;
    --max) MAX=1; shift ;;
    --edge) EDGE=1; shift ;;
    --observability) OBSERVABILITY=1; shift ;;
    --build) BUILD=1; shift ;;
    --no-detach) DETACH=0; shift ;;
    --validate-only) VALIDATE_ONLY=1; shift ;;
    --allow-insecure-defaults) ALLOW_INSECURE_DEFAULTS=1; shift ;;
    --web-replicas)
      WEB_REPLICAS="${2:-}"
      shift 2
      ;;
    --worker-replicas)
      WORKER_REPLICAS="${2:-}"
      shift 2
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
OBSERVABILITY_COMPOSE_FILE="${REPO_ROOT}/docker-compose.production-observability.yml"
ENV_FILE="${REPO_ROOT}/.env"

get_setting_value() {
  local name="$1"
  local value="${!name-}"
  if [[ -n "${value}" ]]; then
    printf '%s' "${value}"
    return 0
  fi
  if [[ -f "${ENV_FILE}" ]]; then
    local line
    line="$(grep -E "^${name}=" "${ENV_FILE}" | tail -n 1 || true)"
    if [[ -n "${line}" ]]; then
      printf '%s' "${line#*=}"
      return 0
    fi
  fi
  printf ''
}

resolve_replica_count() {
  local explicit="$1"
  local setting="$2"
  local fallback="$3"
  local value="${explicit}"

  if [[ "${value}" == "0" || -z "${value}" ]]; then
    value="$(get_setting_value "${setting}")"
  fi
  if [[ -z "${value}" ]]; then
    value="${fallback}"
  fi
  if ! [[ "${value}" =~ ^[1-9][0-9]*$ ]]; then
    echo "[ERROR] ${setting} must be a positive integer." >&2
    exit 1
  fi
  printf '%s' "${value}"
}

is_truthy() {
  local value="${1:-}"
  case "$(printf '%s' "${value}" | tr '[:upper:]' '[:lower:]')" in
    1|true|yes|on) return 0 ;;
    *) return 1 ;;
  esac
}

assert_required_file() {
  local path="$1"
  local label="$2"
  if [[ ! -f "${path}" ]]; then
    echo "[ERROR] ${label} is missing: ${path}" >&2
    exit 1
  fi
}

assert_required_setting() {
  local name="$1"
  local message="$2"
  local value
  value="$(get_setting_value "${name}")"
  if [[ -z "${value}" ]]; then
    echo "[ERROR] ${message} (${name}). Configure it via process environment or repository .env." >&2
    exit 1
  fi
}

assert_non_default_secret() {
  local name="$1"
  local message="$2"
  shift 2
  local value
  value="$(get_setting_value "${name}")"
  if [[ -z "${value}" ]]; then
    echo "[ERROR] ${message} (${name})" >&2
    exit 1
  fi
  local candidate
  for candidate in "$@"; do
    if [[ "${value}" == "${candidate}" ]]; then
      echo "[ERROR] ${message} (${name} uses disallowed default '${candidate}')." >&2
      exit 1
    fi
  done
}

resolve_repo_path() {
  local value="$1"
  if [[ -z "${value}" ]]; then
    value="./deploy/nginx/certs"
  fi
  if [[ "${value}" = /* ]]; then
    printf '%s' "${value}"
  else
    printf '%s' "${REPO_ROOT}/${value}"
  fi
}

invoke_preflight_checks() {
  assert_required_file "${REPO_ROOT}/config/shared/settings.json" "Shared config settings"
  assert_required_file "${REPO_ROOT}/config/shared/locations.json" "Shared config locations"
  assert_required_file "${REPO_ROOT}/config/shared/org_structure.json" "Shared config org structure"

  if [[ "${ALLOW_INSECURE_DEFAULTS}" == "1" ]]; then
    assert_required_setting "APP_INTERNAL_BOT_API_TOKEN" "Internal bot API token must be configured"
    assert_required_setting "APP_SECURITY_REMEMBER_ME_KEY" "Remember-me key must be configured"
    assert_required_setting "MONITORING_CREDENTIALS_MASTER_KEY" "Shared monitoring credentials master key is required by split backend roles"
    assert_required_setting "IGUANA_POSTGRES_PASSWORD" "PostgreSQL password must be configured"
    assert_required_setting "IGUANA_RABBITMQ_PASSWORD" "RabbitMQ password must be configured"
    assert_required_setting "IGUANA_REDIS_PASSWORD" "Redis password must be configured"
    assert_required_setting "APP_STORAGE_OBJECT_ACCESS_KEY" "Object storage access key must be configured"
    assert_required_setting "APP_STORAGE_OBJECT_SECRET_KEY" "Object storage secret key must be configured"
    assert_required_setting "APP_STORAGE_OBJECT_BUCKET" "Object storage bucket must be configured"
  else
    assert_non_default_secret "APP_INTERNAL_BOT_API_TOKEN" "Internal bot API token must be overridden" "change-me" "iguana-internal-bot-token"
    assert_non_default_secret "APP_SECURITY_REMEMBER_ME_KEY" "Remember-me key must be overridden" "change-me" "iguana-panel-remember-me"
    assert_non_default_secret "MONITORING_CREDENTIALS_MASTER_KEY" "Shared monitoring credentials master key must be overridden" "change-me" "iguana-monitoring-key"
    assert_non_default_secret "IGUANA_POSTGRES_PASSWORD" "PostgreSQL password must be overridden" "iguana"
    assert_non_default_secret "IGUANA_RABBITMQ_PASSWORD" "RabbitMQ password must be overridden" "iguana"
    assert_non_default_secret "IGUANA_REDIS_PASSWORD" "Redis password must be overridden" "iguana-redis"
    assert_non_default_secret "APP_STORAGE_OBJECT_ACCESS_KEY" "Object storage access key must be overridden" "iguana-minio"
    assert_non_default_secret "APP_STORAGE_OBJECT_SECRET_KEY" "Object storage secret key must be overridden" "iguana-minio-secret"
    assert_non_default_secret "APP_STORAGE_OBJECT_BUCKET" "Object storage bucket must be overridden for production-like launch" "iguana"
  fi

  if [[ "${OBSERVABILITY}" == "1" ]]; then
    if [[ "${ALLOW_INSECURE_DEFAULTS}" == "1" ]]; then
      assert_required_setting "IGUANA_GRAFANA_ADMIN_PASSWORD" "Grafana admin password must be configured"
    else
      assert_non_default_secret "IGUANA_GRAFANA_ADMIN_PASSWORD" "Grafana admin password must be overridden" "change-me" "admin" "grafana"
    fi
  fi

  if [[ "${TELEGRAM}" == "1" ]]; then
    assert_required_setting "TELEGRAM_BOT_TOKEN" "Telegram profile requires TELEGRAM_BOT_TOKEN"
    assert_required_setting "TELEGRAM_BOT_USERNAME" "Telegram profile requires TELEGRAM_BOT_USERNAME"
    assert_required_setting "GROUP_CHAT_ID" "Telegram profile requires GROUP_CHAT_ID"
  fi
  if [[ "${VK}" == "1" ]]; then
    assert_required_setting "VK_BOT_TOKEN" "VK profile requires VK_BOT_TOKEN"
    assert_required_setting "VK_GROUP_ID" "VK profile requires VK_GROUP_ID"
    assert_required_setting "VK_OPERATOR_CHAT_ID" "VK profile requires VK_OPERATOR_CHAT_ID"
  fi
  if [[ "${MAX}" == "1" ]]; then
    assert_required_setting "MAX_BOT_TOKEN" "MAX profile requires MAX_BOT_TOKEN"
    assert_required_setting "MAX_CHANNEL_ID" "MAX profile requires MAX_CHANNEL_ID"
    assert_required_setting "MAX_SUPPORT_CHAT_ID" "MAX profile requires MAX_SUPPORT_CHAT_ID"
  fi

  if [[ "${EDGE}" == "1" ]]; then
    if [[ "${ALLOW_INSECURE_DEFAULTS}" == "1" ]]; then
      assert_required_setting "IGUANA_PUBLIC_HOST" "Edge contour requires IGUANA_PUBLIC_HOST"
    else
      assert_non_default_secret "IGUANA_PUBLIC_HOST" "Edge contour requires explicit public host" "localhost" "127.0.0.1" "example.com"
    fi

    local tls_enabled
    tls_enabled="$(get_setting_value "IGUANA_EDGE_TLS_ENABLED")"
    if is_truthy "${tls_enabled}"; then
      local cert_dir
      cert_dir="$(resolve_repo_path "$(get_setting_value "IGUANA_EDGE_CERTS_DIR")")"
      assert_required_file "${cert_dir}/fullchain.pem" "Edge TLS certificate"
      assert_required_file "${cert_dir}/privkey.pem" "Edge TLS private key"
    fi
  fi
}

[[ -f "${COMPOSE_FILE}" ]] || { echo "[ERROR] Compose file not found: ${COMPOSE_FILE}" >&2; exit 1; }
if [[ "${EDGE}" == "1" && ! -f "${EDGE_COMPOSE_FILE}" ]]; then
  echo "[ERROR] Edge compose file not found: ${EDGE_COMPOSE_FILE}" >&2
  exit 1
fi
if [[ "${OBSERVABILITY}" == "1" && ! -f "${OBSERVABILITY_COMPOSE_FILE}" ]]; then
  echo "[ERROR] Observability compose file not found: ${OBSERVABILITY_COMPOSE_FILE}" >&2
  exit 1
fi

PROFILES=()
[[ "${TELEGRAM}" == "1" ]] && PROFILES+=("telegram")
[[ "${VK}" == "1" ]] && PROFILES+=("vk")
[[ "${MAX}" == "1" ]] && PROFILES+=("max")

WEB_REPLICAS="$(resolve_replica_count "${WEB_REPLICAS}" "IGUANA_PANEL_WEB_REPLICAS" "1")"
WORKER_REPLICAS="$(resolve_replica_count "${WORKER_REPLICAS}" "IGUANA_OPS_WORKER_REPLICAS" "1")"

mkdir -p \
  "${REPO_ROOT}/attachments/knowledge_base" \
  "${REPO_ROOT}/attachments/forms" \
  "${REPO_ROOT}/attachments/avatars" \
  "${REPO_ROOT}/logs" \
  "${REPO_ROOT}/bot_databases" \
  "${REPO_ROOT}/deploy/nginx/certs"

invoke_preflight_checks

command -v docker >/dev/null 2>&1 || {
  echo "[ERROR] Docker is not installed or not available in PATH." >&2
  exit 1
}
docker compose version >/dev/null 2>&1 || {
  echo "[ERROR] docker compose is unavailable." >&2
  exit 1
}

BASE_ARGS=(compose --project-directory "${REPO_ROOT}")
if [[ -f "${ENV_FILE}" ]]; then
  BASE_ARGS+=(--env-file "${ENV_FILE}")
fi
BASE_ARGS+=(-f "${COMPOSE_FILE}")
if [[ "${EDGE}" == "1" ]]; then
  BASE_ARGS+=(-f "${EDGE_COMPOSE_FILE}")
fi
if [[ "${OBSERVABILITY}" == "1" ]]; then
  BASE_ARGS+=(-f "${OBSERVABILITY_COMPOSE_FILE}")
fi
for profile in "${PROFILES[@]}"; do
  BASE_ARGS+=(--profile "${profile}")
done

if [[ "${VALIDATE_ONLY}" == "1" ]]; then
  docker "${BASE_ARGS[@]}" config -q
  echo "[INFO] Validation succeeded."
  echo "[INFO] panel-web replicas: ${WEB_REPLICAS}"
  echo "[INFO] ops-worker replicas: ${WORKER_REPLICAS}"
  echo "[INFO] Edge enabled: ${EDGE}"
  echo "[INFO] Observability enabled: ${OBSERVABILITY}"
  exit 0
fi

ARGS=("${BASE_ARGS[@]}" up --remove-orphans --scale "panel-web=${WEB_REPLICAS}" --scale "ops-worker=${WORKER_REPLICAS}")
[[ "${BUILD}" == "1" ]] && ARGS+=(--build)
[[ "${DETACH}" == "1" ]] && ARGS+=(-d)

echo "[INFO] Starting Iguana docker production contour"
echo "[INFO] panel-web replicas: ${WEB_REPLICAS}"
echo "[INFO] ops-worker replicas: ${WORKER_REPLICAS}"
echo "[INFO] Edge enabled: ${EDGE}"
echo "[INFO] Observability enabled: ${OBSERVABILITY}"

docker "${ARGS[@]}"

echo "[INFO] Iguana docker production contour started."
