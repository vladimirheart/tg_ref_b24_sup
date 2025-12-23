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

case "${ENGINE}" in
  python)
    if ! command -v flask >/dev/null 2>&1; then
      echo "[ERROR] Flask CLI is not available. Activate the Python environment before running." >&2
      exit 1
    fi
    export FLASK_APP="panel/app.py"
    PORT="${PORT:-5000}"
    echo "[INFO] Starting Flask panel on port ${PORT} using shared APP_DB_* settings..."
    cd "${ROOT_DIR}"
    exec flask run --host=0.0.0.0 --port="${PORT}"
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
