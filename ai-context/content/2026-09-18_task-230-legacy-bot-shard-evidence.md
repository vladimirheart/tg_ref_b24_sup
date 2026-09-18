# Evidence ledger — 01-230 legacy bot shard reconciliation

Date: 2026-09-18

Baseline commit: `843de433d0beb3922e7f425c4a5391f533aaaace`

Decision: `delta=0` for both reviewed legacy bot shards. No reconciliation import was executed.

## Read-only safety

- PostgreSQL writes: `false`.
- SQLite writes: `false`.
- Project file changes during forensic probes: `0`.
- `IGUANA_LEGACY_SQLITE_AUTO_IMPORT=false` guard passed before the metadata audit.

## bot-1.db

- Repository source: `bot_databases/bot-1.db`.
- Canonical marker path: `/opt/iguana/bot_databases/bot-1.db`.
- Size: `20480` bytes.
- Source modified UTC: `2026-07-07T11:55:01.3365941Z`.
- SHA-256: `15dea9332b92df7b190c1ec5dfb237aa89f22dac3da58094571582ddd91d2ee3`.
- Source == staged snapshot by size and SHA-256: `true`.
- Staged snapshot == 01-228 manifest by size and SHA-256: `true`.
- Import marker source size: `20480`.
- Import marker source modified at: `2026-07-07 11:55:01.336594+00`.
- Prior marker imported rows: `0`.
- Prior marker imported at: `2026-08-27 09:51:15.532497+00`.
- `bot_users` rows: `0`.
- `bot_chat_history` rows: `0`.
- `applications`: absent.
- `schema_audit`: present but outside the consolidation payload contract.
- Proven reconciliation delta rows: `0`.

## bot-3.db

- Repository source: `bot_databases/bot-3.db`.
- Canonical marker path: `/opt/iguana/bot_databases/bot-3.db`.
- Size: `20480` bytes.
- Source modified UTC: `2026-07-31T14:34:25.3881009Z`.
- SHA-256: `15dea9332b92df7b190c1ec5dfb237aa89f22dac3da58094571582ddd91d2ee3`.
- Source == staged snapshot by size and SHA-256: `true`.
- Staged snapshot == 01-228 manifest by size and SHA-256: `true`.
- Import marker source size: `20480`.
- Import marker source modified at: `2026-07-31 14:34:25.388101+00`.
- Prior marker imported rows: `0`.
- Prior marker imported at: `2026-08-27 09:51:15.648572+00`.
- `bot_users` rows: `0`.
- `bot_chat_history` rows: `0`.
- `applications`: absent.
- `schema_audit`: present but outside the consolidation payload contract.
- Proven reconciliation delta rows: `0`.

## Marker and coverage verification

The existing read-only 01-228 verifier completed GREEN with `changed_bot_shard_markers=0`. Critical-table coverage remained satisfied: `messages 21 -> 45`, `chat_history 250 -> 603`, `notifications 689 -> 5094`, `web_form_sessions 1 -> 1`, `chat_attachment_metadata 17 -> 107`; recovery ledger rows: `6`.

## Historical warning forensic

No matching historical `db-migrate` or local log lines are retained on the audited workstation (Docker hits: `0`, local log hits: `0`). Therefore the exact historical size/mtime value that caused the earlier warning cannot be reconstructed from retained logs.

The current implementation of `LegacyBotShardConsolidationService.warnIfShardChangedAfterImport` emits that warning when either source size or source modified time differs from the stored marker. Current evidence does not reproduce that condition and, more importantly, proves that both payload-bearing shard tables are empty.

## Closeout decision

- `reconciliation_action=none`.
- `delta_rows=0` for `bot-1.db`.
- `delta_rows=0` for `bot-3.db`.
- No new reconciliation/import implementation is justified.
- No PostgreSQL mutation, SQLite mutation, deploy, restart, migration, backfill, or external-system mutation is required for this closeout.
