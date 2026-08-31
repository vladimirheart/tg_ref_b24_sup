#!/usr/bin/env bash
set -euo pipefail

PROJECT_NAME=""
OUTPUT_JSON=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --project-name)
      PROJECT_NAME="${2:-}"
      shift 2
      ;;
    --json)
      OUTPUT_JSON=1
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
ENV_FILE="${REPO_ROOT}/.env"

command -v docker >/dev/null 2>&1 || { echo "[ERROR] Docker is not installed or not available in PATH." >&2; exit 1; }
docker version >/dev/null 2>&1 || { echo "[ERROR] docker CLI is unavailable." >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "[ERROR] docker daemon is unavailable." >&2; exit 1; }

get_setting_value() {
  local name="$1"
  local value="${!name-}"
  if [[ -n "${value}" ]]; then
    printf '%s' "${value}"
    return
  fi
  if [[ -f "${ENV_FILE}" ]]; then
    local line
    line="$(grep -E "^${name}=" "${ENV_FILE}" | tail -n 1 || true)"
    if [[ -n "${line}" ]]; then
      printf '%s' "${line#*=}"
      return
    fi
  fi
  printf ''
}

is_disallowed_secret() {
  local value="${1:-}"
  shift
  if [[ -z "${value}" ]]; then
    return 0
  fi
  local candidate
  for candidate in "$@"; do
    if [[ "${value}" == "${candidate}" ]]; then
      return 0
    fi
  done
  return 1
}

resolve_project_name() {
  if [[ -n "${PROJECT_NAME}" ]]; then
    printf '%s' "${PROJECT_NAME}"
    return
  fi
  local from_env
  from_env="$(get_setting_value "COMPOSE_PROJECT_NAME")"
  if [[ -n "${from_env}" ]]; then
    printf '%s' "${from_env}"
    return
  fi
  basename "${REPO_ROOT}"
}

get_volume_name() {
  local project="$1"
  local logical="$2"
  local name
  name="$(docker volume ls --filter "label=com.docker.compose.project=${project}" --filter "label=com.docker.compose.volume=${logical}" --format '{{.Name}}' | head -n 1)"
  if [[ -n "${name}" ]]; then
    printf '%s' "${name}"
    return
  fi
  local fallback="${project}_${logical}"
  name="$(docker volume ls --filter "name=^${fallback}$" --format '{{.Name}}' | head -n 1)"
  if [[ -n "${name}" ]]; then
    printf '%s' "${name}"
    return
  fi
  printf ''
}

get_container_id() {
  local project="$1"
  local service="$2"
  docker ps -aq \
    --filter "label=com.docker.compose.project=${project}" \
    --filter "label=com.docker.compose.service=${service}" | head -n 1
}

get_container_status() {
  local container_id="$1"
  if [[ -z "${container_id}" ]]; then
    printf ''
    return
  fi
  docker inspect --format '{{.State.Status}}' "${container_id}"
}

get_container_env_value() {
  local container_id="$1"
  local name="$2"
  if [[ -z "${container_id}" ]]; then
    printf ''
    return
  fi
  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "${container_id}" \
    | grep -E "^${name}=" | tail -n 1 | sed "s/^${name}=//" || true
}

json_escape() {
  python3 - "$1" <<'PY'
import json, sys
print(json.dumps(sys.argv[1]))
PY
}

PROJECT="$(resolve_project_name)"
CHECKED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"

declare -a COMPONENT_NAMES=()
declare -a COMPONENT_STATUSES=()
declare -a COMPONENT_VERIFY=()
declare -a COMPONENT_REASONS=()
declare -a COMPONENT_ACTIONS=()

append_component() {
  COMPONENT_NAMES+=("$1")
  COMPONENT_STATUSES+=("$2")
  COMPONENT_VERIFY+=("$3")
  COMPONENT_REASONS+=("$4")
  COMPONENT_ACTIONS+=("$5")
}

postgres_volume="$(get_volume_name "${PROJECT}" "iguana-postgres-data")"
postgres_container="$(get_container_id "${PROJECT}" "postgres")"
postgres_status="$(get_container_status "${postgres_container}")"
postgres_password="$(get_setting_value "IGUANA_POSTGRES_PASSWORD")"
postgres_user="$(get_setting_value "IGUANA_POSTGRES_USER")"
postgres_db="$(get_setting_value "IGUANA_POSTGRES_DB")"
[[ -n "${postgres_user}" ]] || postgres_user="iguana"
[[ -n "${postgres_db}" ]] || postgres_db="iguana"
if [[ -z "${postgres_volume}" ]]; then
  append_component "postgresql" "fresh" "not_applicable" \
    "Compose-managed PostgreSQL volume was not found." \
    "Safe to initialize PostgreSQL with secure bootstrap-generated credentials."
