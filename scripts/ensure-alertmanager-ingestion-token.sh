#!/usr/bin/env bash
set -euo pipefail

umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SECRETS_DIR="${IGUANA_SECRETS_DIR:-${REPO_ROOT}/config/secrets}"

if [[ "${SECRETS_DIR}" != /* ]]; then
  SECRETS_DIR="${REPO_ROOT}/${SECRETS_DIR#./}"
fi

mkdir -p "${SECRETS_DIR}"
chmod 700 "${SECRETS_DIR}" || true
TOKEN_PATH="${SECRETS_DIR}/alertmanager-ingestion.token"

if [[ -f "${TOKEN_PATH}" ]]; then
  existing="$(tr -d '\r\n[:space:]' < "${TOKEN_PATH}")"
  if [[ "${#existing}" -lt 32 ]]; then
    echo "[ERROR] Existing Alertmanager ingestion token is too short/invalid: ${TOKEN_PATH}" >&2
    exit 1
  fi
  chmod 644 "${TOKEN_PATH}"
  echo "[GREEN] Alertmanager ingestion token already exists."
  echo "[INFO] Path: ${TOKEN_PATH}"
  exit 0
fi

token="$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')"
[[ "${#token}" -ge 64 ]] || {
  echo "[ERROR] Failed to generate Alertmanager ingestion token." >&2
  exit 1
}

tmp="${TOKEN_PATH}.tmp.$$"
printf '%s\n' "${token}" > "${tmp}"
chmod 644 "${tmp}"
mv "${tmp}" "${TOKEN_PATH}"

echo "[GREEN] Alertmanager ingestion token created."
echo "[INFO] Path: ${TOKEN_PATH}"
echo "[INFO] Secret value was not printed."
