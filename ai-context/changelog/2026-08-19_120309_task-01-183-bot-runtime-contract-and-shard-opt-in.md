# 2026-08-19 12:03:09 - task-01-183 bot runtime contract and shard opt-in

- User prompt:
  `хорошо. бери в работу следующих несколько пунктов. например эти:
  java-bot всё ещё тяготеет к panel_runtime.db по default runtime contract.
  bot-<channelId>.db и registry/per-channel shard layer ещё остаются как legacy topology.`
- Scope:
  - убрать implicit default притяжение `java-bot` к `panel_runtime.db`;
  - перевести shared panel SQLite bridge для `java-bot` в explicit contract;
  - остановить automatic bootstrap per-channel `bot-<channelId>.db` shard-layer без явного opt-in;
  - обновить тесты и архитектурную документацию.

## Что изменено

- В `java-bot/bot-core/src/main/resources/application.yml` цепочка
  `support-bot.database.path` переставлена:
  - сначала `SUPPORT_BOT_DATABASE_PATH`;
  - затем `APP_DB_BOT_RUNTIME` / `APP_DB_BOT`;
  - только потом legacy `APP_DB_PANEL_RUNTIME` / `APP_DB_TICKETS`.
- `BotRuntimeContractService` теперь в SQLite compatibility mode:
  - прокидывает `APP_DB_BOT_RUNTIME` и `APP_DB_BOT`;
  - использует `SUPPORT_BOT_DATABASE_PATH` как явный shared panel-runtime bridge;
  - больше не считает `APP_DB_PANEL_RUNTIME` / `APP_DB_TICKETS` normal bot runtime contract.
- В `BotProcessProperties` добавлен guardrail
  `sqlitePerChannelShardEnabled`, а в `spring-panel/application.yml` —
  property `app.bots.sqlite-per-channel-shard-enabled` с default `false`.
- `BotDatabaseRegistry` больше не bootstrap-ит `bot-<channelId>.db`, если
  per-channel shard layer явно не включён.
- `DatabaseBootstrapService` больше не регистрирует/инициализирует per-channel
  bot shard layer по умолчанию и пропускает `bots` registry metadata, если
  shard layer выключен.
- Обновлены:
  - `docs/BOT_RUNTIME_CONTRACT.md`
  - `docs/database_distribution.md`
  - `docs/database-paths.md`
  - `docs/environment_variables.md`
  - `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md`
  - `docs/target-production-architecture-plan.md`
  - `ai-context/tasks/task-details/01-183.md`

## Проверка

- `./mvnw clean test "-Dtest=BotRuntimeContractServiceTest,BotProcessServiceTest,BotProcessLifecycleContractTest,BotDatabaseRegistryTest,DatabaseBootstrapServiceRuntimeModeTest,ClientProfileApiControllerTest"`
  - `BUILD SUCCESS`
  - `Tests run: 52, Failures: 0, Errors: 0, Skipped: 0`
- `.\\mvnw.cmd -pl bot-core clean test "-Dtest=DataSourceConfigTest,BotDatabaseRuntimeModeTest,SqliteSchemaInitializerTest,SqliteTriggerInitializerTest"`
  - `BUILD SUCCESS`
  - `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`
