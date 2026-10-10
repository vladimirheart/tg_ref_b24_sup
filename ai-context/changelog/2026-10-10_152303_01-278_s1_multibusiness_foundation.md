# S1 foundation независимых бизнесов Iguana

- Время: `2026-10-10T15:23:03+03:00` (Europe/Moscow).
- Промты пользователя:
  - `выполни задачу 01-278`
  - `дальше`
- Тип изменения: source-only S1. Flyway V51 проверялась только в временной
  PostgreSQL Testcontainers-схеме; рабочая/production БД, runtime, очереди,
  backfill, внешние системы, staging, commit, push и rollout не выполнялись.

## Изменённые файлы

- `spring-panel/src/main/resources/db/migration/postgresql/V51__multibusiness_foundation.sql`
  — additive business schema, preinstalled businesses, memberships/all-scope
  grants, alias/channel/location mapping foundations и nullable ticket FK.
- `spring-panel/src/main/java/com/example/panel/service/BusinessAccessService.java`
  — canonical resolver accessible IDs и business capabilities с deny-by-default
  поведением.
- `spring-panel/src/test/java/com/example/panel/service/BusinessAccessServicePostgresIntegrationTest.java`
  — реальный PostgreSQL contract test V51 и access scope.
- `ai-context/rules/03-multibusiness-s0-contract.md` — S1 checkpoint и
  корректные gates для следующих этапов.
- `ai-context/tasks/task-details/01-278.md` — фактический S1 checkpoint.
- `ai-context/changelog/2026-10-10_152303_01-278_s1_multibusiness_foundation.md`
  — текущая append-only запись.

## Что фактически сделано

- Существующие `messages.business`, ticket IDs, API/UI и legacy rows не
  переписывались. `tickets.business_id` допускает `NULL` до отдельного S2
  assignment/backfill решения.
- Созданы обычные initial businesses с codes `sushi` и `bliny`; реальные
  legacy aliases намеренно не seed-ятся и не угадываются.
- `ALL_BUSINESSES` динамически видит новый active business, но grant с ролью
  `VIEWER` не получает business capability `CONFIGURE` и не равен system admin.
- Targeted команда
  `spring-panel\\mvnw.cmd -q -Dtest=BusinessAccessServicePostgresIntegrationTest test`
  прошла GREEN: 2 tests, 0 failures, 0 errors. Первая попытка выявила SQL alias
  `grant`, конфликтующий с PostgreSQL keyword; alias заменён на `access_grant`,
  после чего тот же тест прошёл.
