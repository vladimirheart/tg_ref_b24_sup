# 2026-08-11 19:05:00 - external bootstrap boundary and task refresh

## Request
- давай следующий крупный шаг. и что осталось по задаче - обнови её

## Summary
- `SecurityBootstrap` переведён на явную external DB boundary: в external PostgreSQL-режиме он больше не создаёт `user_authorities` через runtime DDL и требует, чтобы таблица уже была подготовлена Flyway;
- обновлена задача `01-181`: в `task-list.md` описание стало предметным, а в `task-details/01-181.md` зафиксированы текущий прогресс и оставшийся объём работ по переезду на PostgreSQL-first architecture;
- в `ObjectPassportService` обезврежен оставшийся legacy helper, чтобы в активном runtime-коде не оставался SQLite-специфичный SQL literal.

## Files Changed
- `spring-panel/src/main/java/com/example/panel/security/SecurityBootstrap.java`
- `spring-panel/src/main/java/com/example/panel/service/ObjectPassportService.java`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-181.md`

## Verification
- `cmd /c mvnw.cmd -q compile` (`spring-panel`)
