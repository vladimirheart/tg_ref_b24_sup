#!/bin/sh
set -eu
umask 077

destination="/backup/offhost/packages/files"
requested="${IGUANA_BACKUP_RESTORE_COMPONENTS:-shared-config}"
success_file="${destination}/.iguana-restore-evidence.properties"
failure_file="${destination}/.iguana-restore-failure.properties"
attempt_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
succeeded=0
current_archive=""
work="/restore-work/package"

write_failure() {
  code="$?"
  if [ "${succeeded}" -eq 1 ]; then
    return 0
  fi
  note="File restore rehearsal failed"
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

is_file_component() {
  case "$1" in
    shared-config|templates|static-js|static-css) return 0 ;;
    *) return 1 ;;
  esac
}

required=""
old_ifs="${IFS}"
IFS=','
for component in ${requested}; do
  IFS="${old_ifs}"
  component="$(printf '%s' "${component}" | tr -d '[:space:]')"
  if is_file_component "${component}"; then
    if [ -z "${required}" ]; then required="${component}"; else required="${required},${component}"; fi
  fi
  IFS=','
done
IFS="${old_ifs}"

[ -n "${required}" ] || { echo "[ERROR] files-restore-rehearsal invoked without file components" >&2; exit 1; }

mkdir -p "${destination}"
for sidecar in $(find "${destination}" -maxdepth 1 -type f -name 'iguana-files-*.tar.gz.components' | sort -r); do
  archive="${sidecar%.components}"
  [ -f "${archive}" ] || continue
  available="$(cat "${sidecar}")"
  ok=1
  IFS=','
  for component in ${required}; do
    IFS="${old_ifs}"
    case ",${available}," in
      *",${component},"*) ;;
      *) ok=0 ;;
    esac
    IFS=','
  done
  IFS="${old_ifs}"
  if [ "${ok}" -eq 1 ]; then
    current_archive="${archive}"
    break
  fi
done

if [ -z "${current_archive}" ]; then
  echo "[ERROR] No file tar.gz package contains requested components: ${required}" >&2
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
[ -f "${work}/checksums.sha256" ] || { echo "[ERROR] checksums.sha256 missing from file package" >&2; exit 1; }
[ -f "${work}/components.txt" ] || { echo "[ERROR] components.txt missing from file package" >&2; exit 1; }
[ -f "${work}/manifest.json" ] || { echo "[ERROR] manifest.json missing from file package" >&2; exit 1; }
(
  cd "${work}"
  sha256sum -c checksums.sha256
)

IFS=','
for component in ${required}; do
  IFS="${old_ifs}"
  [ -d "${work}/payload/${component}" ] || { echo "[ERROR] Requested component missing from extracted package: ${component}" >&2; exit 1; }
  IFS=','
done
IFS="${old_ifs}"

file_count="$(find "${work}/payload" -type f | wc -l | tr -d '[:space:]')"
verified_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
tmp="${success_file}.tmp.$$"
{
  printf 'status=ok\n'
  printf 'attempt_at=%s\n' "${attempt_at}"
  printf 'verified_at=%s\n' "${verified_at}"
  printf 'note=Automated file tar.gz restore rehearsal passed for %s; requested=%s; files=%s\n' "$(basename "${current_archive}")" "${required}" "${file_count}"
} > "${tmp}"
mv "${tmp}" "${success_file}"
rm -f "${failure_file}"
rm -rf "${work}"
succeeded=1
trap - EXIT INT TERM

echo "[GREEN] File isolated tar.gz restore rehearsal passed: $(basename "${current_archive}"); requested=${required}; files=${file_count}"
