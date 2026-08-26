#!/bin/sh
set -eu
umask 077

bucket="${APP_STORAGE_OBJECT_BUCKET:?APP_STORAGE_OBJECT_BUCKET is required}"
destination="/backup/offhost/packages/minio"
retention_days="${IGUANA_MINIO_BACKUP_RETENTION_DAYS:-14}"
mode="${IGUANA_BACKUP_MODE:-critical}"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
base="iguana-minio-${stamp}"
stage="/tmp/${base}.$$"
tmp_archive="${destination}/.${base}.tar.gz.tmp"
final_archive="${destination}/${base}.tar.gz"
tmp_archive_sha="${destination}/.${base}.tar.gz.sha256.tmp"
final_archive_sha="${final_archive}.sha256"

mkdir -p "${destination}"
rm -rf "${stage}"
mkdir -p "${stage}/objects"

cleanup() {
  rm -rf "${stage}"
  rm -f "${tmp_archive}" "${tmp_archive_sha}"
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

echo "[BACKUP] MinIO bucket ${bucket} -> portable recovery package ${final_archive}"
mc ls --recursive --json "primary/${bucket}" > "${stage}/inventory.jsonl"
source_objects="$(wc -l < "${stage}/inventory.jsonl" | tr -d '[:space:]')"
echo "[BACKUP] MinIO source objects: ${source_objects}"

# An empty object bucket is a valid production state. Publish a recoverable
# zero-object package instead of treating it as a backup failure.
if [ "${source_objects}" -gt 0 ]; then
  mc cp --recursive "primary/${bucket}/" "${stage}/objects/"
fi

local_files="$(find "${stage}/objects" -type f | wc -l | tr -d '[:space:]')"
if [ "${source_objects}" != "${local_files}" ]; then
  echo "[ERROR] MinIO package object count mismatch: source=${source_objects}, local=${local_files}" >&2
  exit 1
fi

printf 'iguana-minio-restore-sentinel-%s\n' "${stamp}" > "${stage}/restore-sentinel.txt"
(
  cd "${stage}"
  : > checksums.sha256
  find objects -type f | sort | while IFS= read -r file; do
    sha256sum "${file}"
  done >> checksums.sha256
  sha256sum inventory.jsonl restore-sentinel.txt >> checksums.sha256
)
printf '{"kind":"minio","bucket":"%s","created_at":"%s","archive_format":"tar.gz","backup_mode":"%s","source_object_count":%s,"local_file_count":%s}\n' \
  "${bucket}" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${mode}" "${source_objects}" "${local_files}" > "${stage}/manifest.json"

tar -czf "${tmp_archive}" -C "${stage}" .
tar -tzf "${tmp_archive}" >/dev/null
archive_sha="$(sha256sum "${tmp_archive}" | awk '{print $1}')"
printf '%s  %s\n' "${archive_sha}" "$(basename "${final_archive}")" > "${tmp_archive_sha}"

mv "${tmp_archive}" "${final_archive}"
mv "${tmp_archive_sha}" "${final_archive_sha}"
trap - EXIT INT TERM
rm -rf "${stage}"

find "${destination}" -maxdepth 1 -type f -mtime "+${retention_days}" \
  \( -name 'iguana-minio-*.tar.gz' -o -name 'iguana-minio-*.tar.gz.sha256' \) \
  -delete 2>/dev/null || true

echo "[GREEN] MinIO recovery package published: ${final_archive}; objects=${local_files}"
