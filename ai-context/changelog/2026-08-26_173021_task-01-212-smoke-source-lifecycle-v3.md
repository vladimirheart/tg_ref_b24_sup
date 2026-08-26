# 01-212 file-backed MinIO smoke source lifecycle repair

## Triggering user runtime evidence

The user ran finalize-01-212-minio-copy-probe-and-smoke-v2_1.ps1.

Observed:
- file-backed two-object MinIO probe passed with the trailing-slash source spelling;
- root and nested paths were preserved and both SHA-256 checks passed;
- PowerShell parse, POSIX sh -n, Compose config, Maven targeted tests and git diff --check passed;
- full smoke then failed in the production minio-backup job with `Unable to guess the type of copy operation`.

## Root cause boundary

The successful probe used file-backed shell scripts, but the full smoke still seeded and cleaned its MinIO bucket through dynamic Windows PowerShell -> Docker `sh -c` argument serialization. That native-argument bridge had already proven unsafe in the previous probe failure. The production recursive copy command itself was not changed because the empirical file-backed probe proved the trailing-slash source spelling.

## Change

- rewrite the smoke seed, fresh-container verification and cleanup as LF-only UTF-8/no-BOM shell files;
- validate generated shell with sh -n before execution;
- verify exactly one source object from a fresh container after a repeated minio-init dependency run;
- execute the exact production minio-backup service as a preflight before full smoke;
- verify preflight source/local counts, bucket identity and materialized smoke.txt;
- log MinIO source object count in the production backup and fail fast on an empty source;
- add regression source-contract coverage and runbook evidence.

## Status

01-212 remains YELLOW pending complete GREEN local backup/restore smoke and real off-host DR proof.

No stage/commit/push/reset/checkout/clean is performed by this finalizer.