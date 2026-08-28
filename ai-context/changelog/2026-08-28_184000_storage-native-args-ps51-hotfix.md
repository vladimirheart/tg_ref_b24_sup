# Storage exact-mapping / quarantine PowerShell 5.1 native-argument hotfix

## Production evidence

On production commit `1f48fb9d46daa7011a3a1c182471f17c5790d3ff` the fresh inventory was reproduced successfully:

- `inventory_files=130`
- `dialog-or-orphan-review=117`
- `separate-audit:avatars=13`
- fresh evidence directory: `C:\Users\SinicinVV\git_h\iguana-legacy-storage-inventory\20260828-182816`

The exact-mapping run then completed both read-only gates successfully:

- storage cutover gate: `attachment_mappings_checked=72`, `known_unrecoverable_dialog_objects=20`, `unexpected_missing_s3_dialog_objects=0`, `missing_metadata_rows=0`, panel avatar gaps `0`;
- client avatar audit: `missing_s3_client_avatars=0`.

The run failed only after entering `runtime-storage-contract` with:

`Expected exactly one running postgres container, found 100.`

No candidate manifest was produced and no quarantine operation was attempted.

## Root cause

Both `scripts/docker-production-storage-local-exact-mapping-audit.ps1` and `scripts/docker-production-storage-quarantine.ps1` declared the native command argument parameter as `$Args` and splatted it as `@Args`.

In Windows PowerShell 5.1, `$args` is an automatic variable. Reusing that name in this native-command helper caused the intended Docker arguments to be lost at runtime. `docker.exe` therefore emitted its help text; the helper normalized those help lines as if they were container IDs, producing the misleading count `100`.

## Change

- Rename the helper parameter to `$Arguments` in both scripts.
- Splat native arguments with `@Arguments`.
- Normalize `Container-Id` results explicitly as an array before enforcing exactly one running service container.
- Keep Docker runtime discovery label-based (`docker ps -q --filter label=...`), not `docker compose ps`.
- Add source-contract assertions that lock the safe PowerShell 5.1 parameter name and splatting form.

## Safety

- No PostgreSQL rows were changed.
- No MinIO objects were changed.
- No legacy local files were moved or deleted.
- `quarantine_authorized` remains false by default.
- Physical deletion remains unimplemented and unauthorized.

## Validation status

Static/source-contract validation is included in the repository change. Real Windows PowerShell 5.1 validation and a fresh exact-mapping run must be repeated on production after pulling the hotfix. Because inventory evidence is commit-bound, production must generate another fresh inventory on the hotfix commit before the full exact-mapping audit.
