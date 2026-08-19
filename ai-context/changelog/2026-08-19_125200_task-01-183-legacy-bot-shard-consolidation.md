# 2026-08-19 12:52:00 - task-01-183 - legacy bot shard consolidation

## Что сделано

- Удалён live runtime-path для per-channel SQLite shard layer:
  - удалён `spring-panel/src/main/java/com/example/panel/service/BotDatabaseRegistry.java`;
  - `DatabaseBootstrapService` больше не создаёт `bot-<channelId>.db`;
  - из `BotProcessProperties` и `application.yml` убран флаг `app.bots.sqlite-per-channel-shard-enabled`;
  - `ClientProfileApiController` больше не читает `bot-<channelId>.db` даже как compatibility probe.
- Добавлен backend-owned импорт legacy shard-данных в canonical contour:
  - новый `LegacyBotShardConsolidationService` сканирует `APP_BOT_DATABASE_DIR`, под advisory lock импортирует `bot_users`, `bot_chat_history`, `applications` и пишет import marker в `legacy_bot_shard_imports`;
  - `bot_chat_history` расширен полями `ticket_id` и `attachment_path`, чтобы legacy история не теряла контекст при переносе.
- Добавлены миграции:
  - `spring-panel/src/main/resources/db/migration/postgresql/V21__legacy_bot_shard_consolidation.sql`;
  - `spring-panel/src/main/resources/db/migration/sqlite/V42__legacy_bot_shard_consolidation.sql`;
  - `spring-panel/src/main/resources/db/migration/mysql/V18__legacy_bot_shard_consolidation.sql`.
- Обновлены targeted tests и документация под новый storage-contract.

## Проверка

- Выполнен targeted test suite:
  - `./mvnw test "-Dtest=DatabaseBootstrapServiceRuntimeModeTest,LegacyBotShardConsolidationServiceTest,LegacySqliteCompatibilityRunnersTest,EnvDefaultsInitializerTest,ObjectPassportServiceRuntimeDataSourceTest,MonitoringSqliteDataSourceConfigurationTest,UsersSqliteDataSourceConfigurationTest,ClientsServiceTest,BotRuntimeBlacklistServiceTest,BotRuntimeContractServiceTest,BotProcessServiceTest,BotProcessLifecycleContractTest"`
- Результат: `BUILD SUCCESS`.

## Эффект

- `bot-<channelId>.db` больше не является live runtime storage и не должен расти в production contour.
- Если legacy shard-файлы с данными ещё существуют, `spring-panel` теперь переводит их содержимое в canonical PostgreSQL contour вместо сохранения постоянного split-storage режима.