elif is_disallowed_secret "${postgres_password}" "iguana"; then
  append_component "postgresql" "migration_required" "blocked_by_default_secret" \
    "A PostgreSQL volume already exists, but IGUANA_POSTGRES_PASSWORD is missing or still uses the documented default." \
    "Do not overwrite .env blindly. Prepare controlled PostgreSQL password rotation and verify the new login before updating runtime config."
elif [[ "${postgres_status}" != "running" ]]; then
  append_component "postgresql" "migration_required" "container_not_running" \
    "A PostgreSQL volume exists, but no running PostgreSQL container is available for live credential verification." \
    "Start the production contour, then rerun this status helper before rotating or trusting the configured password."
elif docker exec -e "PGPASSWORD=${postgres_password}" "${postgres_container}" psql -h 127.0.0.1 -U "${postgres_user}" -d "${postgres_db}" -Atqc "SELECT 1" >/tmp/iguana-pg-check.$$ 2>/dev/null && [[ "$(tr -d '\r\n' < /tmp/iguana-pg-check.$$)" == "1" ]]; then
  rm -f /tmp/iguana-pg-check.$$
  append_component "postgresql" "ready" "authenticated" \
    "Configured PostgreSQL password authenticated successfully against the live persisted database." \
    "You can keep the current password or plan an explicit rotation runbook with backup/rollback."
else
  rm -f /tmp/iguana-pg-check.$$ || true
  append_component "postgresql" "migration_required" "auth_failed" \
    "Configured PostgreSQL password did not authenticate against the live persisted database." \
    "Treat this as a credential drift. Stop and run a controlled PostgreSQL rotation/recovery path before changing .env further."
fi

rabbit_volume="$(get_volume_name "${PROJECT}" "iguana-rabbitmq-data")"
rabbit_container="$(get_container_id "${PROJECT}" "rabbitmq")"
rabbit_status="$(get_container_status "${rabbit_container}")"
rabbit_password="$(get_setting_value "IGUANA_RABBITMQ_PASSWORD")"
rabbit_user="$(get_setting_value "IGUANA_RABBITMQ_USER")"
[[ -n "${rabbit_user}" ]] || rabbit_user="iguana"
if [[ -z "${rabbit_volume}" ]]; then
  append_component "rabbitmq" "fresh" "not_applicable" \
    "Compose-managed RabbitMQ volume was not found." \
    "Safe to initialize RabbitMQ with secure bootstrap-generated credentials."
elif is_disallowed_secret "${rabbit_password}" "iguana"; then
  append_component "rabbitmq" "migration_required" "blocked_by_default_secret" \
    "A RabbitMQ volume already exists, but IGUANA_RABBITMQ_PASSWORD is missing or still uses the documented default." \
    "Plan a controlled RabbitMQ password sync so queues/definitions survive and runtime config stays aligned."
elif [[ "${rabbit_status}" != "running" ]]; then
  append_component "rabbitmq" "migration_required" "container_not_running" \
    "A RabbitMQ volume exists, but no running RabbitMQ container is available for live credential verification." \
    "Start the contour, then rerun this helper before rotating RabbitMQ users or trusting the configured password."
elif docker exec "${rabbit_container}" rabbitmqctl authenticate_user "${rabbit_user}" "${rabbit_password}" >/dev/null 2>&1; then
  append_component "rabbitmq" "ready" "authenticated" \
    "Configured RabbitMQ password authenticated successfully against the live broker." \
    "You can keep the current broker credential or rotate it later through a controlled queue-safe workflow."
else
  append_component "rabbitmq" "migration_required" "auth_failed" \
    "Configured RabbitMQ password did not authenticate against the live broker." \
    "Treat this as broker credential drift and execute a controlled RabbitMQ password synchronization before the next restart."
fi

redis_volume="$(get_volume_name "${PROJECT}" "iguana-redis-data")"
redis_container="$(get_container_id "${PROJECT}" "redis")"
redis_status="$(get_container_status "${redis_container}")"
redis_password="$(get_setting_value "IGUANA_REDIS_PASSWORD")"
if [[ -z "${redis_volume}" ]]; then
  append_component "redis" "fresh" "not_applicable" \
    "Compose-managed Redis volume was not found." \
    "Safe to initialize Redis with secure bootstrap-generated runtime password."
