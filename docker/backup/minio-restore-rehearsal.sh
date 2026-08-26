#!/bin/sh
set -eu
umask 077

destination="/backup/offhost/packages/minio"
success_file="${destination}/.iguana-restore-evidence.properties"
failure_file="${destination}/.iguana-restore-failure.properties"
attempt_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
succeeded=0
current_archive=""
work="/tmp/iguana-minio-restore-$$"

write_failure() {
  code="$?"
  if [ "${succeeded}" -eq 1 ]; then
    return 0
  fi
  note="MinIO restore rehearsal failed"
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
current_archive="$(find "${destination}" -maxdepth 1 -type f -name 'iguana-minio-*.tar.gz' | sort | tail -n 1)"
if [ -z "${current_archive}" ]; then
  echo "[ERROR] No MinIO tar.gz recovery package found in ${destination}" >&2
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
[ -f "${work}/checksums.sha256" ] || { echo "[ERROR] checksums.sha256 missing from MinIO package" >&2; exit 1; }
[ -f "${work}/manifest.json" ] || { echo "[ERROR] manifest.json missing from MinIO package" >&2; exit 1; }
(
  cd "${work}"
  sha256sum -c checksums.sha256
)

source_count="$(find "${work}/objects" -type f | wc -l | tr -d '[:space:]')"

attempt=0
until mc alias set restore http://minio-restore-target:9000 restore-drill restore-drill-secret-only >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "${attempt}" -ge 60 ]; then
    echo "[ERROR] Timed out waiting for isolated MinIO restore target" >&2
    exit 1
  fi
  sleep 2
done

mc mb --ignore-existing "restore/iguana-restore-drill" >/dev/null
if [ "${source_count}" -gt 0 ]; then
  mc mirror "${work}/objects" "restore/iguana-restore-drill"
fi

target_count="$(mc ls --recursive --json "restore/iguana-restore-drill" | grep -c '"type"[[:space:]]*:[[:space:]]*"file"' || true)"
if [ "${source_count}" != "${target_count}" ]; then
  echo "[ERROR] MinIO restore object count mismatch: source=${source_count}, target=${target_count}" >&2
  exit 1
fi

first_file="$(find "${work}/objects" -type f | head -n 1 || true)"
if [ -n "${first_file}" ]; then
  relative="${first_file#${work}/objects/}"
  expected_sha="$(sha256sum "${first_file}" | awk '{print $1}')"
  actual_sha="$(mc cat "restore/iguana-restore-drill/${relative}" | sha256sum | awk '{print $1}')"
  [ "${expected_sha}" = "${actual_sha}" ] || { echo "[ERROR] MinIO restored sample checksum mismatch: ${relative}" >&2; exit 1; }
fi

mc cp "${work}/restore-sentinel.txt" "restore/iguana-restore-drill/.iguana-restore-sentinel.txt" >/dev/null
expected_sentinel_sha="$(sha256sum "${work}/restore-sentinel.txt" | awk '{print $1}')"
actual_sentinel_sha="$(mc cat "restore/iguana-restore-drill/.iguana-restore-sentinel.txt" | sha256sum | awk '{print $1}')"
[ "${expected_sentinel_sha}" = "${actual_sentinel_sha}" ] || { echo "[ERROR] MinIO package sentinel checksum mismatch" >&2; exit 1; }

verified_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
tmp="${success_file}.tmp.$$"
{
  printf 'status=ok\n'
  printf 'attempt_at=%s\n' "${attempt_at}"
  printf 'verified_at=%s\n' "${verified_at}"
  printf 'note=Automated MinIO tar.gz restore rehearsal passed for %s; objects=%s\n' "$(basename "${current_archive}")" "${source_count}"
} > "${tmp}"
mv "${tmp}" "${success_file}"
rm -f "${failure_file}"
rm -rf "${work}"
succeeded=1
trap - EXIT INT TERM

echo "[GREEN] MinIO isolated tar.gz restore rehearsal passed: $(basename "${current_archive}"); objects=${source_count}"
