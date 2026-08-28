# Legacy media local storage purge and rollback runbook

Date: 2026-08-28
Related tasks: `01-217`, `01-218`
Status: cutover complete; read-only purge inventory complete; quarantine **not executed**

## Purpose

This runbook describes the reversible cleanup of legacy local media after the production object-storage cutover. It does not authorize broad deletion of `attachments/**`, database rows, or MinIO objects.

The required sequence is:

1. verify the production/GitHub baseline;
2. re-run the authoritative read-only storage gates;
3. generate a fresh local inventory;
4. generate an exact mapping/candidate manifest;
5. review the manifest;
6. dry-run quarantine;
7. quarantine only after a separate explicit operator decision;
8. observe the live contour and keep rollback available;
9. physically delete quarantine contents only after a later explicit rollback-window closure.

## Current production cutover invariant

Before any mutating action, all of the following must remain true:

- production runs with `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=false`;
- `ops-worker`, `panel-web`, and `panel-direct` are running and healthy;
- `scripts/docker-production-storage-cutover-gate.ps1` is GREEN;
- `scripts/docker-production-client-avatar-cutover-audit.ps1` is GREEN;
- `missing_metadata_rows=0`;
- `unexpected_missing_s3_dialog_objects=0`;
- `stale_known_unrecoverable_entries=0`;
- `missing_s3_panel_avatars=0` and `invalid_panel_avatar_refs=0`;
- `missing_s3_client_avatars=0`;
- the known-unrecoverable set still matches `ai-context/storage-known-unrecoverable-dialog-attachments.json` exactly;
- the post-cutover UI/media smoke remains successful;
- the cutover `.env.storage-cutover-*.bak` rollback backup remains readable.

The authoritative service check on this Windows production host is raw Docker label discovery plus `docker inspect`. Do not treat `docker compose ps` as authoritative contour state.

## Explicit exclusions

The following remain outside purge/quarantine scope until dedicated audits prove them safe:

- canonical MinIO objects under `iguana/...`;
- legacy/unprefixed MinIO objects;
- PostgreSQL rows and attachment metadata;
- the 20 reviewed known-unrecoverable attachment records;
- `knowledge_base/**`;
- `passport_photos/**`;
- `forms/**`;
- panel/client avatar local files unless a dedicated candidate audit is added;
- arbitrary orphan files without an exact authoritative metadata mapping;
- ambiguous mappings;
- any local path outside the reviewed legacy roots.

Never create placeholder or empty MinIO objects to make a candidate appear safe.

## Reviewed legacy roots

Only these roots are currently valid inventory sources:

- `attachments/**`;
- `java-bot/attachments/**`.

They are inventory sources, not whole-directory move/delete targets.

## Read-only evidence captured on 2026-08-28

The first manual read-only inventory and exact mapping audit were completed on production commit `5d284ccef34a99740466a037a8d859427d8d673e`.

Observed local inventory:

- total local files: `130`;
- total bytes: `35,882,309` (`34.22 MiB`);
- `dialog-or-orphan-review`: `117` files;
- separately-audited avatar scope: `13` files;
- duplicate relative paths across legacy roots: `43`.

Exact dialog mapping audit:

- unique dialog relative paths: `74`;
- current attachment metadata rows: `72`;
- reviewed known-unrecoverable rows: `20`;
- exact mapped storage keys: `52`;
- exact mapped physical local files: `80`;
- exact mapped candidate bytes: `12,703,648`;
- duplicate mapped keys with identical SHA-256: `28`;
- duplicate mapped keys with different SHA-256: `0`;
- orphan unique paths: `22`;
- orphan physical files: `37`;
- ambiguous paths: `0`;
- reviewed known-unrecoverable rows rediscovered locally: `0`.

The generated candidate manifest had `quarantine_authorized=false` and was read-only evidence only.

**Important:** that evidence is tied to commit `5d284c...`. Once this runbook/tooling commit lands and production pulls the new `main`, the old evidence is intentionally stale and must not be used for mutation. Regenerate inventory and mapping evidence on the new production HEAD.

## Phase 0 - synchronize and record rollback evidence

