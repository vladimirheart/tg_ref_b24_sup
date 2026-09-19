# 01-271 — UI GREEN, real SMB acceptance deferred

## Result

- Final manual Backup & recovery UI acceptance after the dark-theme token fix is GREEN.
- Final read-only runtime review is GREEN with no runtime mutation.
- Production baseline remains HEAD `2c2930e0a9b95e3a90f9b6beb6472ed26665de2e`, panel-web container `e2583fe172b61cfed810157f72e18aa05b02fdcb7e2277ce97dea0cd2c66bd3a`, image `sha256:a66bbf08f1cd66cf7b31ecb812e1ea78535273bb3b5377b0feb9a84f8050122b`.
- ops-worker, bot-runner, panel-direct and host backup runner identities are preserved.

## Deferred acceptance

- Current `C:\1C\backup` success is a local read-only probe only and remains `NOT_DR`.
- Real SMB + `credential_ref` acceptance is deliberately deferred until real destination metadata is entered through the UI and the referenced secret is configured only on the host.
- No invented SMB values, write/delete probe, production backup/restore, credential rotation or unrelated runtime mutation is part of this docs-only sync.
- Phase C is not started.
- `01-271` remains 🟡; only the user can mark it 🟢.

## Runtime evidence carried forward

- `RUNTIME_REVIEW=GREEN`
- `DR_CLASSIFICATION=NOT_DR`
- `PROBE_STATUS=success`
- `PROBE_WRITE_TEST=false`
- `PENDING_MANUAL_BACKUP=false`
- `PENDING_DESTINATION_PROBE=false`
