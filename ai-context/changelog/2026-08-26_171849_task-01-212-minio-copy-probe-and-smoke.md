# 01-212 MinIO recursive copy empirical repair v2.1

## Trigger

The user reran the 01-212 sanity finalizer. All BOM, PowerShell parser, POSIX shell, Compose, Maven and git diff checks were GREEN, then MinIO backup failed with:

mc: <ERROR> Unable to prepare URL for copying. Unable to guess the type of copy operation.

The user had explicitly requested stricter verification after previous repair iterations.

## Method

No recursive copy syntax was patched speculatively. The finalizer created an isolated two-object bucket, verified both objects from a fresh backup container, tested pinned mc recursive-copy source forms, and required exact file layout plus SHA-256 equality before changing production code.

Probe results: candidate=noslash; exit=0; files=2; layout=False; sha256=False; candidate=wildcard; exit=1; files=0; layout=False; sha256=False; candidate=slash; exit=0; files=2; layout=True; sha256=True
Selected candidate: slash

## Change

- production minio-backup.sh uses the empirically validated source spelling;
- source/local object-count fail-fast remains in place;
- source-contract gains a regression guard against the failing trailing-slash form;
- runbook and task evidence record the empirical probe.

## Status

Post-patch static checks and full local backup/restore smoke are executed by the same finalizer. 01-212 remains YELLOW until real off-host DR proof is complete.

No git add/commit/push/reset/checkout/clean is performed.
## Automated verification result

RED during post-patch verification.

Failure: 01-212 full backup/restore smoke failed with exit code 1.

No git add/commit/push/reset/checkout/clean was performed. 01-212 remains YELLOW.
