# 01-212 portable recovery runtime resume v1.2

## Trigger

The first portable-runtime apply stopped after writing the tar.gz runtime files because the admin-policy v2 had normalized `scripts/docker-production-up.sh` to Windows CRLF while the v1 helper used an LF-only exact multiline patch anchor. Resume v1.1 then stopped before changing repository files because it incorrectly required the literal `IGUANA_BACKUP_CRITICAL_ENABLED` marker even though the PowerShell runner intentionally builds `IGUANA_BACKUP_${Prefix}_ENABLED` dynamically.

## Repair

- do not regenerate the already-written portable recovery runtime;
- validate the PowerShell policy runner by its dynamic Prefix lookup and CRITICAL/FULL execution calls instead of a nonexistent literal environment-variable marker;
- verify all expected partial-state runtime markers first;
- patch the production-up Bash failure-domain guard semantically by lines, independent of CRLF/LF;
- finish backup-readiness managed path migration and files monitor registration;
- add scheduler-state ignores, task evidence and runbook;
- run PowerShell parser, shell syntax, Compose, Maven, diff-check and the full seeded+empty-MinIO Docker smoke.

No git add/commit/push/reset/checkout/clean is performed.
