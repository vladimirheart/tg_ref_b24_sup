# Storage legacy purge exact-manifest and quarantine tooling

Date: 2026-08-28
Task: `01-217`

## Summary

Formalized the post-cutover legacy local-storage cleanup path after live read-only inventory and exact mapping evidence were collected on production.

## Production evidence used before this change

Production and GitHub `main` were both confirmed at `5d284ccef34a99740466a037a8d859427d8d673e` before repository changes.

The production preflight remained GREEN:

- local fallback disabled;
- core runtime healthy;
- storage cutover gate GREEN;
- client-avatar cutover audit GREEN;
- `72` dialog metadata mappings checked;
- exactly `20` reviewed known-unrecoverable dialog objects;
- `0` unexpected missing dialog objects;
- `0` metadata gaps;
- panel/client avatar gaps `0`.

Read-only local inventory evidence:

- `130` total local files / `35,882,309` bytes;
- `117` dialog-or-orphan files;
- `13` avatar files kept out of the dialog candidate scope;
- `43` duplicate relative paths.

Exact mapping evidence:

- `74` unique dialog relative paths;
- `52` exact mapped storage keys / `80` physical files / `12,703,648` bytes;
- `28` mapped duplicate keys with identical SHA-256;
- `0` different-hash mapped duplicates;
- `22` orphan paths / `37` physical files excluded;
- `0` ambiguous paths;
- `0` rediscovered known-unrecoverable entries.

No production files, database rows, or MinIO objects were modified while collecting this evidence.

## Repository changes

- Added `scripts/docker-production-storage-legacy-inventory.ps1`:
  - inventories only reviewed local roots;
  - explicitly separates/excludes unaudited domains;
  - stores evidence outside the repository;
  - has `-ValidateOnly` and no mutation path.
- Added `scripts/docker-production-storage-local-exact-mapping-audit.ps1`:
  - reruns authoritative gates;
  - uses raw Docker labels + inspect rather than `docker compose ps`;
  - matches local relative paths exactly to `chat_attachment_metadata.storage_key`;
  - verifies duplicate local copies with SHA-256;
  - blocks different-hash duplicates, ambiguous mappings, and rediscovered known-loss rows;
  - keeps orphans outside candidates;
  - emits an integrity-bound manifest with `candidate_set_sha256`, `quarantine_authorized=false`, and `physical_delete_authorized=false`.
- Added `scripts/docker-production-storage-quarantine.ps1`:
  - verifies manifest integrity, current Git commit, rollback backup, both gates, runtime health/fallback, source path, length and SHA-256;
  - uses exact `Move-Item -LiteralPath` only;
  - requires an explicit quarantine path for `-Apply`;
  - requires same-volume move semantics;
  - supports dry-run, `-ValidateOnly`, and PowerShell `-WhatIf`;
  - has no physical-delete path.
- Added source-contract coverage in `DockerProductionStorageLegacyPurgeSourceContractTest`.
- Updated the purge/rollback runbook with current evidence and the new reproducible workflow.
- Updated task `01-217` to reflect completed cutover and current purge-preparation state.

## Validation status

Static source-contract checks were prepared locally. This execution environment does not contain Windows PowerShell 5.1 or `pwsh`, so a real PowerShell parser run was not claimed here.

Required next validation is `-ValidateOnly` for the new scripts on the actual Windows PowerShell 5.1 production host after a clean fast-forward pull. The previous manual evidence is tied to `5d284c...` and must then be regenerated on the new HEAD before any quarantine action.

## Safety outcome

This change does **not** authorize quarantine and does not authorize physical deletion. The 22 observed orphan paths / 37 physical files and all unaudited domains remain outside the candidate set.

<details>
<summary><b>Original user prompt</b></summary>

Прочитай https://github.com/vladimirheart/tg_ref_b24_sup `ai-context/handoffs/2026-08-28_storage-cutover-handoff.md`, затем продолжай работу по актуальному `main`. Перед любыми изменениями сверяй живое состояние production и репозиторий через GitHub.

</details>
