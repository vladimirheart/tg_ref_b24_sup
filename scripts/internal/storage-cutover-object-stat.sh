#!/bin/sh
set -u

if ! mc alias set local http://minio:9000 "$IGUANA_GATE_ACCESS_KEY" "$IGUANA_GATE_SECRET_KEY" >/dev/null; then
  echo "[GATE_OBJECT] error alias"
  exit 2
fi

object="local/$IGUANA_GATE_BUCKET/$IGUANA_GATE_OBJECT_KEY"
if mc stat "$object" >/dev/null 2>&1; then
  echo "[GATE_OBJECT] present"
  exit 0
fi

echo "[GATE_OBJECT] missing"
exit 4
