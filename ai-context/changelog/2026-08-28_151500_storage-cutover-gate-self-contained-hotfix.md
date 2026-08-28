# Storage cutover gate self-contained hotfix

Date: 2026-08-28

## Production finding

The first live run of `scripts/docker-production-storage-disable-fallback.ps1` stopped before any `.env` change or runtime recreate because its pre-cutover authoritative gate invoked the legacy raw `docker-production-storage-cutover-audit.ps1` and then failed to parse `missing_metadata_rows` from that upstream process output.

No cutover mutation had occurred at the failure point: the helper runs both pre-cutover gates before backing up or editing `.env`.

## Fix

Reworked `scripts/docker-production-storage-cutover-gate.ps1` to be fully self-contained:

- removed execution/parsing dependency on `docker-production-storage-cutover-audit.ps1`;
- counts missing `chat_attachment_metadata` rows directly from PostgreSQL;
- reads attachment `storage_key` and panel `users.photo` through HEX UTF-8 transport;
- checks attachment and panel-avatar canonical objects through the existing LF-only `scripts/internal/storage-cutover-object-stat.sh` helper;
- retains exact reviewed known-unrecoverable manifest matching;
- keeps `unexpected_missing_s3_dialog_objects=0`, `missing_s3_panel_avatars=0`, `invalid_panel_avatar_refs=0` and `missing_metadata_rows=0` as hard gates;
- remains read-only: no database writes, object copies, object deletion, local-file deletion, or Compose orphan removal.

## Operator continuation

After pulling this commit, rerun PowerShell parser validation, gate `-ValidateOnly`, the live gate, and only then rerun `docker-production-storage-disable-fallback.ps1 -ValidateOnly` followed by the live helper.
