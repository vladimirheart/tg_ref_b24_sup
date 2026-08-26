# 01-212 encoding sanity finalizer

## Trigger

User runtime evidence reported Maven test compilation failure with `illegal character: '\ufeff'` in `ProductionBackupContourSourceContractTest.java` after the MinIO remote-to-local repair.

User prompt excerpt, translated: "check more correctly".

## Scope

- remove accidental UTF-8 BOM from critical Java/shell/docs files touched by the last repairs;
- verify the already-applied MinIO remote-to-local copy markers instead of patching them again;
- parse PowerShell helpers;
- syntax-check container shell scripts;
- validate the backup Compose overlay;
- run Maven test-compile and targeted 01-194/01-212 tests;
- run the complete local Docker backup/restore smoke.

No stage/commit/push/reset/checkout/clean is performed.

## Verification failure 2026-08-26 16:44:29 +03:00

Finalizer stopped before marking local smoke GREEN.

Failure: docker compose command failed with exit code 1: run --rm --build minio-backup

