# 2026-08-11 23:29:00 - explicit bot sqlite bootstrap

## Request
- `давай следующий крупный шаг. и что осталось по задаче - обнови её`

## Summary
- `java-bot` переведён с неявного Spring Boot `sql.init` на явный `SqliteSchemaInitializer`, который исполняет `schema-sqlite.sql` только в SQLite-режиме;
- `SqliteTriggerInitializer` больше не опирается на `spring.sql.init.platform` и использует `BotDatabaseRuntimeMode`;
- из runtime-контракта `spring-panel -> java-bot` убран `SPRING_SQL_INIT_MODE`, так что bot process получает только явный DB-контракт (`APP_DB_PANEL_RUNTIME` для SQLite или `APP_DB_MODE` + `SPRING_DATASOURCE_*` для PostgreSQL);
- обновлены документация и карточка `01-181` с новым фактическим остатком работ.

## Files Changed
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/DataSourceConfig.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/SqliteSchemaInitializer.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/SqliteTriggerInitializer.java`
- `java-bot/bot-core/src/main/resources/application.yml`
- `java-bot/bot-core/src/test/java/com/example/supportbot/config/DataSourceConfigTest.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/config/SqliteSchemaInitializerTest.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeContractService.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeContractServiceTest.java`
- `docs/configuration.md`
- `docs/environment_variables.md`
- `docs/target-production-architecture-plan.md`
- `ai-context/tasks/task-details/01-181.md`

## Verification
- `cmd /c mvnw.cmd -q -pl bot-core test` (`java-bot`) - passed
- `cmd /c mvnw.cmd -q -DskipTests compile` (`spring-panel`) - passed
- `cmd /c mvnw.cmd -q -Dtest=BotRuntimeContractServiceTest test` (`spring-panel`) - blocked by pre-existing unrelated `testCompile` error in `src/test/java/com/example/panel/service/BotProcessServiceTest.java:364`
