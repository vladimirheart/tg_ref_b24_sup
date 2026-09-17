# Распределение БД в проекте Iguana

## Назначение документа

Этот документ фиксирует **текущую production storage model** после принятого PostgreSQL cutover (`01-228`) и начала удаления SQLite compatibility perimeter (`01-229`).

Он описывает runtime ownership, а не историческую физическую топологию legacy `*.db` файлов.

## Production runtime

В production единственным business/identity/monitoring datasource для `spring-panel` является внешний PostgreSQL-контур, задаваемый через:

- `APP_DB_MODE=postgresql`;
- `SPRING_DATASOURCE_URL`;
- `SPRING_DATASOURCE_USERNAME`;
- `SPRING_DATASOURCE_PASSWORD`.

Primary datasource создаёт `PanelDataSourceConfiguration`. Обычный `JdbcTemplate`, JPA и transaction manager работают поверх этого external datasource.

`usersJdbcTemplate` создаёт `UsersDataSourceConfiguration`, но это **alias на тот же primary datasource**, а не отдельная SQLite БД пользователей.

Monitoring/business/read-model контуры в production также не должны открывать отдельные SQLite-файлы как runtime storage.

## Canonical ownership

| Контур | Production owner | Runtime access |
| --- | --- | --- |
| panel/business runtime | PostgreSQL | primary JPA + primary `JdbcTemplate` |
| identity/auth | PostgreSQL | `usersJdbcTemplate` alias на primary datasource |
| monitoring | PostgreSQL | primary/monitoring runtime JDBC alias |
| bot business data | PostgreSQL / panel internal API boundary | canonical PostgreSQL contract |
| object passports | PostgreSQL | canonical panel datasource |

Это означает, что имена `panel_runtime.db`, `panel_identity.db`, `monitoring.db`, `bot_runtime.db`, `clients.db`, `knowledge_base.db`, `objects.db` больше не описывают production runtime topology.

## Legacy SQLite perimeter

Legacy SQLite-файлы допускаются только как источник для controlled import/recovery, диагностики исторических инсталляций и тестовых fixtures.

В частности:

- `docker-compose.legacy-sqlite-import.yml` и backend-owned import/recovery services могут читать staged legacy `*.db`;
- `bot-<channelId>.db` сохраняется как import-only legacy shard source;
- `IGUANA_LEGACY_SQLITE_AUTO_IMPORT` в normal production runtime должен оставаться `false`;
- legacy source files не должны монтироваться в live `panel-web` / `ops-worker` как runtime datasource;
- наличие archive/import tooling не означает поддержку `APP_DB_MODE=sqlite` для `spring-panel`.

## Java bot / worker exception

Отдельный technical SQLite store может существовать внутри изолированного bot worker/local-test contract, если он не становится business source of truth и не возвращает panel production runtime к SQLite ownership.

Этот technical worker store не следует смешивать с retired panel SQLite compatibility topology.

## Source cleanup status

После structural slice 2B live panel graph больше не содержит secondary SQLite bootstrap для clients/knowledge/objects/monitoring. `ObjectPassportService` использует только canonical primary datasource, а monitoring JDBC beans являются aliases того же primary datasource.

Оставшиеся `SqliteDataSourceProperties`, `MonitoringSqliteDataSourceProperties` и `BotSqliteDataSourceProperties` регистрируются через `LegacySqliteArchiveConfiguration` только как source-path holders для explicit archive/recovery tooling; эта конфигурация не создаёт live `DataSource`.

Structural source cleanup `01-229` завершён: panel-side SQLite runtime predicates/SQL branches удалены в slice 2C; финальный UI/reference closeout синхронизирует только пользовательские формулировки и текущие reference docs.

## Operational rule

Для production проверки ориентируйтесь на фактический datasource contract и container env, а не на наличие исторических `*.db` рядом с checkout:

```text
APP_DB_MODE=postgresql
SPRING_DATASOURCE_URL=jdbc:postgresql://...
IGUANA_LEGACY_SQLITE_AUTO_IMPORT=false
```

Повторный import, migration или backfill не выполняется автоматически только из-за наличия legacy SQLite evidence.
