# 01-278: JDBC fail-closed guard для legacy ticket ID

- Время: `2026-10-10 23:21:18`
- Файлы: `spring-panel/src/main/java/com/example/panel/service/LegacyTicketIdJdbcGuard.java`, `DialogTicketLifecycleService.java`, `DialogResponsibilityService.java`, `DialogParticipantService.java`, `DialogReplyTargetService.java`, `BotRuntimeTicketWriteService.java`, их тесты, `ai-context/tasks/task-details/01-278.md`
- Промт пользователя: `дальше. более широким пакетом`
- Что сделано: общий JDBC guard читает максимум два кандидата по legacy `ticket_id` и пропускает операцию только при ровно одном. Lifecycle, ответственные, участники, reply-target и bot-runtime используют его до bare-ID чтения или изменения. При отсутствии либо неоднозначности данных операция fail-closed и не выбирает/не изменяет произвольный диалог.
- Проверка: целевой PostgreSQL Testcontainers-набор проверяет 0/1/2 кандидата и отсутствие мутаций для duplicate ID; `PanelApplicationTests` поднимает Spring-контекст и чистую Flyway-цепочку V1–V52 во временной БД.
- Границы: `business_id`, legacy aliases, Flyway-схема, рабочая БД, migration/backfill, feature flags и production rollout не менялись.