Before live work:

```powershell
git status --short
git pull --ff-only origin main
git rev-parse HEAD
```

Stop if the working tree is dirty or the fast-forward pull fails.

Record:

1. current Git commit;
2. `.env` value of `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED`;
3. exact cutover backup path;
4. current runtime service health/replica evidence using Docker labels + inspect;
5. GREEN outputs from both storage gates;
6. current manual UI/media smoke confirmation.

## Phase 1 - validate scripts and re-run authoritative gates

On the production Windows PowerShell 5.1 host, first run parser/static validation:

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass `
    -File ".\scripts\docker-production-storage-legacy-inventory.ps1" `
    -ValidateOnly
```

The exact mapping and quarantine scripts also expose `-ValidateOnly`, but they require an evidence/manifest path respectively.

Then re-run both authoritative read-only gates:

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass `
    -File ".\scripts\docker-production-storage-cutover-gate.ps1"

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass `
    -File ".\scripts\docker-production-client-avatar-cutover-audit.ps1"
```

Stop immediately if either gate is not GREEN.

## Phase 2 - generate a fresh read-only local inventory

Use the repository script instead of an ad-hoc directory walk:

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass `
    -File ".\scripts\docker-production-storage-legacy-inventory.ps1"
```

By default, evidence is written outside the repository under:

```text
<repo-parent>\iguana-legacy-storage-inventory\YYYYMMDD-HHmmss\
```

The script:

- reads only `attachments/**` and `java-bot/attachments/**`;
- separates `avatars` for another audit;
- excludes `knowledge_base`, `passport_photos`, and `forms`;
- classifies remaining files as `dialog-or-orphan-review`;
- records exact full/relative paths, lengths and timestamps;
- refuses to place evidence inside the repository;
- does not modify legacy files, PostgreSQL or MinIO.

## Phase 3 - build the exact dialog candidate manifest

Validate first:

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass `
    -File ".\scripts\docker-production-storage-local-exact-mapping-audit.ps1" `
    -EvidenceDirectory "<fresh-evidence-directory>" `
    -ValidateOnly
```

Then run the read-only live audit:

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass `
    -File ".\scripts\docker-production-storage-local-exact-mapping-audit.ps1" `
    -EvidenceDirectory "<fresh-evidence-directory>"
```

Candidate eligibility is intentionally strict:

- current Git HEAD must equal the inventory evidence commit;
- both authoritative gates must pass again;
- `panel-web` must have local fallback disabled;
- a local relative path must map to exactly one `chat_attachment_metadata.storage_key`;
- the row must not be one of the 20 known-unrecoverable rows;
- its canonical runtime object must remain covered by the GREEN cutover gate;
- duplicate copies of one relative path must have identical SHA-256;
- any different-hash duplicate blocks the audit;
- any ambiguous mapping blocks the audit;
- any previously known-unrecoverable row that suddenly has a local source blocks the audit;
- orphans are reported but never added to the candidate manifest.

The output candidate manifest contains an integrity hash `candidate_set_sha256` and is created with:

```text
quarantine_authorized=false
physical_delete_authorized=false
```

Changing candidate entries after the audit invalidates the integrity hash.

## Phase 4 - review and dry-run quarantine

Do not edit candidate entries. If the operator explicitly approves quarantine, make a separately retained reviewed copy of the exact manifest and change **only**:

```text
quarantine_authorized=true
```

Keep:

```text
physical_delete_authorized=false
```

First validate the reviewed copy:

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass `
    -File ".\scripts\docker-production-storage-quarantine.ps1" `
    -ManifestPath "<reviewed-manifest.json>" `
    -ValidateOnly
```

Then perform a non-mutating full dry run using the exact quarantine path intended for the real move:

```powershell
$quarantine = "C:\Users\SinicinVV\git_h\iguana-legacy-storage-quarantine\<timestamp>"

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass `
    -File ".\scripts\docker-production-storage-quarantine.ps1" `
    -ManifestPath "<reviewed-manifest.json>" `
    -QuarantineRoot $quarantine
```