elif is_disallowed_secret "${redis_password}" "iguana-redis"; then
  append_component "redis" "migration_required" "blocked_by_default_secret" \
    "A Redis volume already exists, but IGUANA_REDIS_PASSWORD is missing or still uses the documented default." \
    "Prepare a coordinated Redis password switch for every client before changing runtime config."
elif [[ "${redis_status}" != "running" ]]; then
  append_component "redis" "migration_required" "container_not_running" \
    "A Redis volume exists, but no running Redis container is available for live credential verification." \
    "Start the contour, rerun this helper, then coordinate a password switch across panel, bots and observability clients."
elif docker exec "${redis_container}" redis-cli -a "${redis_password}" ping 2>/dev/null | grep -q "PONG"; then
  append_component "redis" "ready" "authenticated" \
    "Configured Redis password authenticated successfully against the live runtime." \
    "Keep the current password or plan a coordinated client switch with explicit restart sequencing."
else
  append_component "redis" "migration_required" "auth_failed" \
    "Configured Redis password did not authenticate against the live runtime." \
    "Treat this as runtime drift and fix the coordinated Redis password contract before restarting dependent services."
fi

minio_volume="$(get_volume_name "${PROJECT}" "iguana-minio-data")"
minio_container="$(get_container_id "${PROJECT}" "minio")"
minio_status="$(get_container_status "${minio_container}")"
minio_access="$(get_setting_value "APP_STORAGE_OBJECT_ACCESS_KEY")"
minio_secret="$(get_setting_value "APP_STORAGE_OBJECT_SECRET_KEY")"
minio_runtime_access="$(get_container_env_value "${minio_container}" "MINIO_ROOT_USER")"
minio_runtime_secret="$(get_container_env_value "${minio_container}" "MINIO_ROOT_PASSWORD")"
if [[ -z "${minio_volume}" ]]; then
  append_component "minio" "fresh" "not_applicable" \
    "Compose-managed MinIO volume was not found." \
    "Safe to initialize MinIO with secure bootstrap-generated object-storage credentials."
elif is_disallowed_secret "${minio_access}" "iguana-minio" || is_disallowed_secret "${minio_secret}" "iguana-minio-secret"; then
  append_component "minio" "migration_required" "blocked_by_default_secret" \
    "A MinIO volume already exists, but object-storage credentials are missing or still use the documented defaults." \
    "Replace default MinIO credentials through a controlled restart plan only after confirming how clients will switch."
elif [[ "${minio_status}" != "running" ]]; then
  append_component "minio" "migration_required" "container_not_running" \
    "A MinIO volume exists, but no running MinIO container is available to confirm the live runtime credential contract." \
    "Start the contour and rerun this helper before treating MinIO credentials as aligned with the persisted bucket/object set."
elif [[ "${minio_access}" == "${minio_runtime_access}" && "${minio_secret}" == "${minio_runtime_secret}" ]]; then
  append_component "minio" "ready" "matched" \
    "Configured MinIO credentials match the live container runtime environment for the existing object-storage volume." \
    "Keep the current credentials or plan an explicit client-coordinated secret rotation."
else
  append_component "minio" "migration_required" "mismatched" \
    "Configured MinIO credentials do not match the live container runtime environment for the existing object-storage volume." \
    "Treat this as object-storage credential drift and reconcile runtime config before the next restart or cutover."
fi

grafana_volume="$(get_volume_name "${PROJECT}" "iguana-grafana-data")"
grafana_container="$(get_container_id "${PROJECT}" "grafana")"
grafana_status="$(get_container_status "${grafana_container}")"
grafana_user="$(get_setting_value "IGUANA_GRAFANA_ADMIN_USER")"
grafana_password="$(get_setting_value "IGUANA_GRAFANA_ADMIN_PASSWORD")"
grafana_host="$(get_setting_value "IGUANA_GRAFANA_BIND_HOST")"
grafana_port="$(get_setting_value "IGUANA_GRAFANA_PORT")"
[[ -n "${grafana_user}" ]] || grafana_user="admin"
[[ -n "${grafana_host}" ]] || grafana_host="127.0.0.1"
[[ -n "${grafana_port}" ]] || grafana_port="3000"
if [[ -z "${grafana_volume}" ]]; then
  append_component "grafana" "fresh" "not_applicable" \
    "Compose-managed Grafana volume was not found." \
    "If observability is enabled later, initialize Grafana with a non-default admin password."
