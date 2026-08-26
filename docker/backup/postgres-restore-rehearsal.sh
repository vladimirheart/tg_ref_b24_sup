#!/bin/sh
set -eu
umask 077

destination="/backup/offhost/postgres"
success_file="${destination}/.iguana-restore-evidence.properties"
failure_file="${destination}/.iguana-restore-failure.properties"
attempt_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
succeeded=0
current_dump=""

write_failure() {
  code="$?"
  if [ "${succeeded}" -eq 1 ]; then
    return 0
  fi
  note="PostgreSQL restore rehearsal failed"
  if [ -n "${current_dump}" ]; then
    note="${note}: $(basename "${current_dump}")"
  fi
  tmp="${failure_file}.tmp.$$"
  {
    printf 'status=error\n'
    printf 'attempt_at=%s\n' "${attempt_at}"
    printf 'note=%s\n' "${note}"
  } > "${tmp}"
  mv "${tmp}" "${failure_file}"
  exit "${code}"
}
trap write_failure EXIT INT TERM

current_dump="$(find "${destination}" -maxdepth 1 -type f -name 'iguana-postgres-*.dump' | sort | tail -n 1)"
if [ -z "${current_dump}" ]; then
  echo "[ERROR] No PostgreSQL backup dump found in ${destination}" >&2
  exit 1
fi

sha_file="${current_dump}.sha256"
if [ ! -f "${sha_file}" ]; then
  echo "[ERROR] Checksum file is missing: ${sha_file}" >&2
  exit 1
fi

(
  cd "${destination}"
  sha256sum -c "$(basename "${sha_file}")"
)
pg_restore --list "${current_dump}" >/dev/null

# The target database is ephemeral/tmpfs and never points at production.
pg_restore \
  --exit-on-error \
  --no-owner \
  --no-privileges \
  --dbname="${PGDATABASE}" \
  "${current_dump}"

table_count="$(psql -Atqc "select count(*) from information_schema.tables where table_schema = 'public';" "${PGDATABASE}")"
case "${table_count}" in
  ''|*[!0-9]*)
    echo "[ERROR] Invalid restored table count: ${table_count}" >&2
    exit 1
    ;;
esac
if [ "${table_count}" -lt 5 ]; then
  echo "[ERROR] Restore drill produced too few public tables: ${table_count}" >&2
  exit 1
fi

for required_table in tickets flyway_schema_history; do
  exists="$(psql -Atqc "select case when to_regclass('public.${required_table}') is null then 0 else 1 end;" "${PGDATABASE}")"
  if [ "${exists}" != "1" ]; then
    echo "[ERROR] Required restored table is missing: ${required_table}" >&2
    exit 1
  fi
done

verified_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
tmp="${success_file}.tmp.$$"
{
  printf 'status=ok\n'
  printf 'attempt_at=%s\n' "${attempt_at}"
  printf 'verified_at=%s\n' "${verified_at}"
  printf 'note=Automated PostgreSQL restore rehearsal passed for %s; public_tables=%s\n' "$(basename "${current_dump}")" "${table_count}"
} > "${tmp}"
mv "${tmp}" "${success_file}"
rm -f "${failure_file}"
succeeded=1
trap - EXIT INT TERM

echo "[GREEN] PostgreSQL isolated restore rehearsal passed: $(basename "${current_dump}")"
