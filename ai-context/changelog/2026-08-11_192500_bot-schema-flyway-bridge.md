# 2026-08-11 19:25:00 - bot schema flyway bridge

## Request
- давай следующий крупный шаг. и что осталось по задаче - обнови её

## Summary
- в PostgreSQL Flyway-слой `spring-panel` перенесены ещё две bot-side business tables: `bot_credentials` и `channel_notifications`;
- добавлены индексы для типовых runtime-paths этих таблиц, чтобы external bot runtime меньше зависел от legacy `schema-postgres.sql`;
- карточка `01-181` обновлена: в ней зафиксирован новый прогресс и уточнён остаток по переводу legacy bot schema под полный Flyway ownership.

## Files Changed
- `spring-panel/src/main/resources/db/migration/postgresql/V13__bot_runtime_schema_bridge.sql`
- `ai-context/tasks/task-details/01-181.md`

## Verification
- `cmd /c mvnw.cmd -q compile` (`spring-panel`)
