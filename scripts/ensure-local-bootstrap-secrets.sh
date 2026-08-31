#!/usr/bin/env bash
set -euo pipefail

ENV_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-file)
      ENV_FILE="${2:-}"
      shift 2
      ;;
    *)
      echo "[ERROR] Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

if [[ -z "${ENV_FILE}" ]]; then
  echo "[ERROR] Usage: ensure-local-bootstrap-secrets.sh --env-file <path>" >&2
  exit 1
fi

generate_hex_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
    return
  fi
  if [[ -r /dev/urandom ]]; then
    od -An -N32 -tx1 /dev/urandom | tr -d ' \n'
    return
  fi
  if command -v python3 >/dev/null 2>&1; then
    python3 - <<'PY'
import secrets
print(secrets.token_hex(32))
PY
    return
  fi
  echo "[ERROR] Unable to generate a secure bootstrap secret." >&2
  exit 1
}

generate_base64_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 32 | tr -d '\n'
    return
  fi
  if command -v python3 >/dev/null 2>&1; then
    python3 - <<'PY'
import base64
import secrets
print(base64.b64encode(secrets.token_bytes(32)).decode("ascii"))
PY
    return
  fi
  echo "[ERROR] Unable to generate a secure bootstrap base64 secret." >&2
  exit 1
}

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "${value}"
}

get_setting_value() {
  local name="$1"
  local env_override="${!name-}"
  if [[ -n "$(trim "${env_override}")" ]]; then
    printf '%s' "$(trim "${env_override}")"
    return
  fi
  if [[ -n "${SETTINGS[$name]+x}" && -n "$(trim "${SETTINGS[$name]}")" ]]; then
    printf '%s' "$(trim "${SETTINGS[$name]}")"
    return
  fi
  printf ''
}

is_truthy() {
  case "$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')" in
    1|true|yes|on) return 0 ;;
    *) return 1 ;;
  esac
}

is_local_bootstrap_contour() {
  local bootstrap_mode database_mode coordination_mode object_storage_mode
  bootstrap_mode="$(get_setting_value "IGUANA_BOOTSTRAP_DB_MODE" | tr '[:upper:]' '[:lower:]')"
  database_mode="$(get_setting_value "APP_DB_MODE" | tr '[:upper:]' '[:lower:]')"
  coordination_mode="$(get_setting_value "APP_COORDINATION_MODE" | tr '[:upper:]' '[:lower:]')"
  object_storage_mode="$(get_setting_value "APP_STORAGE_OBJECT_MODE" | tr '[:upper:]' '[:lower:]')"

  local coordination_required storage_required
  coordination_required="$(get_setting_value "APP_COORDINATION_REQUIRED_FOR_POSTGRESQL")"
  storage_required="$(get_setting_value "APP_STORAGE_OBJECT_REQUIRED_FOR_POSTGRESQL")"

  [[ "${bootstrap_mode}" == "postgresql" ]] \
    && [[ "${database_mode}" == "postgresql" ]] \
    && [[ "${coordination_mode}" != "redis" ]] \
    && [[ "${object_storage_mode}" != "s3" ]] \
    && ! is_truthy "${coordination_required}" \
    && ! is_truthy "${storage_required}"
}

