#!/usr/bin/env bash
set -euo pipefail

SOURCE_DIRECTORY=""
STAGING_DIRECTORY=""
REPLACE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --source-directory) SOURCE_DIRECTORY="$2"; shift 2 ;;
    --staging-directory) STAGING_DIRECTORY="$2"; shift 2 ;;
    --replace) REPLACE=1; shift ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SOURCE_ROOT="${SOURCE_DIRECTORY:-${REPO_ROOT}}"
STAGE_ROOT="${STAGING_DIRECTORY:-${REPO_ROOT}/.tmp/legacy-sqlite-import}"

[[ -d "${SOURCE_ROOT}" ]] || { echo "Legacy SQLite source directory does not exist: ${SOURCE_ROOT}" >&2; exit 1; }
SAFE_STAGE_PREFIX="${REPO_ROOT}/.tmp/"
case "${STAGE_ROOT}/" in
  "${SAFE_STAGE_PREFIX}"*) ;;
  *) echo "Staging directory must stay under ${REPO_ROOT}/.tmp" >&2; exit 1 ;;
esac

if [[ -d "${STAGE_ROOT}" ]] && [[ -n "$(find "${STAGE_ROOT}" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
  [[ "${REPLACE}" == "1" ]] || { echo "Staging directory is not empty: ${STAGE_ROOT}. Verify its manifest and pass --replace." >&2; exit 1; }
  rm -rf -- "${STAGE_ROOT}"
fi
mkdir -p "${STAGE_ROOT}/bot_databases"

entries=()
for name in panel_runtime.db panel_identity.db monitoring.db bot_runtime.db clients.db knowledge_base.db objects.db settings.db; do
  [[ -f "${SOURCE_ROOT}/${name}" ]] && entries+=("${name}")
done
if [[ -d "${SOURCE_ROOT}/bot_databases" ]]; then
  while IFS= read -r -d '' file; do entries+=("bot_databases/$(basename "${file}")"); done < <(find "${SOURCE_ROOT}/bot_databases" -maxdepth 1 -type f -name 'bot-*.db' -print0)
fi
[[ ${#entries[@]} -gt 0 ]] || { echo "No legacy SQLite files were found under ${SOURCE_ROOT}" >&2; exit 1; }

manifest="${STAGE_ROOT}/manifest.tsv"
printf 'relative_path\tsize_bytes\tsha256\n' > "${manifest}"
for relative in "${entries[@]}"; do
  source="${SOURCE_ROOT}/${relative}"
  target="${STAGE_ROOT}/${relative}"
  mkdir -p "$(dirname "${target}")"
  source_hash="$(sha256sum "${source}" | awk '{print $1}')"
  cp -- "${source}" "${target}"
  staged_hash="$(sha256sum "${target}" | awk '{print $1}')"
  [[ "${source_hash}" == "${staged_hash}" ]] || { echo "Hash mismatch while staging ${source}" >&2; exit 1; }
  printf '%s\t%s\t%s\n' "${relative}" "$(stat -c '%s' "${source}")" "${source_hash}" >> "${manifest}"
done

echo "[GREEN] Legacy SQLite staging completed without modifying source files."
echo "[RESULT] staging_directory=${STAGE_ROOT}"
echo "[RESULT] manifest=${manifest}"
echo "[RESULT] staged_files=${#entries[@]}"
