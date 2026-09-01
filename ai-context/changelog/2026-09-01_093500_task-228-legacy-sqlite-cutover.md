# 2026-09-01 09:35:00 - task 01-228 legacy SQLite cutover

## User prompt

> делай

## Summary

- Added a read-only, staging-only legacy SQLite mount for `db-migrate`; web and worker runtime roles do not receive legacy database files.
- Added PowerShell and Bash staging helpers with SHA-256 evidence, a PostgreSQL coverage verifier, and a documented stage/migrate/verify/rollback workflow.
- Updated all legacy SQLite JDBC readers to open staged files with SQLite immutable read-only mode.
- Ran the migrator successfully: generic import copied 7,545 rows and critical recovery recorded six ledger entries while adding 650 rows.
- Verified PostgreSQL covers all critical `panel_runtime.db` table counts and added `01-230` for forensic review of historical bot-shard warnings before any reconciliation.
- Moved `01-228` to `🟣`; `.env` was intentionally not modified because the task explicitly forbids automatic user environment changes.

## Verification

- `docker compose -f docker-compose.production-contour.yml config -q`
- `docker compose -f docker-compose.production-contour.yml up --no-deps --force-recreate db-migrate` exited `0`.
- `powershell -ExecutionPolicy Bypass -File .\\scripts\\stage-legacy-sqlite-import.ps1 -Replace`: staged 11 files with manifest.
- `powershell -ExecutionPolicy Bypass -File .\\scripts\\verify-legacy-sqlite-import.ps1`: GREEN critical table coverage and recovery evidence.
- `spring-panel\\mvnw.cmd -Dtest=DockerProductionRoleTopologySourceContractTest,LegacySqliteImportOperationsSourceContractTest,PostgresLegacyCriticalDataRecoverySourceContractTest,LegacySqliteCompatibilityRunnersTest,LegacyBotShardConsolidationServiceTest test`: 15 tests passed.
- Live production contour roles are healthy.

## Files

- `docker-compose.production-contour.yml`
- `.env.example`
- `scripts/stage-legacy-sqlite-import.ps1`
- `scripts/stage-legacy-sqlite-import.sh`
- `scripts/verify-legacy-sqlite-import.ps1`
- `docs/runbooks/postgresql-production-contour.md`
- `spring-panel/src/main/java/com/example/panel/service/LegacySqliteImportService.java`
- `spring-panel/src/main/java/com/example/panel/service/PostgresLegacyCriticalDataRecoveryService.java`
- `spring-panel/src/main/java/com/example/panel/service/LegacyBotShardConsolidationService.java`
- `spring-panel/src/test/java/com/example/panel/runtime/DockerProductionRoleTopologySourceContractTest.java`
- `spring-panel/src/test/java/com/example/panel/runtime/LegacySqliteImportOperationsSourceContractTest.java`
- `spring-panel/src/test/java/com/example/panel/service/PostgresLegacyCriticalDataRecoverySourceContractTest.java`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-228.md`
- `ai-context/tasks/task-details/01-229.md`
- `ai-context/tasks/task-details/01-230.md`
