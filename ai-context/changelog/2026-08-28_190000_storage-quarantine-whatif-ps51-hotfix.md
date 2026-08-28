# 2026-08-28 — storage quarantine `-Apply -WhatIf` PS5.1 hotfix

## Контекст

На production commit `35eda2d2953a4eab899c90f31fa5b586c58ff729` fresh inventory, full exact-mapping, quarantine `-ValidateOnly` и non-`-Apply` dry-run прошли успешно.

Verified candidate boundary:

- `52` exact mapped storage keys;
- `80` physical local files;
- `12,703,648` bytes;
- `28` identical duplicate-key pairs;
- `22` orphan unique paths / `37` orphan physical files excluded;
- `0` ambiguous paths;
- `0` rediscovered known-loss entries;
- candidate set SHA-256 `0ac1d1bfabf904d820cf66e10b2c5f2dad35fd5afc6b37c5e79e24b2383c43fb`.

A reviewed manifest copy was created after explicit operator approval with only `quarantine_authorized=true`; `physical_delete_authorized=false` remained unchanged. Its `-ValidateOnly` passed.

## Найденная проблема

`docker-production-storage-quarantine.ps1 -Apply -WhatIf` failed during the first source SHA-256 check under Windows PowerShell 5.1.

The script-level `$WhatIfPreference` leaked into `Get-FileHash` provider handling. PowerShell emitted a provider WhatIf operation and `Get-FileHash` did not return a normal object containing `.Hash`, leading to `PropertyNotFoundStrict`.

The failure happened during source-plan verification, before the plan completed and before the `ShouldProcess` / `Move-Item` loop. No quarantine move was reached.

## Изменения

- Added `Get-Sha256ReadOnly` in `scripts/docker-production-storage-quarantine.ps1`.
- The helper temporarily sets `$WhatIfPreference=false` only around read-only `Get-FileHash`, then restores the original preference in `finally`.
- The original invocation WhatIf state is retained for the later `ShouldProcess` move loop, so `-Apply -WhatIf` must still suppress every move.
- Storage cutover and client-avatar gates now stream output with explicit stage markers instead of buffering the whole child process output.
- Added runtime-health and manifest-source-verification progress stages.
- Updated source-contract regression checks to require the PS5.1-safe hashing behavior and streamed gate execution.
- Updated task `01-217` with the verified fresh mapping/dry-run evidence and failed WhatIf diagnosis.

## Safety

Unchanged safety contract:

- no broad move/delete;
- no PostgreSQL mutation;
- no MinIO mutation;
- no `Remove-Item`, `DELETE`, `mc rm`, or physical delete path;
- only exact manifest entries may reach the move loop;
- `quarantine_authorized=true` is still required for real `-Apply`;
- `physical_delete_authorized=true` is explicitly rejected;
- clean Git tree, matching manifest commit, rollback backup, runtime health, fallback=false, exact source path/size/SHA and same-volume quarantine remain mandatory.

## Production follow-up

Because evidence is commit-bound, pulling this hotfix makes the previous `20260828-184146` inventory/reviewed manifests stale. Production must generate fresh inventory and exact-mapping evidence on the new HEAD, create a new reviewed authorization copy, and repeat `-Apply -WhatIf` before any real quarantine decision.
