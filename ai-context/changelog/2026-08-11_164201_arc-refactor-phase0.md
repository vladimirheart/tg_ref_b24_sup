# 2026-08-11 16:42:01 - arc-refactor-phase0

## Затронутые области

- `spring-panel/src/main/java/com/example/panel/config/DatabaseMode.java`
- `spring-panel/src/main/java/com/example/panel/config/ExternalDatabaseSettings.java`
- `spring-panel/src/main/java/com/example/panel/config/ExternalDatabaseSettingsResolver.java`
- `spring-panel/src/main/java/com/example/panel/config/SqliteDataSourceConfiguration.java`
- `spring-panel/src/main/resources/application.yml`
- `spring-panel/src/test/java/com/example/panel/config/ExternalDatabaseSettingsResolverTest.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/DatabaseMode.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/ExternalDatabaseSettings.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/ExternalDatabaseSettingsResolver.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/DataSourceConfig.java`
- `java-bot/bot-core/src/main/resources/application.yml`
- `java-bot/bot-core/src/test/java/com/example/supportbot/config/ExternalDatabaseSettingsResolverTest.java`
- `docs/target-production-architecture-plan.md`
- `docs/configuration.md`
- `docs/environment_variables.md`
- `ai-context/rules/backend/05-iguana-production-storage-boundaries.md`
- `ai-context/tasks/task-list.md`

## Пользовательский промпт

> сделай задачу 01-181

## Что сделано

- Для `spring-panel` добавлен явный контракт выбора DB-режима через `APP_DB_MODE` / `app.datasource.mode` с поддержкой `auto`, `sqlite`, `postgresql` и legacy-compatible `mysql`.
- Для `spring-panel` external DB wiring теперь выбирает корректный Hibernate dialect и vendor-specific `spring.flyway.locations`, чтобы external режим не продолжал молча смотреть в SQLite migrations.
- Для `java-bot` добавлен явный `support-bot.database.mode`, и в external PostgreSQL-режиме runtime теперь принудительно отключает schema ownership через `spring.sql.init.mode=never`.
- Добавлены unit-тесты на resolver external DB settings в `java-bot`; основной код обоих модулей дополнительно проверен через `compile`.
- Создан обязательный audit-артефакт `docs/target-production-architecture-plan.md` с фиксацией текущих writers/readers, source-of-truth контуров, migration risks и phase plan для `01-181`.
- В `ai-context/rules/backend` вынесено новое durable правило про production storage boundaries Iguana, чтобы архитектурные границы не оставались только внутри task detail.
- Статус `01-181` переведён в `🟡`, так как выполнен первый implementation-срез, но сам архитектурный эпик ещё не завершён.

## Остаточный риск

- В `spring-panel` остаётся существующий несвязанный `testCompile`-долг в нескольких тестах (`ChatMessageDto`, `RmsLicenseMonitoringService`), поэтому новый panel-specific unit test пока нельзя прогнать через полный `mvn test`, хотя основной код панели компилируется.
- Legacy split между `panel_runtime.db`, `panel_identity.db`, `monitoring.db`, `bot_runtime.db`, `settings.db` и `bot-<channelId>.db` пока остаётся в runtime и потребует следующих фаз миграции по новому target plan.
