# Legacy media local storage purge and rollback runbook

Date: 2026-08-28
Related tasks: `01-217`, `01-218`
Status: prepared, **not executed**

## Purpose

This runbook describes the manual, reversible final cleanup of legacy local media after the production object-storage cutover has already completed successfully.

The runbook is intentionally conservative. It does **not** authorize broad deletion of `attachments/**`, does not delete database rows, and does not delete any MinIO objects.

## Current cutover invariant

Before this runbook may be used for any mutating action, all of the following must remain true:

- production runs with `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=false`;
- `ops-worker` and `panel-web` are healthy with that exact runtime environment value;
- `scripts/docker-production-storage-cutover-gate.ps1` is GREEN;
- `scripts/docker-production-client-avatar-cutover-audit.ps1` is GREEN;
- `missing_metadata_rows=0`;
- `unexpected_missing_s3_dialog_objects=0`;
- `stale_known_unrecoverable_entries=0`;
- `missing_s3_panel_avatars=0` and `invalid_panel_avatar_refs=0`;
- `missing_s3_client_avatars=0`;
- the reviewed known-unrecoverable attachment set still matches `ai-context/storage-known-unrecoverable-dialog-attachments.json` exactly;
- post-cutover manual UI/media smoke has been completed successfully.

A full panel shutdown is not required merely to preserve this invariant. The cutover is designed to run on the live production contour with only targeted `ops-worker` / `panel-web` recreation when the fallback flag changes.

## Explicit exclusions

The following are outside the purge scope unless a future dedicated audit explicitly proves them safe:

- canonical MinIO objects under the runtime prefix `iguana/...`;
- legacy/unprefixed MinIO objects such as `attachments/...` and `avatars/...`;
- PostgreSQL rows or attachment metadata;
- the 20 reviewed known-unrecoverable records themselves;
- `knowledge_base` files without a dedicated canonical-object audit;
- `passport_photos` without a dedicated canonical-object audit;
- `forms` / web-form files without a dedicated canonical-object audit;
- arbitrary orphan files for which no authoritative metadata/object mapping exists;
- any local path outside the explicitly reviewed legacy roots.

Do not create placeholder/empty MinIO objects to make a purge candidate appear safe.

## Known legacy roots to inventory

The current migration history includes at least these local roots:

- repository `attachments/**`;
- `java-bot/attachments/**`.

These roots are **inventory sources**, not deletion targets as a whole.

## Phase 0 - record rollback evidence

Before any quarantine or purge action, record:

1. current Git commit;
2. current `.env` value of `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED`;
3. the exact `.env.storage-cutover-*.bak` file created by the successful cutover;
4. current `ops-worker` and `panel-web` replica counts;
5. current GREEN output from both cutover audits;
6. current manual UI/media smoke confirmation.

The cutover backup must remain readable for the entire rollback window.

## Phase 1 - re-run read-only gates

Run before generating purge candidates:

```powershell
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass `
    -File ".\scripts\docker-production-storage-cutover-gate.ps1"

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass `
    -File ".\scripts\docker-production-client-avatar-cutover-audit.ps1"
```

Stop immediately if either gate is not GREEN.

## Phase 2 - build a read-only local inventory

Inventory the local roots without moving or deleting anything:

```powershell
$roots = @(
    ".\attachments",
    ".\java-bot\attachments"
) | Where-Object { Test-Path -LiteralPath $_ }

$inventory = foreach ($root in $roots) {
    $resolvedRoot = (Resolve-Path -LiteralPath $root).Path
    Get-ChildItem -LiteralPath $resolvedRoot -File -Recurse -Force | ForEach-Object {
        [pscustomobject]@{
            Root = $resolvedRoot
            FullName = $_.FullName
            RelativePath = $_.FullName.Substring($resolvedRoot.Length).TrimStart('\')
            Length = $_.Length
            LastWriteTimeUtc = $_.LastWriteTimeUtc
        }
    }
}

$inventory | Sort-Object Root, RelativePath | Format-Table -AutoSize
```

Save the resulting inventory as operator evidence before continuing.

## Phase 3 - candidate eligibility rules

A local file may enter a purge-candidate manifest only when all relevant checks are true.

### Dialog attachment candidate

- the file maps to one exact `chat_attachment_metadata` row/storage key;
- that metadata row is not one of the reviewed known-unrecoverable records;
- the exact canonical runtime object `iguana/attachments/<storage_key>` exists in MinIO;
- the authoritative cutover gate still reports zero unexpected missing objects;
- the candidate is not also required by another local-only feature.

