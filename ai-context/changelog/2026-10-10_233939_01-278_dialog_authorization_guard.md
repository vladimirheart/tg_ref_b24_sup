# 01-278: fail-closed authorization boundary для dialog actions

- Время: `2026-10-10 23:39:39`
- Файлы: `spring-panel/src/main/java/com/example/panel/service/DialogAuthorizationService.java`, `spring-panel/src/test/java/com/example/panel/service/DialogAuthorizationServiceTest.java`, `ai-context/tasks/task-details/01-278.md`
- Промт пользователя: `продолжай`
- Что сделано: общий authorization boundary после проверки page capability требует уникальный legacy `ticket_id` для ticket-scoped action. При 0 либо duplicate кандидате он возвращает безопасный `404` до AI operation, quick action или legacy location action и не создаёт ambiguous audit row. Global actions без ticket ID guard не вызывают.
- Проверка: `24/24` targeted tests GREEN; `PanelApplicationTests` поднимает полный Spring-контекст и чистую Flyway-цепочку V1–V52 во временной PostgreSQL Testcontainers-схеме.
- Границы: canonical `business_id`, legacy aliases, Flyway-схема, рабочая БД, migration/backfill, feature flags и production rollout не менялись.
