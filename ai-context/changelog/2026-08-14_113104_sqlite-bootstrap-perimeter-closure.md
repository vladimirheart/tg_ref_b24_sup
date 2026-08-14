# 2026-08-14 11:31:04 - sqlite bootstrap perimeter closure

## Промт пользователя
- `давай дальше`

## Что сделано
- добавлен `docs/SQLITE_BOOTSTRAP_PERIMETER.md` с явной фиксацией допустимого SQLite-only слоя после PostgreSQL-first cleanup;
- обновлены `README.md` и `docs/target-production-architecture-plan.md`, чтобы perimeter был виден из основных архитектурных документов;
- в `spring-panel` и `java-bot` ключевые остаточные bootstrap-компоненты помечены как local/dev-only perimeter, а не как часть external PostgreSQL runtime;
- добавлен `spring-panel/src/test/java/com/example/panel/service/BotDatabaseRegistryTest.java`, который проверяет, что `settings.db` и per-channel bot SQLite bootstrap не активируются в external PostgreSQL-режиме;
- добавлен `java-bot/bot-core/src/test/java/com/example/supportbot/config/SqliteTriggerInitializerTest.java`, который проверяет, что SQLite trigger bootstrap не попадает в external PostgreSQL path, но продолжает работать в SQLite-режиме;
- карточка `01-181` обновлена новым фактическим остатком: readiness-часть почти сведена к финальному close-out decision.

## Затронутые файлы
- `docs/SQLITE_BOOTSTRAP_PERIMETER.md`
- `README.md`
- `docs/target-production-architecture-plan.md`
- `spring-panel/src/main/java/com/example/panel/service/BotDatabaseRegistry.java`
- `spring-panel/src/main/java/com/example/panel/service/DatabaseBootstrapService.java`
- `spring-panel/src/main/java/com/example/panel/service/MonitoringDatabaseBootstrapService.java`
- `spring-panel/src/main/java/com/example/panel/service/SqliteSchemaBootstrapSupport.java`
- `spring-panel/src/test/java/com/example/panel/service/BotDatabaseRegistryTest.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/SqliteSchemaInitializer.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/SqliteTriggerInitializer.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/config/SqliteTriggerInitializerTest.java`
- `ai-context/tasks/task-details/01-181.md`

## Проверка
- `cmd /c mvnw.cmd -q -Dtest=BotDatabaseRegistryTest test` (`spring-panel`) - passed
- `cmd /c "mvnw.cmd -q -pl bot-core -Dtest=SqliteTriggerInitializerTest,SqliteSchemaInitializerTest,ChatHistoryServiceTest test"` (`java-bot`) - passed
- `git diff --check -- README.md docs/SQLITE_BOOTSTRAP_PERIMETER.md docs/target-production-architecture-plan.md spring-panel/src/main/java/com/example/panel/service/BotDatabaseRegistry.java spring-panel/src/main/java/com/example/panel/service/DatabaseBootstrapService.java spring-panel/src/main/java/com/example/panel/service/MonitoringDatabaseBootstrapService.java spring-panel/src/main/java/com/example/panel/service/SqliteSchemaBootstrapSupport.java spring-panel/src/test/java/com/example/panel/service/BotDatabaseRegistryTest.java java-bot/bot-core/src/main/java/com/example/supportbot/config/SqliteSchemaInitializer.java java-bot/bot-core/src/main/java/com/example/supportbot/config/SqliteTriggerInitializer.java java-bot/bot-core/src/test/java/com/example/supportbot/config/SqliteTriggerInitializerTest.java ai-context/tasks/task-details/01-181.md` - only CRLF/LF warnings, no diff formatting errors
