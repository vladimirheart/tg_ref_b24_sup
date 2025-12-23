#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORIGINAL_DIR="$(pwd)"

cd "${SCRIPT_DIR}"

# Choose a port if the default one is busy and the user has not explicitly set APP_HTTP_PORT.
DEFAULT_PORT="${APP_HTTP_PORT:-8080}"
is_port_listening() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -lnt "sport = :${port}" 2>/dev/null | grep -q ":${port} "
  elif command -v netstat >/dev/null 2>&1; then
    netstat -lnt 2>/dev/null | awk '{print $4}' | grep -q ":${port}$"
  else
    return 1
  fi
}

if [[ -z "${APP_HTTP_PORT:-}" && -n "${DEFAULT_PORT}" ]]; then
  if is_port_listening "${DEFAULT_PORT}"; then
    APP_HTTP_PORT=8081
    export APP_HTTP_PORT
    echo "[INFO] Port ${DEFAULT_PORT} is already in use. Falling back to APP_HTTP_PORT=${APP_HTTP_PORT}." >&2
  fi
elif [[ -n "${APP_HTTP_PORT:-}" && is_port_listening "${APP_HTTP_PORT}" ]]; then
  echo "[WARN] APP_HTTP_PORT=${APP_HTTP_PORT} appears to be in use. The application may fail to start." >&2
fi

JAVA_BIN=""
if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
  JAVA_BIN="${JAVA_HOME}/bin/java"
elif command -v java >/dev/null 2>&1; then
  JAVA_BIN="$(command -v java)"
fi

if [[ -z "${JAVA_BIN}" ]]; then
  echo "[ERROR] Java executable not found. Install JDK 17 and ensure it is on PATH or JAVA_HOME." >&2
  exit 1
fi

JAVA_VERSION="$("${JAVA_BIN}" -version 2>&1 | awk -F\" '/version/ {print $2; exit}')"
JAVA_MAJOR="${JAVA_VERSION%%.*}"
if [[ "${JAVA_MAJOR}" =~ ^[0-9]+$ ]]; then
  if (( JAVA_MAJOR < 17 )); then
    echo "[ERROR] JDK 17 or newer is required, but ${JAVA_VERSION:-unknown} was detected." >&2
    exit 1
  fi
else
  echo "[WARN] Unable to parse Java version from '${JAVA_VERSION:-unknown}'. Proceeding anyway." >&2
fi

if [[ -x "${SCRIPT_DIR}/mvnw" ]]; then
  MVN_CMD="${SCRIPT_DIR}/mvnw"
elif command -v mvn >/dev/null 2>&1; then
  MVN_CMD="$(command -v mvn)"
else
  echo "[ERROR] Maven wrapper not found and mvn command is unavailable." >&2
  exit 1
fi

MVN_ARGS=()
if [[ -n "${JAVA_OPTS:-}" ]]; then
  MVN_ARGS+=("-Dspring-boot.run.jvmArguments=${JAVA_OPTS}")
fi
if [[ -n "${SPRING_OPTS:-}" ]]; then
  MVN_ARGS+=("-Dspring-boot.run.arguments=${SPRING_OPTS}")
fi

cleanup() {
  if [[ -n "${MVN_PID:-}" ]]; then
    kill -INT "${MVN_PID}" 2>/dev/null || true
    wait "${MVN_PID}" 2>/dev/null || true
  fi
  cd "${ORIGINAL_DIR}"
}
trap 'cleanup; exit 130' INT TERM

"${MVN_CMD}" "${MVN_ARGS[@]}" spring-boot:run "$@" &
MVN_PID=$!
wait "${MVN_PID}"
cd "${ORIGINAL_DIR}"
