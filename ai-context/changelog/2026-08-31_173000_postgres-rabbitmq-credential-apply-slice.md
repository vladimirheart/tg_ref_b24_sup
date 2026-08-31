# 2026-08-31 17:30:00 - postgres and rabbitmq credential apply slice

## User prompt

> контейнеры запустил. давай дальше

## Summary

- Продолжил `01-223` уже на живом running contour и взял следующий безопасный apply slice для persisted credentials.
- Добавил Windows-first workflow `scripts/docker-production-credential-migration-apply.ps1` для `postgresql` и `rabbitmq`.
- Workflow по умолчанию делает только dry-run; реальные изменения происходят лишь с `-Apply`.
- Для обоих компонентов реализованы: live auth discovery текущего credential, генерация нового секрета без вывода, checkpoint `.env`, live update, повторная auth verification, обновление `.env`, recreate зависимых сервисов и best-effort rollback.
- Обновил операторскую документацию: появился runbook `docs/runbooks/persisted-credential-rotation-apply.md` и навигационные ссылки на него.
- `01-223` переведена в `🟡`, а remaining coordinated contour для `redis/minio` вынесен в новую `01-224`.

## Verification

- PowerShell parser check для `scripts/docker-production-credential-migration-apply.ps1`.
- targeted test: `spring-panel\\mvnw.cmd -Dtest=PersistedCredentialRotationApplySourceContractTest test`.
- live dry-run:
  - `powershell -File scripts/docker-production-credential-migration-apply.ps1 -Component postgresql`
  - `powershell -File scripts/docker-production-credential-migration-apply.ps1 -Component rabbitmq`
- live dry-run results on `2026-08-31`:
  - PostgreSQL plan detected valid current auth and prepared `.env` sync for `IGUANA_POSTGRES_PASSWORD` + `SPRING_DATASOURCE_PASSWORD` with dependent recreate `ops-worker`, `panel-web`, `postgres-exporter`;
  - RabbitMQ plan detected valid current auth and prepared `.env` sync for `IGUANA_RABBITMQ_PASSWORD` + `SPRING_RABBITMQ_PASSWORD` with dependent recreate `ops-worker`, `panel-web`.

## Files

- `scripts/docker-production-credential-migration-apply.ps1`
- `docs/runbooks/persisted-credential-rotation-apply.md`
- `docs/CURRENT_PROJECT_DOCUMENTATION.md`
- `docs/runbooks/production-backup-recovery.md`
- `spring-panel/src/test/java/com/example/panel/runtime/PersistedCredentialRotationApplySourceContractTest.java`
- `ai-context/tasks/task-details/01-223.md`
- `ai-context/tasks/task-details/01-224.md`
- `ai-context/tasks/task-list.md`
