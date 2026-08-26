#!/bin/sh
set -eu

if [ -z "${APP_INSTANCE_ID:-}" ]; then
  APP_INSTANCE_ID="${HOSTNAME:-local}"
  export APP_INSTANCE_ID
fi

if [ -z "${APP_PANEL_LOG_PATH:-}" ]; then
  APP_PANEL_LOG_PATH="/opt/iguana/logs/spring-panel-${APP_RUNTIME_ROLE:-all}-${APP_INSTANCE_ID}.log"
  export APP_PANEL_LOG_PATH
fi

exec java ${JAVA_OPTS:-} -jar /opt/iguana/panel/app.jar
