# 01-278: fail-closed доступ к ticket attachments

- Время: `2026-10-10 23:56:32`
- Файлы: `spring-panel/src/main/java/com/example/panel/storage/AttachmentService.java`, `AttachmentStorageKeyResolver.java`, их unit/MVC-тесты, `ai-context/tasks/task-details/01-278.md`
- Промт пользователя: `дальше`
- Что сделано: извлечение ticket namespace из storage key и legacy absolute path позволяет public attachment lookup по ID, path и storage key требовать уникальный `tickets.ticket_id` до object storage. При отсутствии или duplicate ID ответ — `404`; внутренние ticket upload/delete/describe также fail-closed. Knowledge-base и avatars не затрагивались.
- Проверка: `18/18` targeted tests GREEN; `PanelApplicationTests` поднимает полный Spring-контекст и чистую Flyway-цепочку V1–V52 во временной PostgreSQL Testcontainers-схеме.
- Границы: canonical `business_id`, legacy aliases, Flyway-схема, рабочая БД, migration/backfill, feature flags и production rollout не менялись.