needs_generated_secret() {
  local value
  value="$(trim "$1")"
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

is_valid_monitoring_master_key_payload() {
  local encoded
  encoded="$(trim "$1")"
  if [[ -z "${encoded}" ]]; then
    return 1
  fi
  if command -v python3 >/dev/null 2>&1; then
    python3 - "${encoded}" <<'PY'
import base64
import sys
value = sys.argv[1].strip()
try:
    decoded = base64.b64decode(value)
except Exception:
    raise SystemExit(1)
raise SystemExit(0 if len(decoded) in (16, 24, 32) else 1)
PY
    return $?
  fi
  if command -v openssl >/dev/null 2>&1; then
    local decoded_length
    decoded_length="$(printf '%s' "${encoded}" | openssl base64 -d -A 2>/dev/null | wc -c | tr -d ' ')"
    [[ "${decoded_length}" == "16" || "${decoded_length}" == "24" || "${decoded_length}" == "32" ]]
    return $?
  fi
  return 1
}

resolve_monitoring_master_key_value() {
  local repo_root legacy_key_path legacy_encoded
  repo_root="$(cd "$(dirname "${ENV_FILE}")" && pwd)"
  legacy_key_path="${repo_root}/config/shared/monitoring-credentials.key"

  if [[ -f "${legacy_key_path}" ]]; then
    legacy_encoded="$(tr -d '\r\n' < "${legacy_key_path}")"
    if [[ -n "${legacy_encoded}" ]]; then
      if ! is_valid_monitoring_master_key_payload "${legacy_encoded}"; then
        echo "[ERROR] Legacy monitoring credentials key is not valid Base64 AES material: ${legacy_key_path}" >&2
        exit 1
      fi
      printf 'base64:%s' "${legacy_encoded}"
      return
    fi
  fi

  printf 'base64:%s' "$(generate_base64_secret)"
}

declare -a LINES=()
declare -A SETTINGS=()
declare -A INDICES=()

if [[ -f "${ENV_FILE}" ]]; then
  line_index=0
  while IFS= read -r line || [[ -n "${line}" ]]; do
    LINES+=("${line}")
    trimmed_line="$(trim "${line}")"
    if [[ -n "${trimmed_line}" && "${trimmed_line:0:1}" != "#" && "${trimmed_line}" == *=* ]]; then
      name="$(trim "${trimmed_line%%=*}")"
      value="${trimmed_line#*=}"
      if [[ -z "${SETTINGS[$name]+x}" ]]; then
        SETTINGS["$name"]="${value}"
        INDICES["$name"]="${line_index}"
      fi
    fi
    line_index=$((line_index + 1))
  done < "${ENV_FILE}"
fi

if ! is_local_bootstrap_contour; then
  exit 0
fi

declare -a PENDING_ADDS=()
declare -a MUTATIONS=()

maybe_set_secret() {
  local name="$1"
  shift
  local current_value
  current_value="$(get_setting_value "${name}")"
  if ! needs_generated_secret "${current_value}" "$@"; then
    return
  fi

  local new_value
  case "${name}" in
    MONITORING_CREDENTIALS_MASTER_KEY)
      new_value="$(resolve_monitoring_master_key_value)"
      ;;
    *)
      new_value="$(generate_hex_secret)"
      ;;
  esac

  if [[ -n "${INDICES[$name]+x}" ]]; then
    local index="${INDICES[$name]}"
    LINES[$index]="${name}=${new_value}"
    MUTATIONS+=("updated ${name}")
  else
    PENDING_ADDS+=("${name}=${new_value}")
    MUTATIONS+=("added ${name}")
  fi
  SETTINGS["$name"]="${new_value}"
}

maybe_set_secret "APP_INTERNAL_BOT_API_TOKEN" "change-me" "iguana-internal-bot-token"
maybe_set_secret "APP_SECURITY_REMEMBER_ME_KEY" "change-me" "iguana-panel-remember-me"
maybe_set_secret "MONITORING_CREDENTIALS_MASTER_KEY" "change-me" "iguana-monitoring-key"

if [[ "${#MUTATIONS[@]}" -eq 0 ]]; then
  exit 0
fi

if [[ "${#PENDING_ADDS[@]}" -gt 0 ]]; then
  if [[ "${#LINES[@]}" -gt 0 && -n "$(trim "${LINES[${#LINES[@]}-1]}")" ]]; then
    LINES+=("")
  fi
  LINES+=("# Local bootstrap generated secrets")
  for pending_line in "${PENDING_ADDS[@]}"; do
    LINES+=("${pending_line}")
  done
fi

tmp_file="${ENV_FILE}.tmp.$$"
{
  for line in "${LINES[@]}"; do
    printf '%s\n' "${line}"
  done
} > "${tmp_file}"
mv "${tmp_file}" "${ENV_FILE}"

printf '[INFO] Local bootstrap secret contract updated in %s: %s.\n' "${ENV_FILE}" "$(IFS=', '; printf '%s' "${MUTATIONS[*]}")"
