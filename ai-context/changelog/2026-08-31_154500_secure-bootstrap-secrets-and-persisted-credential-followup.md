# 2026-08-31 15:45:00 - secure bootstrap secrets and persisted credential follow-up

## User prompt

> посмотри по последнему изменению репо - там задача незавершённая есть - доделай её

## Summary

- Довёл незавершённый хвост после последнего bootstrap-рефакторинга: fresh first-run теперь генерирует production-like секреты для PostgreSQL, RabbitMQ, Redis, object storage, internal bot API, remember-me, monitoring master key и Grafana admin.
- `docker-compose.local-postgres.yml` переведён на чтение bootstrap-generated `IGUANA_POSTGRES_PASSWORD` и `IGUANA_RABBITMQ_PASSWORD`, чтобы local infra не оставалась на hardcoded `iguana`.
- Для уже существующих local bootstrap `.env` добавлен безопасный auto-heal только app-side ключей (`APP_INTERNAL_BOT_API_TOKEN`, `APP_SECURITY_REMEMBER_ME_KEY`, `MONITORING_CREDENTIALS_MASTER_KEY`) без немой ротации persisted infra credentials.
- Добавлен Bash helper `scripts/ensure-local-bootstrap-secrets.sh` и parity-вызов из `spring-panel/run-linux.sh`.
- Обновлены `.env.example`, `docs/configuration.md`, `docs/environment_variables.md`, `docs/SQLITE_BOOTSTRAP_PERIMETER.md`, а также task-контур `01-216`.
- Для оставшегося незакрытого migration-path создан follow-up `01-222`.

## Verification

- PowerShell parser check для `scripts/bootstrap-first-run.ps1` и `scripts/ensure-local-bootstrap-secrets.ps1`.
- `C:\Program Files\Git\bin\bash.exe -n` для `scripts/bootstrap-first-run.sh` и `scripts/ensure-local-bootstrap-secrets.sh`.
- targeted test: `spring-panel\mvnw.cmd -Dtest=BootstrapFirstRunSecretsSourceContractTest test`.

## Files

- `scripts/bootstrap-first-run.ps1`
- `scripts/bootstrap-first-run.sh`
- `scripts/ensure-local-bootstrap-secrets.ps1`
- `scripts/ensure-local-bootstrap-secrets.sh`
- `docker-compose.local-postgres.yml`
- `spring-panel/run-linux.sh`
- `.env.example`
- `docs/configuration.md`
- `docs/environment_variables.md`
- `docs/SQLITE_BOOTSTRAP_PERIMETER.md`
- `spring-panel/src/test/java/com/example/panel/runtime/BootstrapFirstRunSecretsSourceContractTest.java`
- `ai-context/tasks/task-details/01-216.md`
- `ai-context/tasks/task-details/01-222.md`
- `ai-context/tasks/task-list.md`