The helper rechecks current HEAD, rollback backup, both storage gates, runtime service health, fallback state, each source path, size and SHA-256. It refuses paths outside the two reviewed roots. `-Apply` additionally requires an explicit quarantine root on the same Windows volume as every source file.

An additional PowerShell `-WhatIf` rehearsal is available only after the reviewed manifest has `quarantine_authorized=true`:

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass `
    -File ".\scripts\docker-production-storage-quarantine.ps1" `
    -ManifestPath "<reviewed-manifest.json>" `
    -QuarantineRoot $quarantine `
    -Apply `
    -WhatIf
```

Review every planned source/destination path. No real quarantine is authorized by this runbook update itself.

## Phase 5 - quarantine execution

The first real mutation is an exact manifest-driven move, never deletion. Execute it only after a separate explicit operator decision.

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass `
    -File ".\scripts\docker-production-storage-quarantine.ps1" `
    -ManifestPath "<reviewed-manifest.json>" `
    -QuarantineRoot $quarantine `
    -Apply
```

The helper uses only exact `Move-Item -LiteralPath` operations and preserves root name plus relative path under quarantine. It contains no physical-delete path.

Never use broad `Remove-Item -Recurse`, `rm -rf`, wildcard deletion, directory-wide move, or whole-root rename.

## Phase 6 - post-quarantine observation

After quarantine:

1. keep `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=false`;
2. confirm `ops-worker`, `panel-web`, and `panel-direct` remain healthy;
3. re-run both read-only storage gates;
4. repeat manual UI/media smoke for representative dialog image/document/PDF/video and avatars;
5. inspect runtime logs for new object-not-found/storage errors;
6. retain the reviewed manifest and quarantine path as rollback evidence.

If any regression appears, restore exact manifest paths from quarantine. Do not restore by wildcard.

## Phase 7 - rollback

### Rollback A - quarantine only

Move each quarantined file back to its original manifest source path, preserving exact paths. Then rerun both gates and manual UI/media smoke.

### Rollback B - compatibility fallback

Use only if a production regression requires it:

1. restore the known-good cutover `.env` backup or explicitly set `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=true`;
2. clear any process-level override;
3. targeted-recreate only `ops-worker` and `panel-web`, preserving replica counts;
4. do not recreate PostgreSQL, MinIO, RabbitMQ, Redis, `panel-direct`, bots, or observability solely for this rollback;
5. verify the runtime containers contain fallback `true`;
6. rerun storage gates and UI/media smoke.

Do not use `--remove-orphans`.

## Phase 8 - physical deletion

Physical deletion is a separate future operator decision. It is forbidden until the rollback window is explicitly closed and recovery evidence is accepted.

Only reviewed quarantine contents may ever be considered for deletion. Never delete source roots broadly, and never include the 22 observed orphan paths (or any future orphans) without a dedicated audit.

## Stop conditions

Stop immediately if any of these occurs:

- Git working tree is dirty or production HEAD differs from the evidence manifest;
- a storage gate is not GREEN;
- local fallback is enabled unexpectedly;
- the rollback backup is missing;
- a new missing canonical S3 object appears outside the reviewed known-unrecoverable set;
- a known-unrecoverable manifest entry becomes stale;
- a known-unrecoverable row is rediscovered as a local source;
- duplicate local paths have different SHA-256;
- a candidate has zero or multiple authoritative metadata mappings;
- a source file changed size or SHA-256 since manifest generation;
- an orphan or unaudited domain enters the candidate set;
- quarantine is inside a source root/repository or on another volume;
- runtime health is degraded;
- the operator has not explicitly authorized the current mutation phase.

## Completion evidence

A completed quarantine/purge change record must retain:

- fresh inventory evidence;
- exact mapping report;
- orphan report;
- candidate manifest and `candidate_set_sha256`;
- reviewed quarantine-authorized manifest;
- quarantine location;
- pre/post quarantine gate outputs;
- runtime fallback/health evidence;
- manual UI/media smoke confirmation;
- rollback-window closure confirmation before any physical deletion;
- exact count/size/list of any later physically deleted quarantine files.
