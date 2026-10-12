# 01-278 / S2A / R42 — точный ticket ID в transport-журналах

Время: 2026-10-12 15:50 MSK. Baseline: `88aba14a97deb173579219a5f8a5970a8914487f`.

Промт пользователя: «продолжи более широким пакетом».

Изменённые области и файлы:

- `java-bot/bot-core/src/main/java/com/example/supportbot/service/IntegrationTransportOutboxService.java` — запись исходного nonblank `ticket_id` без trim.
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/OutboundTransportDeliveryLedgerService.java` — то же при insert и retry update.
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/IntegrationTransportOutboxServiceTest.java` — PostgreSQL-проверки metadata и JSON payload для двух видов событий.
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/OutboundTransportDeliveryLedgerServiceTest.java` — PostgreSQL-проверки insert, retry, delivered idempotence и blank/null.
- `ai-context/tasks/task-details/01-278.md` — checkpoint R42.
- Этот append-only changelog-файл.

До исправления targeted baseline: 9 tests, 4 failures по padded ID. После исправления
31/31 GREEN в четырёх bot-core классах. Проверены точные пары `T-42`/` T-42 `
и `null`/` null `. RabbitMQ mocked, PostgreSQL изолирован в Testcontainers.
Ни рабочая БД, ни storage, ни runtime/deploy не затронуты. S2B/R12 не одобрены;
статус задачи остаётся 🟡.
