#!/bin/sh
set -eu
umask 077

destination="/backup/offhost/postgres"
retention_days="${IGUANA_BACKUP_RETENTION_DAYS:-30}"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
base="iguana-postgres-${stamp}"
tmp_dump="${destination}/.${base}.dump.tmp"
final_dump="${destination}/${base}.dump"
tmp_sha="${destination}/.${base}.dump.sha256.tmp"
final_sha="${final_dump}.sha256"
tmp_manifest="${destination}/.${base}.manifest.json.tmp"
final_manifest="${destination}/${base}.manifest.json"

mkdir -p "${destination}"

cleanup() {
  rm -f "${tmp_dump}" "${tmp_sha}" "${tmp_manifest}"
}
trap cleanup EXIT INT TERM

echo "[BACKUP] PostgreSQL pg_dump -> ${final_dump}"
pg_dump \
  --format=custom \
  --no-owner \
  --no-privileges \
  --file="${tmp_dump}" \
  "${PGDATABASE}"

# Validate the custom-format archive before publication.
pg_restore --list "${tmp_dump}" >/dev/null

sha256="$(sha256sum "${tmp_dump}" | awk '{print $1}')"
size_bytes="$(wc -c < "${tmp_dump}" | tr -d '[:space:]')"
printf '%s  %s\n' "${sha256}" "$(basename "${final_dump}")" > "${tmp_sha}"
printf '{"kind":"postgresql","database":"%s","created_at":"%s","format":"pg_dump_custom","size_bytes":%s,"sha256":"%s"}\n' \
  "${PGDATABASE}" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${size_bytes}" "${sha256}" > "${tmp_manifest}"

mv "${tmp_dump}" "${final_dump}"
mv "${tmp_sha}" "${final_sha}"
mv "${tmp_manifest}" "${final_manifest}"
trap - EXIT INT TERM

# Time-based retention. Current artifacts cannot match +N days and therefore
# cannot be removed by the same run that produced them.
find "${destination}" -type f -mtime "+${retention_days}" \
  \( -name 'iguana-postgres-*.dump' -o -name 'iguana-postgres-*.dump.sha256' -o -name 'iguana-postgres-*.manifest.json' \) \
  -delete 2>/dev/null || true

echo "[GREEN] PostgreSQL backup validated and published: ${final_dump}"