elif is_disallowed_secret "${grafana_password}" "change-me" "admin" "grafana"; then
  append_component "grafana" "migration_required" "blocked_by_default_secret" \
    "A Grafana volume already exists, but IGUANA_GRAFANA_ADMIN_PASSWORD is missing or still uses the documented default placeholder." \
    "Prepare an explicit Grafana admin reset workflow against the persisted DB before changing runtime config."
elif [[ "${grafana_status}" != "running" ]]; then
  append_component "grafana" "migration_required" "container_not_running" \
    "A Grafana volume exists, but no running Grafana container is available for live admin authentication verification." \
    "Start observability and rerun this helper before rotating or trusting the configured admin password."
elif command -v curl >/dev/null 2>&1 && curl -fsS -u "${grafana_user}:${grafana_password}" "http://${grafana_host}:${grafana_port}/api/user" >/dev/null 2>&1; then
  append_component "grafana" "ready" "authenticated" \
    "Configured Grafana admin password authenticated successfully against the live API." \
    "Keep the current admin password or rotate it later through a controlled observability runbook."
else
  append_component "grafana" "migration_required" "auth_failed" \
    "Configured Grafana admin password did not authenticate against the live API for the existing persisted DB." \
    "Treat this as Grafana admin drift and execute a controlled admin reset/rotation path before the next restart."
fi

fresh_count=0
ready_count=0
migration_count=0
for status in "${COMPONENT_STATUSES[@]}"; do
  case "${status}" in
    fresh) fresh_count=$((fresh_count + 1)) ;;
    ready) ready_count=$((ready_count + 1)) ;;
    migration_required) migration_count=$((migration_count + 1)) ;;
  esac
done

overall_status="mixed"
if (( migration_count > 0 )); then
  overall_status="migration_required"
elif (( fresh_count == ${#COMPONENT_STATUSES[@]} )); then
  overall_status="fresh"
elif (( ready_count == ${#COMPONENT_STATUSES[@]} )); then
  overall_status="ready"
fi

if (( OUTPUT_JSON == 1 )); then
  printf '{\n'
  printf '  "checked_at": %s,\n' "$(json_escape "${CHECKED_AT}")"
  printf '  "project_name": %s,\n' "$(json_escape "${PROJECT}")"
  printf '  "env_file": %s,\n' "$(json_escape "${ENV_FILE}")"
  printf '  "overall_status": %s,\n' "$(json_escape "${overall_status}")"
  printf '  "summary": {"total": %d, "fresh": %d, "ready": %d, "migration_required": %d},\n' "${#COMPONENT_NAMES[@]}" "${fresh_count}" "${ready_count}" "${migration_count}"
  printf '  "components": [\n'
  for i in "${!COMPONENT_NAMES[@]}"; do
    printf '    {"component": %s, "status": %s, "verification_status": %s, "reason": %s, "next_action": %s}' \
      "$(json_escape "${COMPONENT_NAMES[$i]}")" \
      "$(json_escape "${COMPONENT_STATUSES[$i]}")" \
      "$(json_escape "${COMPONENT_VERIFY[$i]}")" \
      "$(json_escape "${COMPONENT_REASONS[$i]}")" \
      "$(json_escape "${COMPONENT_ACTIONS[$i]}")"
    if (( i + 1 < ${#COMPONENT_NAMES[@]} )); then
      printf ','
    fi
    printf '\n'
  done
  printf '  ]\n'
  printf '}\n'
  exit 0
fi

printf '[INFO] Project: %s\n' "${PROJECT}"
printf '[INFO] Overall status: %s\n' "${overall_status}"
printf '[INFO] Summary: total=%d, fresh=%d, ready=%d, migration_required=%d\n\n' "${#COMPONENT_NAMES[@]}" "${fresh_count}" "${ready_count}" "${migration_count}"

for i in "${!COMPONENT_NAMES[@]}"; do
  printf '[%s] %s\n' "${COMPONENT_STATUSES[$i]}" "${COMPONENT_NAMES[$i]}"
  printf '  verification: %s\n' "${COMPONENT_VERIFY[$i]}"
  printf '  reason: %s\n' "${COMPONENT_REASONS[$i]}"
  printf '  next: %s\n\n' "${COMPONENT_ACTIONS[$i]}"
done
