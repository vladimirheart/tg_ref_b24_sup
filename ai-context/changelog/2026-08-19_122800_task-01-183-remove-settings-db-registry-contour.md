# 2026-08-19 12:28:00 - task-01-183 remove settings-db registry contour

## User prompt

`тогда делай всё. желательно в рамках одного подхода`

## What changed

- `settings.db` удалён из active runtime/config/import contract `spring-panel`.
- `BotDatabaseRegistry` больше не:
  - bootstrap-ит `settings.db`;
  - создаёт `database_registry`, `database_links`, `bot_instances`;
  - пишет registry/link metadata в отдельный SQLite contour.
- `BotDatabaseRegistry` оставлен только как local helper для explicit legacy
  `bot-<channelId>.db` shard bootstrap.
- `DatabaseBootstrapService` больше не тянет `SettingsSqliteDataSourceProperties`,
  не вызывает `ensureSettingsSchema()` и не пишет `settings`/`bots` registry links.
- Удалены:
  - `spring-panel/src/main/java/com/example/panel/config/SettingsSqliteDataSourceProperties.java`
  - `app.datasource.settings-sqlite.*` из `spring-panel/src/main/resources/application.yml`
  - `APP_DB_SETTINGS` defaults из `EnvDefaultsInitializer`
  - legacy `settings.db` source group из `LegacySqliteImportService`
  - `SettingsSqliteDataSourceProperties` из `SecondarySqliteDataSourceConfiguration`
- Тесты обновлены под новый контракт:
  - `BotDatabaseRegistryTest`
  - `DatabaseBootstrapServiceRuntimeModeTest`
  - `EnvDefaultsInitializerTest`
- Актуальная документация и task context синхронизированы с новым состоянием:
  - `README.md`
  - `docs/environment_variables.md`
  - `docs/database-paths.md`
  - `docs/database_distribution.md`
  - `docs/SQLITE_BOOTSTRAP_PERIMETER.md`
  - `docs/target-production-architecture-plan.md`
  - `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md`
  - `docs/IGUANA_PROJECT_GUIDE.md`
  - `ai-context/tasks/task-details/01-183.md`

## Validation

- `spring-panel` targeted verification:

```powershell
./mvnw clean test "-Dtest=BotDatabaseRegistryTest,DatabaseBootstrapServiceRuntimeModeTest,EnvDefaultsInitializerTest,LegacySqliteCompatibilityRunnersTest,UsersSqliteDataSourceConfigurationTest,ObjectPassportServiceRuntimeDataSourceTest,MonitoringSqliteDataSourceConfigurationTest,ClientsServiceTest,BotRuntimeBlacklistServiceTest,ClientProfileApiControllerTest,BotRuntimeContractServiceTest,BotProcessServiceTest,BotProcessLifecycleContractTest"
```

- Result:
  - `BUILD SUCCESS`
  - `Tests run: 66, Failures: 0, Errors: 0, Skipped: 0`

## Notes

- Решение принято в сторону удаления legacy registry-модели, а не её переноса
  в PostgreSQL как отдельного metadata contour.
- Следующим логичным scope остаётся судьба `bot-<channelId>.db` shard layer и
  более широкий infra/incident block задачи `01-183`.
