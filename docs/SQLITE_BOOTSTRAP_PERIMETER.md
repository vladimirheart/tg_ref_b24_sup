# SQLite archive/test perimeter after PostgreSQL cutover

После принятой задачи `01-228` SQLite больше не является поддерживаемым runtime mode для `spring-panel`. Canonical production storage — PostgreSQL.

## Разрешённые остаточные роли SQLite

- **Архивный one-time import/recovery** через explicit tooling и staging-копии legacy `*.db`.
- **Read-only verification** исторических источников против PostgreSQL.
- **Тестовые fixtures**.
- **Bot worker technical store** в `APP_DB_MODE=worker`: временный self-owned SQLite state для coordination/dedup без canonical business schema.

## Что больше не является поддерживаемым perimeter

- `spring-panel APP_DB_MODE=sqlite`;
- automatic `APP_DB_*` path seeding через `EnvDefaultsInitializer`;
- local/dev panel bootstrap на business SQLite;
- live secondary SQLite databases для users/clients/knowledge/objects/monitoring;
- panel-side child JDBC contract, который запускает business bot runtime через SQLite.

## Архивный import boundary

Сохраняются до отдельного принятого purge/cleanup шага:

- `docker-compose.legacy-sqlite-import.yml`;
- `scripts/stage-legacy-sqlite-import.ps1` / `.sh`;
- `scripts/verify-legacy-sqlite-import.ps1`;
- backend-owned legacy import/recovery services и ledger;
- исходные legacy DB/archive evidence, если они ещё нужны для rollback/audit.

Normal production compose не должен подключать эти sources к live Java roles. Запуск archive import — отдельная осознанная операция, а не startup helper.
