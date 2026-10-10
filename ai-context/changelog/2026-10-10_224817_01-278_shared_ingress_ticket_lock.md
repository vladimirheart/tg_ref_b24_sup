# 01-278: общий ingress lock по legacy ticket ID

- Время: `2026-10-10 22:48:17`
- Файлы: `spring-panel/src/main/java/com/example/panel/service/integration/LegacyTicketIdConcurrencyGuard.java`, `spring-panel/src/main/java/com/example/panel/service/integration/ConversationTicketCreationIngestionService.java`, `spring-panel/src/main/java/com/example/panel/service/integration/InboundClientMessageIngestionService.java`, связанные unit/integration tests и `ai-context/tasks/task-details/01-278.md`
- Промт пользователя: `дальше`
- Что сделано: PostgreSQL transaction advisory lock вынесен в общий компонент и применяется перед lookup/write как при создании root-ticket, так и при приёме клиентского сообщения. Параллельное child-сообщение с тем же exact legacy ID ждёт commit/rollback создания обращения.
- Границы: нет assignment `business_id`, alias эвристик, изменения Flyway-схемы, рабочей БД, migration/backfill, feature flags или production rollout.
