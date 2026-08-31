# 2026-08-31 14:14:06 — SQLite bootstrap and bot JDBC cutover

## Что изменено

- `scripts/bootstrap-first-run.ps1` и `scripts/bootstrap-first-run.sh` переведены на PostgreSQL-only first-run contract:
  - `IGUANA_BOOTSTRAP_DB_MODE` теперь поддерживает только `auto` и `postgresql`;
  - новый `.env` больше не может быть сгенерирован в `APP_DB_MODE=sqlite`;
  - удалён аварийный SQLite fallback из активного bootstrap path.
- `spring-panel` больше не формирует SQLite child JDBC contract для `java-bot`:
  - `BotRuntimeContractService` требует canonical PostgreSQL datasource для non-worker JDBC launch;
  - при `app.datasource.mode=sqlite` child JDBC contract завершается fail-closed ошибкой вместо передачи `APP_DB_BOT_RUNTIME` и `SUPPORT_BOT_DATABASE_PATH`.
- Обновлены целевые runtime/lifecycle tests под canonical PostgreSQL child-env.
- Синхронизирована документация по bootstrap и bot runtime contract.

## Проверки

- `./mvnw.cmd "-Dtest=BotRuntimeContractServiceTest,BotProcessServiceTest,BotProcessLifecycleContractTest" test` — passed.
- PowerShell parser check для `scripts/bootstrap-first-run.ps1` — passed.
- `powershell -File scripts/bootstrap-first-run.ps1 -ValidateOnly -SkipDocker` — passed.
- `C:\Program Files\Git\bin\bash.exe -n scripts/bootstrap-first-run.sh` — passed.