### Panel/operator avatar candidate

- the current panel avatar reference is valid;
- the canonical `iguana/avatars/...` object exists;
- `missing_s3_panel_avatars=0` and `invalid_panel_avatar_refs=0` remain true.

### Client avatar candidate

- the canonical runtime avatar object exists for the corresponding client history;
- `missing_s3_client_avatars=0` remains true.

### Other domains

Do **not** include `knowledge_base`, `passport_photos`, `forms`, or other domains until a dedicated read-only audit for that domain has been implemented and run GREEN.

## Phase 4 - manifest-only quarantine

The first mutating step is quarantine, not deletion.

Requirements:

- use an explicit, reviewed candidate manifest;
- move only files listed in that manifest;
- preserve relative paths inside the quarantine directory;
- quarantine must be outside the mounted legacy roots;
- never use broad `Remove-Item -Recurse`, `rm -rf`, directory-wide move, wildcard delete, or whole-root rename;
- first execute the planned move with `-WhatIf` or an equivalent dry-run and review every path.

Recommended quarantine naming convention:

```text
<repo-parent>\iguana-legacy-storage-quarantine\YYYYMMDD-HHmmss\...
```

No physical deletion happens in this phase.

## Phase 5 - post-quarantine observation

After quarantine:

1. keep `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=false`;
2. confirm `ops-worker`, `panel-web`, and `panel-direct` remain healthy;
3. re-run both read-only cutover gates;
4. repeat the manual UI/media smoke:
   - login;
   - dialogs list and representative dialogs;
   - representative historical image;
   - document/PDF;
   - video;
   - panel/operator avatar;
   - client avatar when a client with avatar history exists;
5. inspect runtime logs for new storage/object-not-found errors.

If any regression appears, restore quarantined files before considering physical deletion.

## Phase 6 - rollback options

### Rollback A - quarantine only

If files were only moved to quarantine, restore each manifest path to its original relative location. Do not restore files by wildcard.

Then rerun both read-only gates and manual UI/media smoke.

### Rollback B - re-enable legacy local fallback

Use this only when a production regression requires compatibility fallback.

1. restore the known-good cutover `.env` backup **or** explicitly set:

```text
APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=true
```

2. clear any process-level override in the current PowerShell session;
3. targeted-recreate only `ops-worker` and `panel-web`, preserving their replica counts;
4. do not recreate PostgreSQL, MinIO, RabbitMQ, Redis, `panel-direct`, bots, or observability solely for this rollback;
5. verify both runtime containers actually contain `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=true`;
6. re-run storage audits and manual UI/media smoke.

Do not use `--remove-orphans` for this rollback.

### Rollback C - after physical deletion

Once quarantine contents have been physically deleted, rollback is possible only from another retained backup/source. Therefore physical deletion is forbidden until the operator explicitly closes the rollback window and confirms that an independent recovery source is available or no rollback is required.

## Phase 7 - physical deletion

Physical deletion is a separate operator decision and must not be bundled with cutover/quarantine.

It is allowed only when:

- the operator explicitly closes the rollback window;
- quarantine observation is successful;
- both cutover gates remain GREEN;
- UI/media smoke remains GREEN;
- no new storage errors have appeared;
- the exact quarantine manifest is retained as evidence;
- required backup/recovery evidence has been accepted.

Delete only the reviewed quarantine contents. Never delete the source roots broadly.

After deletion, rerun both gates and the manual UI/media smoke one final time.

## Stop conditions

Stop and do not purge if any of these occur:

- a new missing S3 object outside the reviewed known-unrecoverable manifest;
- a stale known-unrecoverable manifest entry;
- any panel/client avatar gap;
- any metadata row without required storage mapping;
- a candidate cannot be mapped one-to-one to a canonical S3 object;
- a local file belongs to an unaudited domain;
- the `.env` rollback backup is missing/unreadable;
- runtime health is degraded;
- the operator has not explicitly closed the rollback window.

## Completion evidence

A completed purge change record must include:

- candidate manifest;
- quarantine manifest and location;
- pre- and post-quarantine gate outputs;
- pre- and post-delete gate outputs;
- runtime fallback state;
- manual UI/media smoke confirmation;
- rollback-window closure confirmation;
- exact list/count/size of physically deleted files.
