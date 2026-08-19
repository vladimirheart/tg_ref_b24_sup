# 2026-08-19 11:52:59 - task-01-183 bot runtime live graph cleanup

- User prompt:
  `бери в работу следующих несколько пунктов`
- Scope:
  - убрать `bot_runtime.db` из live Spring datasource graph `spring-panel`;
  - оставить shared bot runtime только как lazy SQLite compatibility/bootstrap contour;
  - обновить runtime-boundary tests и архитектурную документацию.

## Что изменено

- `BotSqliteDataSourceConfiguration` больше не поднимает отдельные
  `botDataSource` / `botJdbcTemplate` beans и остаётся только holder'ом для
  `BotSqliteDataSourceProperties`.
- `DatabaseBootstrapService` больше не зависит от injected `botDataSource`:
  SQLite datasource для `bot_runtime.db` теперь создаётся лениво только внутри
  explicit `sqlite` runtime path.
- Добавлен `DatabaseBootstrapServiceRuntimeModeTest`, который фиксирует:
  - полный skip compatibility bootstrap в external PostgreSQL runtime;
  - создание shared `bot_runtime.db` bootstrap path только в SQLite mode.
- Обновлены:
  - `docs/database_distribution.md`
  - `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md`
  - `docs/target-production-architecture-plan.md`
  - `ai-context/tasks/task-details/01-183.md`

## Проверка

- `./mvnw clean test "-Dtest=DatabaseBootstrapServiceRuntimeModeTest,BotDatabaseRegistryTest,UsersSqliteDataSourceConfigurationTest,ObjectPassportServiceRuntimeDataSourceTest,MonitoringSqliteDataSourceConfigurationTest,ClientsServiceTest,BotRuntimeBlacklistServiceTest,ClientProfileApiControllerTest,LegacySqliteCompatibilityRunnersTest"`
  - `BUILD SUCCESS`
  - `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0`
