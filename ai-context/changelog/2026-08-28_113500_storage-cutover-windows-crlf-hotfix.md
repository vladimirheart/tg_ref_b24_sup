# Storage cutover Windows CRLF hotfix — 2026-08-28

## Symptom

Running `scripts/docker-production-storage-backfill.ps1` from Windows PowerShell 5.1 failed immediately inside the temporary `minio-init` container with:

```text
/bin/sh: line 1: set: - : invalid option
```

The Compose orphan warning for observability services was incidental and was not the failure cause.

## Root cause

PowerShell here-strings on Windows contained CRLF line endings. The multi-line shell command was passed verbatim as the argument to `/bin/sh -c`, so BusyBox `sh` received a carriage return on the `set -eu` line and parsed it as an invalid option.

The failure happened on the first shell command, before `mc alias`, `mc mirror`, mapped `mc cp`, `mc stat`, or PostgreSQL availability updates.

## Fix

Added `ConvertTo-LfLineEndings` to:

- `scripts/docker-production-storage-backfill.ps1`
- `scripts/docker-production-storage-cutover-audit.ps1`
- `scripts/docker-production-client-avatar-cutover-audit.ps1`

Every multi-line shell command is now normalized with:

```powershell
$Value.Replace("`r`n", "`n").Replace("`r", "`n")
```

before being passed to `/bin/sh -c`.

The production storage source-contract test now requires this normalization and rejects direct `-c, $ShellCommand` / `-c, $shellCommand` usage.

## Safety

- No `--remove-orphans` was added. Observability containers must not be removed by the base-compose storage scripts.
- No MinIO cleanup or legacy file deletion was introduced.
- No storage fallback setting was changed.
- No runtime image rebuild is required for this script-only hotfix.

## Verification status

Fresh GitHub source was inspected after the changes and all three MinIO shell execution paths use the normalized LF command. Windows PowerShell parser/`-ValidateOnly` and live backfill still need to be rerun on the operator host after `git pull --ff-only`.
