# 2026-08-11 23:58:00 - sqlite only schema init contract

## Request
- давай следующий крупный шаг. и что осталось по задаче - обнови её

## Summary
- `java-bot` больше не держит generic `schema-${platform}` путь в `application.yml`;
- schema init переведён в явный SQLite-only runtime contract: в SQLite-режиме runtime сам включает `schema-sqlite.sql`, а в external PostgreSQL-режиме принудительно очищает `schema-locations` и оставляет `spring.sql.init.mode=never`;
- добавлены тесты на оба сценария runtime property override;
- обновлена документация и карточка `01-181` с новым фактическим остатком.

## Files Changed
- `java-bot/bot-core/src/main/resources/application.yml`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/DataSourceConfig.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/config/DataSourceConfigTest.java`
- `docs/configuration.md`
- `docs/environment_variables.md`
- `ai-context/tasks/task-details/01-181.md`

## Verification
- `cmd /c mvnw.cmd -q -pl bot-core test` (`java-bot`)
- `cmd /c mvnw.cmd -q compile` (`spring-panel`)
