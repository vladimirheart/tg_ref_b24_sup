#!/bin/sh
set -eu
umask 077

destination="/backup/offhost/packages/postgres"
retention_days="${IGUANA_BACKUP_RETENTION_DAYS:-30}"
mode="${IGUANA_BACKUP_MODE:-critical}"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
base="iguana-postgres-${stamp}"
stage="/tmp/${base}.$$"
tmp_archive="${destination}/.${base}.tar.gz.tmp"
final_archive="${destination}/${base}.tar.gz"
tmp_archive_sha="${destination}/.${base}.tar.gz.sha256.tmp"
final_archive_sha="${final_archive}.sha256"

mkdir -p "${destination}"
rm -rf "${stage}"
mkdir -p "${stage}"

cleanup() {
  rm -rf "${stage}"
  rm -f "${tmp_archive}" "${tmp_archive_sha}"
}
trap cleanup EXIT INT TERM

dump="${stage}/database.dump"
echo "[BACKUP] PostgreSQL pg_dump -> portable recovery package ${final_archive}"
pg_dump \
  --format=custom \
  --no-owner \
  --no-privileges \
  --file="${dump}" \
  "${PGDATABASE}"

pg_restore --list "${dump}" >/dev/null

dump_sha="$(sha256sum "${dump}" | awk '{print $1}')"
dump_size="$(wc -c < "${dump}" | tr -d '[:space:]')"
printf '%s  database.dump\n' "${dump_sha}" > "${stage}/database.dump.sha256"
printf '{"kind":"postgresql","database":"%s","created_at":"%s","archive_format":"tar.gz","payload_format":"pg_dump_custom","backup_mode":"%s","dump_size_bytes":%s,"dump_sha256":"%s"}\n' \
  "${PGDATABASE}" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${mode}" "${dump_size}" "${dump_sha}" > "${stage}/manifest.json"

tar -czf "${tmp_archive}" -C "${stage}" .
tar -tzf "${tmp_archive}" >/dev/null

archive_sha="$(sha256sum "${tmp_archive}" | awk '{print $1}')"
printf '%s  %s\n' "${archive_sha}" "$(basename "${final_archive}")" > "${tmp_archive_sha}"

mv "${tmp_archive}" "${final_archive}"
mv "${tmp_archive_sha}" "${final_archive_sha}"
trap - EXIT INT TERM
rm -rf "${stage}"

find "${destination}" -maxdepth 1 -type f -mtime "+${retention_days}" \
  \( -name 'iguana-postgres-*.tar.gz' -o -name 'iguana-postgres-*.tar.gz.sha256' \) \
  -delete 2>/dev/null || true

echo "[GREEN] PostgreSQL recovery package published: ${final_archive}"
