#!/bin/sh
set -eu
umask 077

destination="/backup/offhost/packages/postgres"
success_file="${destination}/.iguana-restore-evidence.properties"
failure_file="${destination}/.iguana-restore-failure.properties"
attempt_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
succeeded=0
current_archive=""
work="/tmp/iguana-postgres-restore-$$"

write_failure() {
  code="$?"
  if [ "${succeeded}" -eq 1 ]; then
    return 0
  fi
  note="PostgreSQL restore rehearsal failed"
  if [ -n "${current_archive}" ]; then
    note="${note}: $(basename "${current_archive}")"
  fi
  tmp="${failure_file}.tmp.$$"
  {
    printf 'status=error\n'
    printf 'attempt_at=%s\n' "${attempt_at}"
    printf 'note=%s\n' "${note}"
  } > "${tmp}"
  mv "${tmp}" "${failure_file}"
  rm -rf "${work}"
  exit "${code}"
}
trap write_failure EXIT INT TERM

mkdir -p "${destination}"
current_archive="$(find "${destination}" -maxdepth 1 -type f -name 'iguana-postgres-*.tar.gz' | sort | tail -n 1)"
if [ -z "${current_archive}" ]; then
  echo "[ERROR] No PostgreSQL tar.gz recovery package found in ${destination}" >&2
  exit 1
fi

sha_file="${current_archive}.sha256"
[ -f "${sha_file}" ] || { echo "[ERROR] Package checksum is missing: ${sha_file}" >&2; exit 1; }

(
  cd "${destination}"
  sha256sum -c "$(basename "${sha_file}")"
)

rm -rf "${work}"
mkdir -p "${work}"
tar -xzf "${current_archive}" -C "${work}"
[ -f "${work}/database.dump" ] || { echo "[ERROR] database.dump missing from PostgreSQL package" >&2; exit 1; }
[ -f "${work}/database.dump.sha256" ] || { echo "[ERROR] payload checksum missing from PostgreSQL package" >&2; exit 1; }
[ -f "${work}/manifest.json" ] || { echo "[ERROR] manifest.json missing from PostgreSQL package" >&2; exit 1; }

(
  cd "${work}"
  sha256sum -c database.dump.sha256
)
pg_restore --list "${work}/database.dump" >/dev/null

pg_restore \
  --exit-on-error \
  --no-owner \
  --no-privileges \
  --dbname="${PGDATABASE}" \
  "${work}/database.dump"

table_count="$(psql -Atqc "select count(*) from information_schema.tables where table_schema = 'public';" "${PGDATABASE}")"
case "${table_count}" in
  ''|*[!0-9]*) echo "[ERROR] Invalid restored table count: ${table_count}" >&2; exit 1 ;;
esac
if [ "${table_count}" -lt 5 ]; then
  echo "[ERROR] Restore drill produced too few public tables: ${table_count}" >&2
  exit 1
fi

for required_table in tickets flyway_schema_history; do
  exists="$(psql -Atqc "select case when to_regclass('public.${required_table}') is null then 0 else 1 end;" "${PGDATABASE}")"
  [ "${exists}" = "1" ] || { echo "[ERROR] Required restored table is missing: ${required_table}" >&2; exit 1; }
done

verified_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
tmp="${success_file}.tmp.$$"
{
  printf 'status=ok\n'
  printf 'attempt_at=%s\n' "${attempt_at}"
  printf 'verified_at=%s\n' "${verified_at}"
  printf 'note=Automated PostgreSQL tar.gz restore rehearsal passed for %s; public_tables=%s\n' "$(basename "${current_archive}")" "${table_count}"
} > "${tmp}"
mv "${tmp}" "${success_file}"
rm -f "${failure_file}"
rm -rf "${work}"
succeeded=1
trap - EXIT INT TERM

echo "[GREEN] PostgreSQL isolated tar.gz restore rehearsal passed: $(basename "${current_archive}")"
