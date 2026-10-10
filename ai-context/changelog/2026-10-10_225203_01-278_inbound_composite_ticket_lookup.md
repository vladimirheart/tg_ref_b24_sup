# 01-278: canonical ticket lookup для inbound client message

- Время: `2026-10-10 22:52:03`
- Файлы: `spring-panel/src/main/java/com/example/panel/service/integration/InboundClientMessageIngestionService.java`, `spring-panel/src/test/java/com/example/panel/service/integration/InboundClientMessageIngestionServiceTest.java`, `ai-context/tasks/task-details/01-278.md`
- Промт пользователя: `дальше`
- Что сделано: inbound client message использует составной canonical locator `(user_id, ticket_id)` из event вместо `findByIdTicketId`. Так transport path не может выбрать обращение только по bare legacy ID.
- Границы: legacy child tables, `business_id`, alias mappings, Flyway-схема, рабочая БД, migration/backfill, feature flags и production rollout не менялись.
