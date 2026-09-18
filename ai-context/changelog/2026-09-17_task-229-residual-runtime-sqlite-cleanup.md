# 2026-09-17 - task 01-229 residual runtime SQLite cleanup

## Why

The user-acceptance audit after the UI/reference closeout found remaining SQLite-specific behavior in normal spring-panel production repositories. This corrective slice closes that residual perimeter before acceptance.

## Changes

- Removed SQLITE_BUSY / SQLITE_BUSY_SNAPSHOT retry loops from normal monitoring repositories.
- Collapsed monitoring timestamp writes to the canonical PostgreSQL TIMESTAMP WITH TIME ZONE binding path.
- Removed SQLite last_insert_rowid() generated-key fallbacks from repository and dialog reply writes.
- Removed the SQLite julianday() retention branch from monitoring_check_history cleanup.
- Kept the generic telemetry epoch-millis parser fallback but removed its stale SQLite-specific description.
- Migrated the directly coupled monitoring-history and bot-runtime test fixtures to H2 PostgreSQL mode.

## Validation

- Exact baseline HEAD/origin/main and blob guards.
- Detached-worktree transformation rehearsal.
- Residual SQLite compatibility semantic grep gates on the touched normal-runtime files.
- Maven compile and targeted tests for repository, dialog reply and bot runtime behavior.
- Exact changed-file set and git diff --check.

## Safety

- No stage/commit/push.
- No deploy/restart.
- No DB migration/backfill/import.
- No external-system mutation.
- Task 01-229 remains purple until repeat user acceptance.
