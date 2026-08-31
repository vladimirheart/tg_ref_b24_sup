#!/usr/bin/env bash
set -uo pipefail

PROJECT_NAME=""
COMPONENT=""
COMPONENT_ARGS=()
TARGET_PASSWORD=""
TARGET_ACCESS_KEY=""
TARGET_SECRET_KEY=""
APPLY=0
REHEARSAL=0
BACKUP_DIR=""
HEALTH_TIMEOUT_SECONDS=180

while [[ $# -gt 0 ]]; do
  case "$1" in
    --component)
      COMPONENT="${2:-}"
      shift 2
      ;;
    --components)
      COMPONENT_ARGS+=("${2:-}")
      shift 2
      ;;
    --project-name)
      PROJECT_NAME="${2:-}"
      shift 2
      ;;
    --target-password)
      TARGET_PASSWORD="${2:-}"
      shift 2
      ;;
    --target-access-key)
      TARGET_ACCESS_KEY="${2:-}"
      shift 2
      ;;
    --target-secret-key)
      TARGET_SECRET_KEY="${2:-}"
      shift 2
      ;;
    --apply)
      APPLY=1
      shift
      ;;
    --rehearsal)
      REHEARSAL=1
      shift
      ;;
    --backup-dir)
      BACKUP_DIR="${2:-}"
      shift 2
      ;;
    --health-timeout-seconds)
      HEALTH_TIMEOUT_SECONDS="${2:-}"
      shift 2
      ;;
    *)
      echo "[ERROR] Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_PATH="${SCRIPT_DIR}/$(basename "${BASH_SOURCE[0]}")"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${REPO_ROOT}/.env"
SUPPORTED_COMPONENTS=(postgresql rabbitmq redis minio grafana)

die() {
  echo "[ERROR] $*" >&2
  exit 1
}

warn() {
  echo "[WARN] $*" >&2
}

command -v docker >/dev/null 2>&1 || die "Docker is not installed or not available in PATH."
docker compose version >/dev/null 2>&1 || die "docker compose is unavailable."
docker info >/dev/null 2>&1 || die "docker daemon is unavailable."

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
      printf '%s' "${line#*=}" | tr -d '\r'
      return
    fi
  fi
  printf ''
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

new_random_hex_token() {
  local bytes_length="${1:-32}"
  od -An -tx1 -N "${bytes_length}" /dev/urandom | tr -d ' \n'
}

copy_file_exact() {
  local source_path="$1"
  local target_path="$2"
  cp "${source_path}" "${target_path}"
}

ensure_env_file() {
  if [[ ! -f "${ENV_FILE}" ]]; then
    : > "${ENV_FILE}"
  fi
}

update_or_add_env_setting() {
  local name="$1"
  local value="$2"
  local file_path="$3"
  local temp_file
  temp_file="$(mktemp "${file_path}.tmp.XXXXXX")" || return 1
  awk -v key="${name}" -v val="${value}" '
    BEGIN {
      replaced = 0
      last_nonempty = 0
    }
    {
      if (length($0) > 0) {
        last_nonempty = 1
      } else {
        last_nonempty = 0
      }
      if (!replaced && $0 ~ ("^" key "=")) {
        print key "=" val
        replaced = 1
        next
      }
      print
    }
    END {
      if (!replaced) {
        if (NR > 0 && last_nonempty) {
          print ""
        }
        print key "=" val
      }
    }
  ' "${file_path}" > "${temp_file}" && mv "${temp_file}" "${file_path}"
}

array_contains() {
  local needle="$1"
  shift
  local item
  for item in "$@"; do
    if [[ "${item}" == "${needle}" ]]; then
      return 0
    fi
  done
  return 1
}

