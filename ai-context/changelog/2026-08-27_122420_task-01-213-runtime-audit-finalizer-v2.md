# 01-213 - runtime audit finalizer v2

## Trigger

The previous finalizer logged:

- smoke partial state verified;
- delivered existence checks verified;
- Java source-contract strengthened;
- runbook patched;
- compose patch incorrectly skipped;
- failure: `Alertmanager service block marker missing: depends_on:`.

## Root cause

The compose helper used an `AlreadyMarker` that was searched across the entire file. The marker:

`source: ${IGUANA_SECRETS_DIR:-./config/secrets}/alertmanager-ingestion.token`

already existed in the `panel-web` service. That caused a false idempotency match for the `alertmanager` service.

## v2 strategy

No HEAD/ancestor/blob gating is used. Safety is based on target content:

1. verify the already-modified smoke, source-contract and runbook by semantic markers;
2. isolate only the compose `alertmanager` service block;
3. accept only the exact known old block or exact canonical block;
4. build and verify the canonical compose content in memory;
5. write only the compose file;
6. run PowerShell parser checks, Bash syntax, Docker Compose `config -q` when available, Maven test-compile and targeted tests;
7. automatically restore the original compose file if verification fails;
8. only after verification succeeds, append task/changelog metadata.

No git add/commit/push/reset/checkout/clean is performed.
