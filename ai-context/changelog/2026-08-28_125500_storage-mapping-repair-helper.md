# Storage mapping repair helper

Date: 2026-08-28

## Context

After the canonical MinIO backfill ran successfully on Windows, the live result was:

- `metadata_rows_considered=72`
- `metadata_rows_verified_s3=49`
- `missing_s3_dialog_objects=23`
- `missing_metadata_rows=0`
- database summary: `available=52`, `external=1`, `missing=20`

Three failed mappings had a local source and Cyrillic/space-containing filenames. Twenty failed mappings had no local source in the current repository roots.

A follow-up ad-hoc diagnostic was not reliable because Docker Compose lifecycle messages from stderr were merged with the command result and because direct PostgreSQL text output in the interactive Windows session mojibaked Cyrillic storage keys. Its final `canonical_objects=0` / `still_missing=72` counters must not be used as evidence about MinIO state.

## Change

Added `scripts/docker-production-storage-repair-mappings.ps1`.

The helper:

- validates the production Compose model with `-ValidateOnly` before runtime access;
- reads attachment metadata without changing database rows;
- transports `storage_key` and `legacy_attachment_ref` from PostgreSQL as HEX-encoded UTF-8 so Windows console encoding cannot corrupt object keys;
- checks the exact canonical key `<prefix>/attachments/<storage_key>`;
- if canonical is absent and the exact local source exists, copies it through the repository-wide `/workspace:ro` bind mount instead of binding one individual Windows file path;
- if canonical is still absent, checks the exact old unprefixed MinIO key `attachments/<storage_key>` and copies that object to the canonical key;
- verifies every successful copy with `mc stat`;
- never deletes local files or MinIO objects;
- never performs SQL `UPDATE` or `DELETE`;
- keeps the legacy fallback requirement in place while any mapping remains missing.

The shell command is constructed with explicit LF separators (`-join "`n"`) and result parsing uses an ASCII `[REPAIR_RESULT]` marker, so the earlier Windows CRLF and Docker Compose stderr/result-mixing failures are not reused.

## Validation contract

Added `DockerProductionStorageRepairMappingsSourceContractTest` to assert the exact-key recovery paths and prohibit database/source deletion operations.

The operator flow remains:

1. pull the updated repository;
2. run repair `-ValidateOnly`;
3. run the live repair;
4. rerun the canonical storage backfill;
5. only after the backfill reports zero canonical gaps, run cutover audits;
6. keep `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=true` until all automated and manual cutover gates pass.
