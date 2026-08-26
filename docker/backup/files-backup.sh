#!/bin/sh
set -eu
umask 077

destination="/backup/offhost/packages/files"
retention_days="${IGUANA_BACKUP_RETENTION_DAYS:-30}"
mode="${IGUANA_BACKUP_MODE:-critical}"
components="${IGUANA_BACKUP_COMPONENTS:-shared-config}"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
base="iguana-files-${mode}-${stamp}"
stage="/tmp/${base}.$$"
tmp_archive="${destination}/.${base}.tar.gz.tmp"
final_archive="${destination}/${base}.tar.gz"
tmp_archive_sha="${destination}/.${base}.tar.gz.sha256.tmp"
final_archive_sha="${final_archive}.sha256"
tmp_components="${destination}/.${base}.tar.gz.components.tmp"
final_components="${final_archive}.components"

mkdir -p "${destination}"
rm -rf "${stage}"
mkdir -p "${stage}/payload"

cleanup() {
  rm -rf "${stage}"
  rm -f "${tmp_archive}" "${tmp_archive_sha}" "${tmp_components}"
}
trap cleanup EXIT INT TERM

has_component() {
  case ",${components}," in
    *",$1,"*) return 0 ;;
    *) return 1 ;;
  esac
}

selected=""

append_selected() {
  if [ -z "${selected}" ]; then
    selected="$1"
  else
    selected="${selected},$1"
  fi
}

if has_component shared-config; then
  mkdir -p "${stage}/payload/shared-config"
  for file in /source/shared-config/*.json /source/shared-config/backup.properties; do
    [ -f "${file}" ] || continue
    cp "${file}" "${stage}/payload/shared-config/"
  done
  append_selected shared-config
fi

if has_component templates; then
  mkdir -p "${stage}/payload/templates"
  cp -R /source/templates/. "${stage}/payload/templates/"
  append_selected templates
fi

if has_component static-js; then
  mkdir -p "${stage}/payload/static-js"
  cp -R /source/static-js/. "${stage}/payload/static-js/"
  append_selected static-js
fi

if has_component static-css; then
  mkdir -p "${stage}/payload/static-css"
  cp -R /source/static-css/. "${stage}/payload/static-css/"
  append_selected static-css
fi

[ -n "${selected}" ] || { echo "[ERROR] files-backup was invoked without file components" >&2; exit 1; }

(
  cd "${stage}"
  : > checksums.sha256
  find payload -type f | sort | while IFS= read -r file; do
    sha256sum "${file}"
  done >> checksums.sha256
)

file_count="$(find "${stage}/payload" -type f | wc -l | tr -d '[:space:]')"
printf '%s\n' "${selected}" > "${stage}/components.txt"
printf '{"kind":"files","created_at":"%s","archive_format":"tar.gz","backup_mode":"%s","components":"%s","file_count":%s}\n' \
  "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "${mode}" "${selected}" "${file_count}" > "${stage}/manifest.json"

tar -czf "${tmp_archive}" -C "${stage}" .
tar -tzf "${tmp_archive}" >/dev/null
archive_sha="$(sha256sum "${tmp_archive}" | awk '{print $1}')"
printf '%s  %s\n' "${archive_sha}" "$(basename "${final_archive}")" > "${tmp_archive_sha}"
printf '%s\n' "${selected}" > "${tmp_components}"

mv "${tmp_archive}" "${final_archive}"
mv "${tmp_archive_sha}" "${final_archive_sha}"
mv "${tmp_components}" "${final_components}"
trap - EXIT INT TERM
rm -rf "${stage}"

find "${destination}" -maxdepth 1 -type f -mtime "+${retention_days}" \
  \( -name 'iguana-files-*.tar.gz' -o -name 'iguana-files-*.tar.gz.sha256' -o -name 'iguana-files-*.tar.gz.components' \) \
  -delete 2>/dev/null || true

echo "[GREEN] File recovery package published: ${final_archive}; components=${selected}; files=${file_count}"