resolve_selected_components() {
  if [[ -n "${COMPONENT}" && ${#COMPONENT_ARGS[@]} -gt 0 ]]; then
    die "Use either --component or --components, but not both."
  fi

  local requested=()
  local entry
  local token
  if [[ -n "${COMPONENT}" ]]; then
    requested+=("$(printf '%s' "${COMPONENT}" | tr '[:upper:]' '[:lower:]')")
  fi

  for entry in "${COMPONENT_ARGS[@]}"; do
    [[ -n "${entry}" ]] || continue
    IFS=',' read -r -a split_tokens <<< "${entry}"
    for token in "${split_tokens[@]}"; do
      token="$(printf '%s' "${token}" | tr '[:upper:]' '[:lower:]' | xargs)"
      [[ -n "${token}" ]] || continue
      requested+=("${token}")
    done
  done

  if [[ ${#requested[@]} -eq 0 ]]; then
    die "Specify --component <name>|all or --components <name1,name2,...>."
  fi

  if [[ ${#requested[@]} -gt 1 ]]; then
    local requested_item
    for requested_item in "${requested[@]}"; do
      [[ "${requested_item}" != "all" ]] || die "Value 'all' cannot be combined with other components."
    done
  fi

  if [[ ${#requested[@]} -eq 1 && "${requested[0]}" == "all" ]]; then
    SELECTED_COMPONENTS=("${SUPPORTED_COMPONENTS[@]}")
    return
  fi

  local unique_requested=()
  local candidate
  local supported
  local is_supported
  for candidate in "${requested[@]}"; do
    is_supported=0
    for supported in "${SUPPORTED_COMPONENTS[@]}"; do
      if [[ "${candidate}" == "${supported}" ]]; then
        is_supported=1
        break
      fi
    done
    (( is_supported == 1 )) || die "Unsupported component '${candidate}'. Supported values: $(join_by_comma "${SUPPORTED_COMPONENTS[@]}")."
    array_contains "${candidate}" "${unique_requested[@]}" || unique_requested+=("${candidate}")
  done

  SELECTED_COMPONENTS=()
  for candidate in "${SUPPORTED_COMPONENTS[@]}"; do
    array_contains "${candidate}" "${unique_requested[@]}" && SELECTED_COMPONENTS+=("${candidate}")
  done
}

join_by_comma() {
  local first=1
  local item
  for item in "$@"; do
    if (( first == 0 )); then
      printf ', '
    fi
    printf '%s' "${item}"
    first=0
  done
}

get_container_id() {
  local project="$1"
  local service="$2"
  docker ps -aq \
    --filter "label=com.docker.compose.project=${project}" \
    --filter "label=com.docker.compose.service=${service}" | head -n 1 | tr -d '\r'
}

get_container_status() {
  local container_id="$1"
  if [[ -z "${container_id}" ]]; then
    printf ''
    return
  fi
  docker inspect --format '{{.State.Status}}' "${container_id}" 2>/dev/null | tr -d '\r' || true
}

get_container_health_status() {
  local container_id="$1"
  if [[ -z "${container_id}" ]]; then
    printf ''
    return
  fi
  docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' "${container_id}" 2>/dev/null | tr -d '\r' || true
}

get_container_exit_code() {
  local container_id="$1"
  if [[ -z "${container_id}" ]]; then
    printf ''
    return
  fi
  docker inspect --format '{{.State.ExitCode}}' "${container_id}" 2>/dev/null | tr -d '\r' || true
}

get_container_env_value() {
  local container_id="$1"
  local name="$2"
  if [[ -z "${container_id}" ]]; then
    printf ''
    return
  fi
  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "${container_id}" \
    | tr -d '\r' | grep -E "^${name}=" | tail -n 1 | sed "s/^${name}=//" || true
}

wait_for_service_ready() {
  local project="$1"
  local service="$2"
  local timeout_seconds="$3"
  local deadline
  deadline=$(( $(date +%s) + timeout_seconds ))

  while (( $(date +%s) < deadline )); do
    local container_id
    container_id="$(get_container_id "${project}" "${service}")"
    if [[ -n "${container_id}" ]]; then
      local status
      local health
      status="$(get_container_status "${container_id}")"
      health="$(get_container_health_status "${container_id}")"
      if [[ "${status}" == "running" ]] && [[ -z "${health}" || "${health}" == "healthy" ]]; then
        return 0
      fi
    fi
    sleep 3
  done

  return 1
}

wait_for_service_completion_success() {
  local project="$1"
  local service="$2"
  local timeout_seconds="$3"
  local deadline
  deadline=$(( $(date +%s) + timeout_seconds ))

  while (( $(date +%s) < deadline )); do
    local container_id
    container_id="$(get_container_id "${project}" "${service}")"
    if [[ -n "${container_id}" ]]; then
      local status
      local exit_code
      status="$(get_container_status "${container_id}")"
      exit_code="$(get_container_exit_code "${container_id}")"
      if [[ "${status}" == "exited" && "${exit_code}" == "0" ]]; then
        return 0
      fi
      if [[ "${status}" == "dead" ]]; then
        return 1
      fi
    fi
    sleep 3
  done

  return 1
}

test_postgres_credential() {
  local container_id="$1"
  local user_name="$2"
  local database_name="$3"
  local password="$4"
  local output_file
  output_file="$(mktemp)"
  if docker exec -e "PGPASSWORD=${password}" "${container_id}" \
    psql -h 127.0.0.1 -U "${user_name}" -d "${database_name}" -Atqc "SELECT 1" >"${output_file}" 2>/dev/null; then
    if [[ "$(tr -d '\r\n' < "${output_file}")" == "1" ]]; then
      rm -f "${output_file}"
      return 0
    fi
  fi
  rm -f "${output_file}"
  return 1
}

test_rabbitmq_credential() {
  local container_id="$1"
  local user_name="$2"
  local password="$3"
  docker exec "${container_id}" rabbitmqctl authenticate_user "${user_name}" "${password}" >/dev/null 2>&1
}

test_redis_credential() {
  local container_id="$1"
  local password="$2"
  docker exec "${container_id}" redis-cli --no-auth-warning -a "${password}" ping 2>/dev/null | grep -q "PONG"
}

test_grafana_credential() {
  local bind_host="$1"
  local port="$2"
  local user_name="$3"
  local password="$4"
  command -v curl >/dev/null 2>&1 || return 1
  curl -fsS -u "${user_name}:${password}" "http://${bind_host}:${port}/api/user" >/dev/null 2>&1
}

resolve_current_postgres_password() {
  local container_id="$1"
  local user_name="$2"
  local database_name="$3"
  local candidate
  for candidate in \
    "$(get_setting_value "IGUANA_POSTGRES_PASSWORD")" \
    "$(get_setting_value "SPRING_DATASOURCE_PASSWORD")" \
    "iguana"; do
    if [[ -n "${candidate}" ]] && test_postgres_credential "${container_id}" "${user_name}" "${database_name}" "${candidate}"; then
      printf '%s' "${candidate}"
      return 0
    fi
  done
  return 1
}

resolve_current_rabbitmq_password() {
  local container_id="$1"
  local user_name="$2"
  local candidate
  for candidate in \
    "$(get_setting_value "IGUANA_RABBITMQ_PASSWORD")" \
    "$(get_setting_value "SPRING_RABBITMQ_PASSWORD")" \
    "iguana"; do
    if [[ -n "${candidate}" ]] && test_rabbitmq_credential "${container_id}" "${user_name}" "${candidate}"; then
      printf '%s' "${candidate}"
      return 0
    fi
  done
  return 1
}

resolve_current_redis_password() {
  local container_id="$1"
  local candidate
  for candidate in \
    "$(get_setting_value "IGUANA_REDIS_PASSWORD")" \
    "$(get_setting_value "SPRING_DATA_REDIS_PASSWORD")" \
    "iguana-redis"; do
    if [[ -n "${candidate}" ]] && test_redis_credential "${container_id}" "${candidate}"; then
      printf '%s' "${candidate}"
      return 0
    fi
  done
  return 1
}

resolve_current_grafana_password() {
  local bind_host="$1"
  local port="$2"
  local user_name="$3"
  local candidate
  for candidate in \
    "$(get_setting_value "IGUANA_GRAFANA_ADMIN_PASSWORD")" \
    "change-me" \
    "admin" \
    "grafana"; do
    if [[ -n "${candidate}" ]] && test_grafana_credential "${bind_host}" "${port}" "${user_name}" "${candidate}"; then
      printf '%s' "${candidate}"
      return 0
    fi
  done
  return 1
}

update_postgres_password() {
  local container_id="$1"
  local user_name="$2"
  local database_name="$3"
  local current_password="$4"
  local new_password="$5"
  local safe_user="${user_name//\"/\"\"}"
  local safe_password="${new_password//\'/\'\'}"
  docker exec -e "PGPASSWORD=${current_password}" "${container_id}" \
    psql -h 127.0.0.1 -U "${user_name}" -d "${database_name}" -v ON_ERROR_STOP=1 \
    -c "ALTER USER \"${safe_user}\" WITH PASSWORD '${safe_password}';" >/dev/null
}

update_rabbitmq_password() {
  local container_id="$1"
  local user_name="$2"
  local new_password="$3"
  docker exec "${container_id}" rabbitmqctl change_password "${user_name}" "${new_password}" >/dev/null
}

update_redis_password() {
  local container_id="$1"
  local current_password="$2"
  local new_password="$3"
  docker exec "${container_id}" redis-cli --no-auth-warning -a "${current_password}" CONFIG SET requirepass "${new_password}" >/dev/null
}

update_grafana_password() {
  local container_id="$1"
  local new_password="$2"
  docker exec "${container_id}" grafana cli --homepath /usr/share/grafana --config /etc/grafana/grafana.ini admin reset-admin-password "${new_password}" >/dev/null
}

get_compose_service_name_from_container() {
  local container_name_or_id="$1"
  docker inspect --format '{{ index .Config.Labels "com.docker.compose.service" }}' "${container_name_or_id}" 2>/dev/null | tr -d '\r' || true
}

RUNNING_SERVICES=()

load_running_services() {
  RUNNING_SERVICES=()
  local container_name
  while IFS= read -r container_name; do
    [[ -n "${container_name}" ]] || continue
    local service_name
    service_name="$(get_compose_service_name_from_container "${container_name}")"
    if [[ -n "${service_name}" ]] && ! array_contains "${service_name}" "${RUNNING_SERVICES[@]}"; then
      RUNNING_SERVICES+=("${service_name}")
    fi
  done < <(docker ps --filter "label=com.docker.compose.project=${PROJECT}" --format '{{.Names}}' | tr -d '\r')
}

service_is_running() {
  local service_name="$1"
  array_contains "${service_name}" "${RUNNING_SERVICES[@]}"
}

COMPOSE_FILES=()
RESTART_SERVICES=()

build_compose_files() {
  COMPOSE_FILES=("${REPO_ROOT}/docker-compose.production-contour.yml")
  local candidate
  for candidate in postgres-exporter redis-exporter alertmanager prometheus loki alloy grafana; do
    if service_is_running "${candidate}"; then
      COMPOSE_FILES+=("${REPO_ROOT}/docker-compose.production-observability.yml")
      break
    fi
  done
}

invoke_compose_recreate() {
  if (( ${#RESTART_SERVICES[@]} == 0 )); then
    return 0
  fi
  local -a args=(compose)
  local compose_file
  for compose_file in "${COMPOSE_FILES[@]}"; do
    args+=(-f "${compose_file}")
  done
  args+=(-p "${PROJECT}" up -d --force-recreate)
  local service_name
  for service_name in "${RESTART_SERVICES[@]}"; do
    args+=("${service_name}")
  done
  docker "${args[@]}" >/dev/null
}

get_compose_network_name() {
  local name
  name="$(docker network ls \
    --filter "label=com.docker.compose.project=${PROJECT}" \
    --filter "label=com.docker.compose.network=default" \
    --format '{{.Name}}' | head -n 1 | tr -d '\r')"
  if [[ -n "${name}" ]]; then
    printf '%s' "${name}"
    return
  fi
  printf '%s_default' "${PROJECT}"
}

test_minio_bucket_access() {
  local network_name="$1"
  local access_key="$2"
  local secret_key="$3"
  local bucket_name="$4"
  MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*' docker run --rm \
    --network "${network_name}" \
    -e "MINIO_ACCESS_KEY=${access_key}" \
    -e "MINIO_SECRET_KEY=${secret_key}" \
    -e "MINIO_BUCKET=${bucket_name}" \
    --entrypoint /bin/sh \
    minio/mc:RELEASE.2025-07-21T05-28-08Z \
    -c 'mc alias set local http://minio:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null && mc ls "local/$MINIO_BUCKET" >/dev/null' >/dev/null 2>&1
}

create_preapply_snapshot() {
  local project="$1"
  shift
  local selected_components=("$@")
  [[ -n "${BACKUP_DIR}" ]] || return 0

  local target_root="${BACKUP_DIR}"
  case "${target_root}" in
    /*|[A-Za-z]:/*) ;;
    *) target_root="${REPO_ROOT}/${target_root}" ;;
  esac

  local snapshot_dir="${target_root}/credential-rotation-$(date +%Y%m%d-%H%M%S)"
  mkdir -p "${snapshot_dir}" || die "Unable to create backup directory '${snapshot_dir}'."

  if [[ -f "${ENV_FILE}" ]]; then
    copy_file_exact "${ENV_FILE}" "${snapshot_dir}/env.before" || die "Unable to create .env snapshot in '${snapshot_dir}'."
  fi

  printf '%s\n' "${selected_components[@]}" > "${snapshot_dir}/component-order.txt"
  docker ps \
    --filter "label=com.docker.compose.project=${project}" \
    --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}' > "${snapshot_dir}/docker-ps.txt" 2>&1 || true

  echo "[INFO] Pre-apply snapshot created at: ${snapshot_dir}"
}

run_orchestration() {
  local mode="dry-run"
  (( APPLY == 1 )) && mode="apply"
  (( REHEARSAL == 1 )) && mode="rehearsal"

  if (( APPLY == 1 && REHEARSAL == 1 )); then
    die "--apply and --rehearsal cannot be used together."
  fi

  if (( ${#SELECTED_COMPONENTS[@]} > 1 )) && [[ -n "${TARGET_PASSWORD}${TARGET_ACCESS_KEY}${TARGET_SECRET_KEY}" ]]; then
    die "Explicit target credential arguments are supported only for single-component runs."
  fi

  echo "[INFO] Credential rotation orchestration mode: ${mode}"
  echo "[INFO] Ordered component flow: $(join_by_comma "${SELECTED_COMPONENTS[@]}")"

  if (( APPLY == 1 )); then
    create_preapply_snapshot "${PROJECT}" "${SELECTED_COMPONENTS[@]}"
  elif [[ -n "${BACKUP_DIR}" ]]; then
    warn "BACKUP_DIR is used only together with --apply. Snapshot creation skipped."
  fi

  local total="${#SELECTED_COMPONENTS[@]}"
  local index=0
  local component
  for component in "${SELECTED_COMPONENTS[@]}"; do
    index=$((index + 1))
    echo "[INFO] Starting step ${index}/${total}: ${component}"
    local child_args=(--component "${component}" --project-name "${PROJECT_NAME}" --health-timeout-seconds "${HEALTH_TIMEOUT_SECONDS}")
    [[ -n "${TARGET_PASSWORD}" ]] && child_args+=(--target-password "${TARGET_PASSWORD}")
    [[ -n "${TARGET_ACCESS_KEY}" ]] && child_args+=(--target-access-key "${TARGET_ACCESS_KEY}")
    [[ -n "${TARGET_SECRET_KEY}" ]] && child_args+=(--target-secret-key "${TARGET_SECRET_KEY}")
    (( APPLY == 1 )) && child_args+=(--apply)
    bash "${SCRIPT_PATH}" "${child_args[@]}"
    echo "[INFO] Completed step ${index}/${total}: ${component}"
  done

  if (( APPLY == 1 )); then
    echo "[INFO] Bulk credential rotation apply completed successfully."
  elif (( REHEARSAL == 1 )); then
    echo "[INFO] Bulk credential rotation rehearsal completed successfully."
  else
    echo "[INFO] Bulk credential rotation dry-run completed successfully."
  fi
}

PROJECT="$(resolve_project_name)"
SELECTED_COMPONENTS=()
resolve_selected_components
if (( APPLY == 1 && REHEARSAL == 1 )); then
  die "--apply and --rehearsal cannot be used together."
fi
USE_ORCHESTRATION=0
(( REHEARSAL == 1 )) && USE_ORCHESTRATION=1
(( ${#SELECTED_COMPONENTS[@]} > 1 )) && USE_ORCHESTRATION=1
(( ${#COMPONENT_ARGS[@]} > 0 )) && USE_ORCHESTRATION=1
[[ "${COMPONENT}" == "all" ]] && USE_ORCHESTRATION=1
[[ -n "${BACKUP_DIR}" ]] && USE_ORCHESTRATION=1
if (( USE_ORCHESTRATION == 1 )); then
  PROJECT="$(resolve_project_name)"
  run_orchestration
  exit 0
fi
COMPONENT="${SELECTED_COMPONENTS[0]}"
load_running_services

run_postgresql() {
  local service_name="postgres"
  local container_id
  container_id="$(get_container_id "${PROJECT}" "${service_name}")"
  [[ -n "${container_id}" ]] || die "PostgreSQL container is not running for compose project '${PROJECT}'."
  [[ "$(get_container_status "${container_id}")" == "running" ]] || die "PostgreSQL container is not running for compose project '${PROJECT}'."

  local user_name
  local database_name
  user_name="$(get_setting_value "IGUANA_POSTGRES_USER")"
  database_name="$(get_setting_value "IGUANA_POSTGRES_DB")"
  [[ -n "${user_name}" ]] || user_name="iguana"
  [[ -n "${database_name}" ]] || database_name="iguana"

  local current_password
  current_password="$(resolve_current_postgres_password "${container_id}" "${user_name}" "${database_name}")" || die "Unable to authenticate to live PostgreSQL with configured or documented fallback credentials."

  local new_password="${TARGET_PASSWORD}"
  [[ -n "${new_password}" ]] || new_password="$(new_random_hex_token 32)"
  [[ "${new_password}" != "${current_password}" ]] || die "Target PostgreSQL password must differ from the current live password."

  RESTART_SERVICES=()
  service_is_running "ops-worker" && RESTART_SERVICES+=("ops-worker")
  service_is_running "panel-web" && RESTART_SERVICES+=("panel-web")
  service_is_running "postgres-exporter" && RESTART_SERVICES+=("postgres-exporter")
  build_compose_files

  local backup_path="${REPO_ROOT}/.env.credential-migration-postgresql-$(date +%Y%m%d-%H%M%S).bak"
  if (( APPLY == 0 )); then
    echo "[INFO] Dry-run: PostgreSQL credential migration plan is ready."
    echo "[INFO] Compose project: ${PROJECT}"
    echo "[INFO] Live PostgreSQL authentication succeeded with the current credential candidate."
    echo "[INFO] Planned updates: IGUANA_POSTGRES_PASSWORD and SPRING_DATASOURCE_PASSWORD in repository .env."
    echo "[INFO] Planned dependent service recreate: $(join_by_comma "${RESTART_SERVICES[@]}")"
    echo "[INFO] Rollback checkpoint file would be created at: ${backup_path}"
    return 0
  fi

  local live_changed=0
  local env_updated=0
  local restart_attempted=0

  rollback_postgresql() {
    if (( live_changed == 1 )); then
      local current_container
      current_container="$(get_container_id "${PROJECT}" "${service_name}")"
      if [[ -n "${current_container}" ]] && [[ "$(get_container_status "${current_container}")" == "running" ]]; then
        if ! update_postgres_password "${current_container}" "${user_name}" "${database_name}" "${new_password}" "${current_password}"; then
          warn "Best-effort PostgreSQL rollback failed. Manual intervention may be required."
        fi
      fi
    fi
    if (( env_updated == 1 )) && [[ -f "${backup_path}" ]]; then
      copy_file_exact "${backup_path}" "${ENV_FILE}" || true
    fi
    if (( restart_attempted == 1 )); then
      if ! invoke_compose_recreate; then
        warn "Best-effort dependent service rollback recreate failed. Manual restart may be required."
      fi
    fi
  }

  [[ ! -f "${ENV_FILE}" ]] || copy_file_exact "${ENV_FILE}" "${backup_path}" || die "Unable to create rollback checkpoint for .env."
  update_postgres_password "${container_id}" "${user_name}" "${database_name}" "${current_password}" "${new_password}" || die "Failed to change PostgreSQL password in the live container."
  live_changed=1

  if ! test_postgres_credential "${container_id}" "${user_name}" "${database_name}" "${new_password}"; then
    rollback_postgresql
    die "PostgreSQL accepted the password change command, but live verification with the new credential failed."
  fi

  ensure_env_file
  update_or_add_env_setting "IGUANA_POSTGRES_PASSWORD" "${new_password}" "${ENV_FILE}" || { rollback_postgresql; die "Failed to update IGUANA_POSTGRES_PASSWORD in repository .env."; }
  local spring_url
  spring_url="$(get_setting_value "SPRING_DATASOURCE_URL")"
  if [[ -z "${spring_url}" || "${spring_url}" =~ ^jdbc:postgresql://(localhost|127\.0\.0\.1): ]]; then
    update_or_add_env_setting "SPRING_DATASOURCE_PASSWORD" "${new_password}" "${ENV_FILE}" || { rollback_postgresql; die "Failed to update SPRING_DATASOURCE_PASSWORD in repository .env."; }
  fi
  env_updated=1

  invoke_compose_recreate || { rollback_postgresql; die "Failed to recreate dependent services after PostgreSQL rotation."; }
  restart_attempted=1

  local service
  for service in "${RESTART_SERVICES[@]}"; do
    wait_for_service_ready "${PROJECT}" "${service}" "${HEALTH_TIMEOUT_SECONDS}" || { rollback_postgresql; die "Service '${service}' did not become ready after PostgreSQL rotation."; }
  done

  local postgres_after_restart
  postgres_after_restart="$(get_container_id "${PROJECT}" "${service_name}")"
  [[ -n "${postgres_after_restart}" ]] && [[ "$(get_container_status "${postgres_after_restart}")" == "running" ]] || { rollback_postgresql; die "PostgreSQL container is not running after dependent service recreation."; }
  test_postgres_credential "${postgres_after_restart}" "${user_name}" "${database_name}" "${new_password}" || { rollback_postgresql; die "PostgreSQL auth verification failed after dependent service recreation."; }

  echo "[INFO] PostgreSQL credential rotation applied successfully."
  echo "[INFO] Updated repository .env and recreated dependent services: $(join_by_comma "${RESTART_SERVICES[@]}")"
  echo "[INFO] Rollback checkpoint: ${backup_path}"
}

run_rabbitmq() {
  local service_name="rabbitmq"
  local container_id
  container_id="$(get_container_id "${PROJECT}" "${service_name}")"
  [[ -n "${container_id}" ]] || die "RabbitMQ container is not running for compose project '${PROJECT}'."
  [[ "$(get_container_status "${container_id}")" == "running" ]] || die "RabbitMQ container is not running for compose project '${PROJECT}'."

  local user_name
  user_name="$(get_setting_value "IGUANA_RABBITMQ_USER")"
  [[ -n "${user_name}" ]] || user_name="iguana"

  local current_password
  current_password="$(resolve_current_rabbitmq_password "${container_id}" "${user_name}")" || die "Unable to authenticate to live RabbitMQ with configured or documented fallback credentials."

  local new_password="${TARGET_PASSWORD}"
  [[ -n "${new_password}" ]] || new_password="$(new_random_hex_token 32)"
  [[ "${new_password}" != "${current_password}" ]] || die "Target RabbitMQ password must differ from the current live password."

  RESTART_SERVICES=()
  service_is_running "ops-worker" && RESTART_SERVICES+=("ops-worker")
  service_is_running "panel-web" && RESTART_SERVICES+=("panel-web")
  service_is_running "bot-telegram" && RESTART_SERVICES+=("bot-telegram")
  service_is_running "bot-vk" && RESTART_SERVICES+=("bot-vk")
  service_is_running "bot-max" && RESTART_SERVICES+=("bot-max")
  build_compose_files

  local backup_path="${REPO_ROOT}/.env.credential-migration-rabbitmq-$(date +%Y%m%d-%H%M%S).bak"
  if (( APPLY == 0 )); then
    echo "[INFO] Dry-run: RabbitMQ credential migration plan is ready."
    echo "[INFO] Compose project: ${PROJECT}"
    echo "[INFO] Live RabbitMQ authentication succeeded with the current credential candidate."
    echo "[INFO] Planned updates: IGUANA_RABBITMQ_PASSWORD and SPRING_RABBITMQ_PASSWORD in repository .env."
    echo "[INFO] Planned dependent service recreate: $(join_by_comma "${RESTART_SERVICES[@]}")"
    echo "[INFO] Rollback checkpoint file would be created at: ${backup_path}"
    return 0
  fi

  local live_changed=0
  local env_updated=0
  local restart_attempted=0

  rollback_rabbitmq() {
    if (( live_changed == 1 )); then
      local current_container
      current_container="$(get_container_id "${PROJECT}" "${service_name}")"
      if [[ -n "${current_container}" ]] && [[ "$(get_container_status "${current_container}")" == "running" ]]; then
        if ! update_rabbitmq_password "${current_container}" "${user_name}" "${current_password}"; then
          warn "Best-effort RabbitMQ rollback failed. Manual intervention may be required."
        fi
      fi
    fi
    if (( env_updated == 1 )) && [[ -f "${backup_path}" ]]; then
      copy_file_exact "${backup_path}" "${ENV_FILE}" || true
    fi
    if (( restart_attempted == 1 )); then
      if ! invoke_compose_recreate; then
        warn "Best-effort dependent service rollback recreate failed. Manual restart may be required."
      fi
    fi
  }

  [[ ! -f "${ENV_FILE}" ]] || copy_file_exact "${ENV_FILE}" "${backup_path}" || die "Unable to create rollback checkpoint for .env."
  update_rabbitmq_password "${container_id}" "${user_name}" "${new_password}" || die "Failed to change RabbitMQ password in the live container."
  live_changed=1

  if ! test_rabbitmq_credential "${container_id}" "${user_name}" "${new_password}"; then
    rollback_rabbitmq
    die "RabbitMQ accepted the password change command, but live verification with the new credential failed."
  fi

  ensure_env_file
  update_or_add_env_setting "IGUANA_RABBITMQ_PASSWORD" "${new_password}" "${ENV_FILE}" || { rollback_rabbitmq; die "Failed to update IGUANA_RABBITMQ_PASSWORD in repository .env."; }
  local rabbit_host
  rabbit_host="$(get_setting_value "SPRING_RABBITMQ_HOST")"
  if [[ -z "${rabbit_host}" || "${rabbit_host}" == "localhost" || "${rabbit_host}" == "127.0.0.1" ]]; then
    update_or_add_env_setting "SPRING_RABBITMQ_PASSWORD" "${new_password}" "${ENV_FILE}" || { rollback_rabbitmq; die "Failed to update SPRING_RABBITMQ_PASSWORD in repository .env."; }
  fi
  env_updated=1

  invoke_compose_recreate || { rollback_rabbitmq; die "Failed to recreate dependent services after RabbitMQ rotation."; }
  restart_attempted=1

  local service
  for service in "${RESTART_SERVICES[@]}"; do
    wait_for_service_ready "${PROJECT}" "${service}" "${HEALTH_TIMEOUT_SECONDS}" || { rollback_rabbitmq; die "Service '${service}' did not become ready after RabbitMQ rotation."; }
  done

  local rabbit_after_restart
  rabbit_after_restart="$(get_container_id "${PROJECT}" "${service_name}")"
  [[ -n "${rabbit_after_restart}" ]] && [[ "$(get_container_status "${rabbit_after_restart}")" == "running" ]] || { rollback_rabbitmq; die "RabbitMQ container is not running after dependent service recreation."; }
  test_rabbitmq_credential "${rabbit_after_restart}" "${user_name}" "${new_password}" || { rollback_rabbitmq; die "RabbitMQ auth verification failed after dependent service recreation."; }

  echo "[INFO] RabbitMQ credential rotation applied successfully."
  echo "[INFO] Updated repository .env and recreated dependent services: $(join_by_comma "${RESTART_SERVICES[@]}")"
  echo "[INFO] Rollback checkpoint: ${backup_path}"
}

run_redis() {
  local service_name="redis"
  local container_id
  container_id="$(get_container_id "${PROJECT}" "${service_name}")"
  [[ -n "${container_id}" ]] || die "Redis container is not running for compose project '${PROJECT}'."
  [[ "$(get_container_status "${container_id}")" == "running" ]] || die "Redis container is not running for compose project '${PROJECT}'."

  local current_password
  current_password="$(resolve_current_redis_password "${container_id}")" || die "Unable to authenticate to live Redis with configured or documented fallback credentials."

  local new_password="${TARGET_PASSWORD}"
  [[ -n "${new_password}" ]] || new_password="$(new_random_hex_token 32)"
  [[ "${new_password}" != "${current_password}" ]] || die "Target Redis password must differ from the current live password."

  RESTART_SERVICES=()
  service_is_running "redis" && RESTART_SERVICES+=("redis")
  service_is_running "redis-exporter" && RESTART_SERVICES+=("redis-exporter")
  service_is_running "ops-worker" && RESTART_SERVICES+=("ops-worker")
  service_is_running "panel-web" && RESTART_SERVICES+=("panel-web")
  service_is_running "bot-telegram" && RESTART_SERVICES+=("bot-telegram")
  service_is_running "bot-vk" && RESTART_SERVICES+=("bot-vk")
  service_is_running "bot-max" && RESTART_SERVICES+=("bot-max")
  build_compose_files

  local backup_path="${REPO_ROOT}/.env.credential-migration-redis-$(date +%Y%m%d-%H%M%S).bak"
  if (( APPLY == 0 )); then
    echo "[INFO] Dry-run: Redis credential migration plan is ready."
    echo "[INFO] Compose project: ${PROJECT}"
    echo "[INFO] Live Redis authentication succeeded with the current credential candidate."
    echo "[INFO] Planned updates: IGUANA_REDIS_PASSWORD and SPRING_DATA_REDIS_PASSWORD in repository .env."
    echo "[INFO] Planned service recreate: $(join_by_comma "${RESTART_SERVICES[@]}")"
    echo "[INFO] Rollback checkpoint file would be created at: ${backup_path}"
    return 0
  fi

  local live_changed=0
  local env_updated=0
  local restart_attempted=0

  rollback_redis() {
    if (( live_changed == 1 )); then
      local current_container
      current_container="$(get_container_id "${PROJECT}" "${service_name}")"
      if [[ -n "${current_container}" ]] && [[ "$(get_container_status "${current_container}")" == "running" ]]; then
        if ! update_redis_password "${current_container}" "${new_password}" "${current_password}"; then
          warn "Best-effort Redis rollback failed. Manual intervention may be required."
        fi
      fi
    fi
    if (( env_updated == 1 )) && [[ -f "${backup_path}" ]]; then
      copy_file_exact "${backup_path}" "${ENV_FILE}" || true
    fi
    if (( restart_attempted == 1 )); then
      if ! invoke_compose_recreate; then
        warn "Best-effort coordinated Redis rollback recreate failed. Manual restart may be required."
      fi
    fi
  }

  [[ ! -f "${ENV_FILE}" ]] || copy_file_exact "${ENV_FILE}" "${backup_path}" || die "Unable to create rollback checkpoint for .env."
  update_redis_password "${container_id}" "${current_password}" "${new_password}" || die "Failed to change Redis password in the live container."
  live_changed=1

  if ! test_redis_credential "${container_id}" "${new_password}"; then
    rollback_redis
    die "Redis accepted the password change command, but live verification with the new credential failed."
  fi

  ensure_env_file
  update_or_add_env_setting "IGUANA_REDIS_PASSWORD" "${new_password}" "${ENV_FILE}" || { rollback_redis; die "Failed to update IGUANA_REDIS_PASSWORD in repository .env."; }
  local redis_host
  redis_host="$(get_setting_value "SPRING_DATA_REDIS_HOST")"
  if [[ -z "${redis_host}" || "${redis_host}" == "localhost" || "${redis_host}" == "127.0.0.1" ]]; then
    update_or_add_env_setting "SPRING_DATA_REDIS_PASSWORD" "${new_password}" "${ENV_FILE}" || { rollback_redis; die "Failed to update SPRING_DATA_REDIS_PASSWORD in repository .env."; }
  fi
  env_updated=1

  invoke_compose_recreate || { rollback_redis; die "Failed to recreate coordinated services after Redis rotation."; }
  restart_attempted=1

  local service
  for service in "${RESTART_SERVICES[@]}"; do
    wait_for_service_ready "${PROJECT}" "${service}" "${HEALTH_TIMEOUT_SECONDS}" || { rollback_redis; die "Service '${service}' did not become ready after Redis rotation."; }
  done

  local redis_after_restart
  redis_after_restart="$(get_container_id "${PROJECT}" "${service_name}")"
  [[ -n "${redis_after_restart}" ]] && [[ "$(get_container_status "${redis_after_restart}")" == "running" ]] || { rollback_redis; die "Redis container is not running after coordinated service recreation."; }
  test_redis_credential "${redis_after_restart}" "${new_password}" || { rollback_redis; die "Redis auth verification failed after coordinated service recreation."; }

  echo "[INFO] Redis credential rotation applied successfully."
  echo "[INFO] Updated repository .env and recreated services: $(join_by_comma "${RESTART_SERVICES[@]}")"
  echo "[INFO] Rollback checkpoint: ${backup_path}"
}

run_minio() {
  local service_name="minio"
  local init_service_name="minio-init"
  local container_id
  container_id="$(get_container_id "${PROJECT}" "${service_name}")"
  [[ -n "${container_id}" ]] || die "MinIO container is not running for compose project '${PROJECT}'."
  [[ "$(get_container_status "${container_id}")" == "running" ]] || die "MinIO container is not running for compose project '${PROJECT}'."

  local network_name
  local bucket_name
  local current_access_key
  local current_secret_key
  network_name="$(get_compose_network_name)"
  bucket_name="$(get_setting_value "APP_STORAGE_OBJECT_BUCKET")"
  [[ -n "${bucket_name}" ]] || bucket_name="iguana"
  current_access_key="$(get_container_env_value "${container_id}" "MINIO_ROOT_USER")"
  current_secret_key="$(get_container_env_value "${container_id}" "MINIO_ROOT_PASSWORD")"
  [[ -n "${current_access_key}" && -n "${current_secret_key}" ]] || die "Unable to resolve live MinIO root credentials from the running container environment."

  test_minio_bucket_access "${network_name}" "${current_access_key}" "${current_secret_key}" "${bucket_name}" || die "Unable to verify live MinIO bucket access with the current runtime credentials."

  local new_access_key="${TARGET_ACCESS_KEY}"
  local new_secret_key="${TARGET_SECRET_KEY}"
  [[ -n "${new_access_key}" ]] || new_access_key="$(new_random_hex_token 12)"
  [[ -n "${new_secret_key}" ]] || new_secret_key="$(new_random_hex_token 32)"
  if [[ "${new_access_key}" == "${current_access_key}" && "${new_secret_key}" == "${current_secret_key}" ]]; then
    die "At least one MinIO target credential value must differ from the current live runtime."
  fi

  RESTART_SERVICES=("minio" "minio-init")
  service_is_running "ops-worker" && RESTART_SERVICES+=("ops-worker")
  service_is_running "panel-web" && RESTART_SERVICES+=("panel-web")
  service_is_running "bot-telegram" && RESTART_SERVICES+=("bot-telegram")
  service_is_running "bot-vk" && RESTART_SERVICES+=("bot-vk")
  service_is_running "bot-max" && RESTART_SERVICES+=("bot-max")
  build_compose_files

  local backup_path="${REPO_ROOT}/.env.credential-migration-minio-$(date +%Y%m%d-%H%M%S).bak"
  if (( APPLY == 0 )); then
    echo "[INFO] Dry-run: MinIO credential migration plan is ready."
    echo "[INFO] Compose project: ${PROJECT}"
    echo "[INFO] Live MinIO bucket access succeeded with the current runtime credentials."
    echo "[INFO] Planned updates: APP_STORAGE_OBJECT_ACCESS_KEY and APP_STORAGE_OBJECT_SECRET_KEY in repository .env."
    echo "[INFO] Planned service recreate: $(join_by_comma "${RESTART_SERVICES[@]}")"
    echo "[INFO] Rollback checkpoint file would be created at: ${backup_path}"
    return 0
  fi

  local env_updated=0
  local restart_attempted=0

  rollback_minio() {
    if (( env_updated == 1 )) && [[ -f "${backup_path}" ]]; then
      copy_file_exact "${backup_path}" "${ENV_FILE}" || true
    fi
    if (( restart_attempted == 1 )); then
      if ! invoke_compose_recreate; then
        warn "Best-effort coordinated MinIO rollback recreate failed. Manual restart may be required."
      fi
    fi
  }

  [[ ! -f "${ENV_FILE}" ]] || copy_file_exact "${ENV_FILE}" "${backup_path}" || die "Unable to create rollback checkpoint for .env."
  ensure_env_file
  update_or_add_env_setting "APP_STORAGE_OBJECT_ACCESS_KEY" "${new_access_key}" "${ENV_FILE}" || { rollback_minio; die "Failed to update APP_STORAGE_OBJECT_ACCESS_KEY in repository .env."; }
  update_or_add_env_setting "APP_STORAGE_OBJECT_SECRET_KEY" "${new_secret_key}" "${ENV_FILE}" || { rollback_minio; die "Failed to update APP_STORAGE_OBJECT_SECRET_KEY in repository .env."; }
  env_updated=1

  invoke_compose_recreate || { rollback_minio; die "Failed to recreate coordinated services after MinIO rotation."; }
  restart_attempted=1

  wait_for_service_ready "${PROJECT}" "${service_name}" "${HEALTH_TIMEOUT_SECONDS}" || { rollback_minio; die "MinIO service did not become ready after coordinated service recreation."; }
  wait_for_service_completion_success "${PROJECT}" "${init_service_name}" "${HEALTH_TIMEOUT_SECONDS}" || { rollback_minio; die "MinIO init job did not complete successfully after coordinated service recreation."; }

  local service
  for service in "${RESTART_SERVICES[@]}"; do
    if [[ "${service}" != "${service_name}" && "${service}" != "${init_service_name}" ]]; then
      wait_for_service_ready "${PROJECT}" "${service}" "${HEALTH_TIMEOUT_SECONDS}" || { rollback_minio; die "Service '${service}' did not become ready after MinIO rotation."; }
    fi
  done

  local minio_after_restart
  minio_after_restart="$(get_container_id "${PROJECT}" "${service_name}")"
  [[ -n "${minio_after_restart}" ]] && [[ "$(get_container_status "${minio_after_restart}")" == "running" ]] || { rollback_minio; die "MinIO container is not running after coordinated service recreation."; }

  local runtime_access_key
  local runtime_secret_key
  runtime_access_key="$(get_container_env_value "${minio_after_restart}" "MINIO_ROOT_USER")"
  runtime_secret_key="$(get_container_env_value "${minio_after_restart}" "MINIO_ROOT_PASSWORD")"
  [[ -n "${runtime_access_key}" && -n "${runtime_secret_key}" ]] || { rollback_minio; die "MinIO runtime environment does not expose MINIO_ROOT_USER/MINIO_ROOT_PASSWORD after recreate."; }
  [[ "${runtime_access_key}" == "${new_access_key}" && "${runtime_secret_key}" == "${new_secret_key}" ]] || { rollback_minio; die "MinIO runtime environment does not match the newly persisted object-storage credentials."; }
  test_minio_bucket_access "${network_name}" "${new_access_key}" "${new_secret_key}" "${bucket_name}" || { rollback_minio; die "MinIO bucket access verification failed after coordinated service recreation."; }

  echo "[INFO] MinIO credential rotation applied successfully."
  echo "[INFO] Updated repository .env and recreated services: $(join_by_comma "${RESTART_SERVICES[@]}")"
  echo "[INFO] Rollback checkpoint: ${backup_path}"
}

run_grafana() {
  local service_name="grafana"
  local container_id
  container_id="$(get_container_id "${PROJECT}" "${service_name}")"
  [[ -n "${container_id}" ]] || die "Grafana container is not running for compose project '${PROJECT}'."
  [[ "$(get_container_status "${container_id}")" == "running" ]] || die "Grafana container is not running for compose project '${PROJECT}'."

  local user_name
  local bind_host
  local port
  user_name="$(get_setting_value "IGUANA_GRAFANA_ADMIN_USER")"
  bind_host="$(get_setting_value "IGUANA_GRAFANA_BIND_HOST")"
  port="$(get_setting_value "IGUANA_GRAFANA_PORT")"
  [[ -n "${user_name}" ]] || user_name="admin"
  [[ -n "${bind_host}" ]] || bind_host="127.0.0.1"
  [[ -n "${port}" ]] || port="3000"

  local current_password
  current_password="$(resolve_current_grafana_password "${bind_host}" "${port}" "${user_name}")" || die "Unable to authenticate to live Grafana with configured or documented fallback credentials."

  local new_password="${TARGET_PASSWORD}"
  [[ -n "${new_password}" ]] || new_password="$(new_random_hex_token 32)"
  [[ "${new_password}" != "${current_password}" ]] || die "Target Grafana password must differ from the current live password."

  RESTART_SERVICES=()
  service_is_running "grafana" && RESTART_SERVICES+=("grafana")
  build_compose_files

  local backup_path="${REPO_ROOT}/.env.credential-migration-grafana-$(date +%Y%m%d-%H%M%S).bak"
  if (( APPLY == 0 )); then
    echo "[INFO] Dry-run: Grafana credential migration plan is ready."
    echo "[INFO] Compose project: ${PROJECT}"
    echo "[INFO] Live Grafana authentication succeeded with the current credential candidate."
    echo "[INFO] Planned updates: IGUANA_GRAFANA_ADMIN_PASSWORD in repository .env."
    echo "[INFO] Planned service recreate: $(join_by_comma "${RESTART_SERVICES[@]}")"
    echo "[INFO] Rollback checkpoint file would be created at: ${backup_path}"
    return 0
  fi

  local live_changed=0
  local env_updated=0
  local restart_attempted=0

  rollback_grafana() {
    if (( live_changed == 1 )); then
      local current_container
      current_container="$(get_container_id "${PROJECT}" "${service_name}")"
      if [[ -n "${current_container}" ]] && [[ "$(get_container_status "${current_container}")" == "running" ]]; then
        if ! update_grafana_password "${current_container}" "${current_password}"; then
          warn "Best-effort Grafana rollback failed. Manual intervention may be required."
        fi
      fi
    fi
    if (( env_updated == 1 )) && [[ -f "${backup_path}" ]]; then
      copy_file_exact "${backup_path}" "${ENV_FILE}" || true
    fi
    if (( restart_attempted == 1 )); then
      if ! invoke_compose_recreate; then
        warn "Best-effort Grafana rollback recreate failed. Manual restart may be required."
      fi
    fi
  }

  [[ ! -f "${ENV_FILE}" ]] || copy_file_exact "${ENV_FILE}" "${backup_path}" || die "Unable to create rollback checkpoint for .env."
  update_grafana_password "${container_id}" "${new_password}" || die "Failed to change Grafana password in the live container."
  live_changed=1

  if ! test_grafana_credential "${bind_host}" "${port}" "${user_name}" "${new_password}"; then
    rollback_grafana
    die "Grafana accepted the password change command, but live verification with the new credential failed."
  fi

  ensure_env_file
  update_or_add_env_setting "IGUANA_GRAFANA_ADMIN_PASSWORD" "${new_password}" "${ENV_FILE}" || { rollback_grafana; die "Failed to update IGUANA_GRAFANA_ADMIN_PASSWORD in repository .env."; }
  env_updated=1

  invoke_compose_recreate || { rollback_grafana; die "Failed to recreate Grafana after password rotation."; }
  restart_attempted=1

  local service
  for service in "${RESTART_SERVICES[@]}"; do
    wait_for_service_ready "${PROJECT}" "${service}" "${HEALTH_TIMEOUT_SECONDS}" || { rollback_grafana; die "Service '${service}' did not become ready after Grafana rotation."; }
  done

  local grafana_after_restart
  grafana_after_restart="$(get_container_id "${PROJECT}" "${service_name}")"
  [[ -n "${grafana_after_restart}" ]] && [[ "$(get_container_status "${grafana_after_restart}")" == "running" ]] || { rollback_grafana; die "Grafana container is not running after service recreation."; }
  test_grafana_credential "${bind_host}" "${port}" "${user_name}" "${new_password}" || { rollback_grafana; die "Grafana auth verification failed after service recreation."; }

  echo "[INFO] Grafana credential rotation applied successfully."
  echo "[INFO] Updated repository .env and recreated services: $(join_by_comma "${RESTART_SERVICES[@]}")"
  echo "[INFO] Rollback checkpoint: ${backup_path}"
}

case "${COMPONENT}" in
  postgresql)
    run_postgresql
    ;;
  rabbitmq)
    run_rabbitmq
    ;;
  redis)
    run_redis
    ;;
  minio)
    run_minio
    ;;
  grafana)
    run_grafana
    ;;
esac
