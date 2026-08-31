# 2026-08-31 16:45:00 - persisted credential status helper slice

## User prompt

> продолжи

## Summary

- Начал практическую реализацию `01-222` с безопасного non-mutating slice вместо слепой авторотации existing volumes.
- Добавил helper-скрипты `scripts/docker-production-credential-migration-status.ps1` и `scripts/docker-production-credential-migration-status.sh`, которые классифицируют `postgresql`, `rabbitmq`, `redis`, `minio`, `grafana` в `fresh` / `ready` / `migration_required`.
- Helper не пишет в `.env`, не перезапускает сервисы и не меняет persisted state; он только проверяет наличие volumes, non-default secrets и live/runtime credential alignment.
- Для PostgreSQL/RabbitMQ/Redis/Grafana добавлена реальная live verification, для MinIO — безопасная сверка configured credentials с live runtime env контейнера.
- Добавлен runbook `docs/runbooks/persisted-credential-migration-status.md` и ссылки на него из общей документации и backup/rotation раздела.
- `01-222` переведена в `🟡`, а remaining apply-path вынесен в новую `01-223`.

## Verification

- PowerShell parser check для `scripts/docker-production-credential-migration-status.ps1`.
- `C:\Program Files\Git\bin\bash.exe -n` для `scripts/docker-production-credential-migration-status.sh`.
- targeted test: `spring-panel\mvnw.cmd -Dtest=PersistedCredentialMigrationStatusSourceContractTest test`.
- live read-only helper run: `powershell -File scripts/docker-production-credential-migration-status.ps1 -Json` -> `overall_status=migration_required`, `migration_required=5`.

## Files

- `scripts/docker-production-credential-migration-status.ps1`
- `scripts/docker-production-credential-migration-status.sh`
- `docs/runbooks/persisted-credential-migration-status.md`
- `docs/runbooks/production-backup-recovery.md`
- `docs/CURRENT_PROJECT_DOCUMENTATION.md`
- `spring-panel/src/test/java/com/example/panel/runtime/PersistedCredentialMigrationStatusSourceContractTest.java`
- `ai-context/tasks/task-details/01-222.md`
- `ai-context/tasks/task-details/01-223.md`
- `ai-context/tasks/task-list.md`
