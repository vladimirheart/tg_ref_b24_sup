# Storage repair static-shell hotfix

Date: 2026-08-28

## Context

The first live run of `scripts/docker-production-storage-repair-mappings.ps1` returned `execution_errors=72`. The generated `/bin/sh -c` payload was corrupted by Windows/native argument quoting because PowerShell single-quoted strings contained literal `\"` sequences.

The failed repair did not copy or delete objects. A follow-up storage backfill reconfirmed the prior state: 49 canonical S3 mappings verified, 23 mappings still missing, and zero missing metadata rows.

## Fix

- Moved MinIO repair shell logic into `scripts/internal/storage-repair-mapping.sh`.
- The PowerShell repair script now invokes the helper as a file through `/bin/sh` instead of passing a multi-line command through `-c`.
- Metadata keys continue to cross the PostgreSQL/PowerShell boundary as HEX UTF-8 before decoding, preserving non-ASCII file names.
- Repair result parsing continues to use `[REPAIR_RESULT]` ASCII markers, independent of Docker Compose lifecycle output.
- Added `.gitattributes` rule forcing LF checkout for the shell helper on Windows.
- Updated the source-contract test to assert the static helper contract and no destructive operations.

## Safety

The repair helper may copy a verified local or legacy-MinIO source into the canonical S3 key. It does not update or delete database rows, delete local files, remove old MinIO objects, or use `--remove-orphans`.

Keep `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=true` until the repair and subsequent storage audits are green.
