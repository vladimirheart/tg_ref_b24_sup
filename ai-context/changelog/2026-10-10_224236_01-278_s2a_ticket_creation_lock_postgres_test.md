# 01-278: PostgreSQL-проверка advisory lock создания обращения

- Время: `2026-10-10 22:42:36`
- Файлы: `spring-panel/src/test/java/com/example/panel/service/integration/LegacyTicketIdAdvisoryLockPostgresIntegrationTest.java`, `spring-panel/src/main/java/com/example/panel/service/integration/ConversationTicketCreationIngestionService.java`
- Промт пользователя: `давай дальше`
- Что сделано: добавлен Testcontainers-тест фактического PostgreSQL transaction advisory lock. Он удерживает первую транзакцию с exact legacy `ticket_id` и подтверждает, что вторая транзакция с тем же ID не получает lock до commit первой.
- Границы: тест использует только временную PostgreSQL-схему Testcontainers; migration/backfill, рабочая БД, business assignment и feature flags не затрагиваются.
