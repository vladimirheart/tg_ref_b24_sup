# 2026-08-11 23:40:00 - external bot sql init hardening

## Request
- давай следующий крупный шаг. и что осталось по задаче - обнови её

## Summary
- `java-bot` external PostgreSQL runtime теперь принудительно отключает legacy `spring.sql.init`, даже если в окружении остались старые overrides вроде `SQL_INIT_MODE=always`;
- для external PostgreSQL также принудительно выравниваются `spring.sql.init.platform` и JPA dialect, чтобы runtime не мог случайно откатиться к SQLite-ожиданиям;
- для SQLite runtime добавлено симметричное принудительное выравнивание platform/dialect;
- карточка `01-181` обновлена: в ней отражён новый runtime boundary и уточнён оставшийся объём работ.

## Files Changed
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/DataSourceConfig.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/config/DataSourceConfigTest.java`
- `ai-context/tasks/task-details/01-181.md`

## Verification
- `cmd /c mvnw.cmd -q -pl bot-core test` (`java-bot`)
- `cmd /c mvnw.cmd -q compile` (`spring-panel`)
