#!/bin/sh
set -eu
umask 077

bucket="${APP_STORAGE_OBJECT_BUCKET:?APP_STORAGE_OBJECT_BUCKET is required}"
root="/backup/offhost/minio"
snapshots="${root}/snapshots"
manifests="${root}/manifests"
retention_days="${IGUANA_MINIO_BACKUP_RETENTION_DAYS:-14}"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
tmp_snapshot="${snapshots}/.${stamp}.tmp"
final_snapshot="${snapshots}/${stamp}"
tmp_manifest="${manifests}/.${stamp}.manifest.json.tmp"
final_manifest="${manifests}/${stamp}.manifest.json"

mkdir -p "${snapshots}" "${manifests}"
rm -rf "${tmp_snapshot}"
mkdir -p "${tmp_snapshot}/objects"

cleanup() {
  rm -rf "${tmp_snapshot}"
  rm -f "${tmp_manifest}"
}
trap cleanup EXIT INT TERM

attempt=0
until mc alias set primary http://minio:9000 "${APP_STORAGE_OBJECT_ACCESS_KEY}" "${APP_STORAGE_OBJECT_SECRET_KEY}" >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "${attempt}" -ge 60 ]; then
    echo "[ERROR] Timed out waiting for primary MinIO" >&2
    exit 1
  fi
  sleep 2
done

# A timestamped snapshot is intentionally used instead of --remove mirroring:
# deleting a primary object must not delete the only backup copy immediately.
echo "[BACKUP] MinIO bucket ${bucket} -> ${final_snapshot}"
mc ls --recursive --json "primary/${bucket}" > "${tmp_snapshot}/inventory.jsonl"
source_objects="$(wc -l < "${tmp_snapshot}/inventory.jsonl" | tr -d '[:space:]')"
echo "[BACKUP] MinIO source objects: ${source_objects}"
if [ "${source_objects}" -lt 1 ]; then
  echo "[ERROR] MinIO source bucket is empty before snapshot copy: ${bucket}" >&2
  exit 1
fi

# mc mirror is intended for a MinIO/S3 target. For an S3/MinIO source and
# local-filesystem target use mc cp --recursive so every object is materialized.
mc cp --recursive "primary/${bucket}/" "${tmp_snapshot}/objects/"

local_files="$(find "${tmp_snapshot}/objects" -type f | wc -l | tr -d '[:space:]')"
if [ "${source_objects}" != "${local_files}" ]; then
  echo "[ERROR] MinIO snapshot object count mismatch: source=${source_objects}, local=${local_files}" >&2
  exit 1
fi
inventory_sha="$(sha256sum "${tmp_snapshot}/inventory.jsonl" | awk '{print $1}')"
printf 'iguana-minio-restore-sentinel-%s\n' "${stamp}" > "${tmp_snapshot}/restore-sentinel.txt"
sentinel_sha="$(sha256sum "${tmp_snapshot}/restore-sentinel.txt" | awk '{print $1}')"

mv "${tmp_snapshot}" "${final_snapshot}"
printf '{"kind":"minio","bucket":"%s","created_at":"%s","snapshot":"%s","source_object_count":%s,"local_file_count":%s,"inventory_sha256":"%s","sentinel_sha256":"%s"}\n' \
  "${bucket}" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${stamp}" "${source_objects}" "${local_files}" "${inventory_sha}" "${sentinel_sha}" > "${tmp_manifest}"
mv "${tmp_manifest}" "${final_manifest}"
trap - EXIT INT TERM

find "${snapshots}" -mindepth 1 -maxdepth 1 -type d -mtime "+${retention_days}" -exec rm -rf {} + 2>/dev/null || true
find "${manifests}" -maxdepth 1 -type f -name '*.manifest.json' -mtime "+${retention_days}" -delete 2>/dev/null || true

echo "[GREEN] MinIO snapshot published: ${final_snapshot}; files=${local_files}"
