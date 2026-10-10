# S2A: JPA mapping canonical owner тикета для задачи 01-278

- Время: `2026-10-10T16:33:38+03:00` (Europe/Moscow).
- Промт пользователя: `дальше`.
- Тип изменения: source-only S2A. Не добавлялась и не изменялась Flyway
  migration; рабочие Docker-контейнеры, рабочая/production БД, backfill,
  очереди, внешние системы, staging, commit, push и rollout не выполнялись.

## Изменённые файлы

- `spring-panel/src/main/java/com/example/panel/entity/Ticket.java` — nullable
  JPA mapping `business_id` с явным getter/setter.
- `spring-panel/src/test/java/com/example/panel/entity/TicketPublicIdTest.java`
  — проверка колонкового mapping и отсутствия implicit assignment.
- `ai-context/tasks/task-details/01-278.md` — checkpoint отображает мост
  V51 `business_id` в JPA-модели.
- `ai-context/changelog/2026-10-10_163338_01-278_s2a_ticket_business_mapping.md`
  — append-only запись данного изменения.

## Что сделано и проверено

- V51 ранее создал nullable `tickets.business_id`, но `Ticket` его не
  отображал. Теперь canonical owner доступен future controlled write paths
  через JPA, при этом constructor/pre-persist не задают default business.
- Первая targeted проверка была RED: тест ссылался на ещё отсутствующие
  `getBusinessId`/`setBusinessId`. После добавления поля и методов чистая
  `mvnw.cmd clean -Dtest=TicketPublicIdTest test` прошла GREEN.
- Финальная чистая команда
  `spring-panel\\mvnw.cmd -q clean "-Dtest=TicketPublicIdTest,TicketLocatorServicePostgresIntegrationTest,TicketLocatorFlywayMigrationTest,LegacyTicketAssignmentPreviewServicePostgresIntegrationTest" test`
  прошла GREEN: `5/5`. Она применила V1→V52 только во временной PostgreSQL
  Testcontainers-схеме и не изменяла legacy rows в runtime.
