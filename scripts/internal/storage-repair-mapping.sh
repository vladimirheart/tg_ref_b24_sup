#!/bin/sh
set -u

if ! mc alias set local http://minio:9000 "$IGUANA_REPAIR_ACCESS_KEY" "$IGUANA_REPAIR_SECRET_KEY" >/dev/null; then
  echo "[REPAIR_RESULT] error alias"
  exit 2
fi

canonical="local/$IGUANA_REPAIR_BUCKET/$IGUANA_REPAIR_CANONICAL_KEY"
legacy="local/$IGUANA_REPAIR_BUCKET/$IGUANA_REPAIR_LEGACY_KEY"

if mc stat "$canonical" >/dev/null 2>&1; then
  echo "[REPAIR_RESULT] canonical"
  exit 0
fi

if [ -n "${IGUANA_REPAIR_LOCAL_PATH:-}" ] && [ -f "$IGUANA_REPAIR_LOCAL_PATH" ]; then
  if mc cp "$IGUANA_REPAIR_LOCAL_PATH" "$canonical" >/dev/null 2>&1 \
      && mc stat "$canonical" >/dev/null 2>&1; then
    echo "[REPAIR_RESULT] local"
    exit 0
  fi
fi

if mc stat "$legacy" >/dev/null 2>&1; then
  if mc cp "$legacy" "$canonical" >/dev/null 2>&1 \
      && mc stat "$canonical" >/dev/null 2>&1; then
    echo "[REPAIR_RESULT] legacy"
    exit 0
  fi
fi

echo "[REPAIR_RESULT] missing"
exit 4
