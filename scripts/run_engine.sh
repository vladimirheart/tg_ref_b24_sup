#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<USAGE
Usage: ENGINE=[python|java] [PORT=5000] [SPRING_OPTS="--server.port=8080"] $0

Switch between Python (Flask) and Java (Spring) panel runtimes while reusing the same
SQLite/JSON data configured via APP_DB_* and config/shared/*.json.

Options (env vars):
  ENGINE      Target runtime: python (default) or java.
  PORT        HTTP port for Flask panel (ENGINE=python, default: 5000).
  SPRING_OPTS Extra arguments passed to Spring Boot (ENGINE=java), e.g. "--server.port=8080".

The script keeps the working directory unchanged and delegates startup to the existing
entrypoints:
  - Flask panel: flask run --host=0.0.0.0 --port=$PORT (FLASK_APP=panel/app.py)
  - Spring panel: spring-panel/run-linux.sh (with optional SPRING_OPTS)
USAGE
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

ENGINE="${ENGINE:-python}"
ENGINE="${ENGINE,,}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-${ROOT_DIR}/.env}"

load_env() {
  if [[ -f "${ENV_FILE}" ]]; then
    echo "[INFO] Loading environment from ${ENV_FILE}"
    # Parse .env-style KEY=VALUE pairs without executing arbitrary commands.
    # Supports optional leading "export" and quoted values.
    while IFS= read -r line || [[ -n "${line}" ]]; do
      # Trim carriage returns for Windows-formatted files.
      line="${line%$'\r'}"

      # Skip blanks and comments.
      [[ -z "${line//[[:space:]]/}" ]] && continue
      [[ "${line}" =~ ^[[:space:]]*# ]] && continue

      # Drop an optional leading "export".
      line="${line#[[:space:]]*export[[:space:]]}"

      if [[ "${line}" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)[[:space:]]*=[[:space:]]*(.*)$ ]]; then
        var="${BASH_REMATCH[1]}"
        raw_val="${BASH_REMATCH[2]}"

        # Trim trailing whitespace from unquoted values.
        raw_val="${raw_val%"${raw_val##*[![:space:]]}"}" || true

        # Strip surrounding single/double quotes if present.
        if [[ "${raw_val}" =~ ^"(.*)"$ ]]; then
          val="${BASH_REMATCH[1]}"
        elif [[ "${raw_val}" =~ ^'(.*)'$ ]]; then
          val="${BASH_REMATCH[1]}"
        else
          val="${raw_val}"
        fi

        export "${var}"="${val}"
      else
        echo "[WARN] Skipping invalid line in ${ENV_FILE}: ${line}" >&2
      fi
    done < "${ENV_FILE}"
  fi
}

require_vars() {
  local missing=()
  for var in "$@"; do
    if [[ -z "${!var:-}" ]]; then
      missing+=("${var}")
    fi
  done

  if [[ ${#missing[@]} -gt 0 ]]; then
    echo "[ERROR] Missing required variables: ${missing[*]}" >&2
    if [[ ! -f "${ENV_FILE}" ]]; then
      echo "[HINT] Create ${ENV_FILE} or export the variables in your shell before running." >&2
    else
      echo "[HINT] Populate ${ENV_FILE} with the missing values or override via environment variables." >&2
    fi
    exit 1
  fi
}

load_env

case "${ENGINE}" in
  python)
    if ! command -v flask >/dev/null 2>&1; then
      echo "[ERROR] Flask CLI is not available. Activate the Python environment before running." >&2
      exit 1
    fi
    # Ensure we keep the GIL enabled for greenlet/gevent compatibility unless explicitly overridden.
    export PYTHON_GIL="${PYTHON_GIL:-1}"
    export FLASK_APP="panel/app.py"
    PORT="${PORT:-5000}"
    require_vars TELEGRAM_BOT_TOKEN
    echo "[INFO] Starting Flask panel on port ${PORT} using shared APP_DB_* settings..."
    cd "${ROOT_DIR}"
    # Ensure the project root stays ahead of the panel directory so imports resolve to
    # the shared config package instead of the legacy panel/config.py module.
    export PYTHONPATH="${ROOT_DIR}:${PYTHONPATH:-}"
    exec python -X gil=1 -m flask run --host=0.0.0.0 --port="${PORT}"
    ;;
  java)
    RUNNER="${ROOT_DIR}/spring-panel/run-linux.sh"
    if [[ ! -x "${RUNNER}" ]]; then
      echo "[ERROR] ${RUNNER} is missing or not executable." >&2
      exit 1
    fi
    echo "[INFO] Starting Spring panel via run-linux.sh with SPRING_OPTS='${SPRING_OPTS:-}' ..."
    cd "${ROOT_DIR}/spring-panel"
    exec env SPRING_OPTS="${SPRING_OPTS:-}" "${RUNNER}" "${@}"
    ;;
  *)
    echo "[ERROR] Unknown ENGINE='${ENGINE}'. Use python or java." >&2
    usage
    exit 1
    ;;

esac
