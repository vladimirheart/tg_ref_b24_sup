# 2026-09-01 23:31:00 - Realtime production rollout

## Промпт пользователя

> делай

## Изменения

- Собран и применён образ с ограничением дочерних Hikari pool ботов; `bot-runner` перезапущен без остановки панели.
- Каждый из трёх runtime ботов получил `APP_DB_MAX_POOL_SIZE=6`; PostgreSQL после запуска использует 36 idle JDBC connections при `max_connections=150`.
- `ops-worker` обновлён с типизированными JDBC-параметрами `created_at` и `expires_at` для запросов оценки после автозакрытия диалога.
- Профильные тесты `DialogAutoCloseSchedulerServiceTest` и `DialogTicketLifecycleServiceTest` прошли: 7 tests, 0 failures.
- Все runtime services имеют health status `healthy`; контрольный просмотр логов не выявил `too many clients`, конфликтов Telegram `409`, ошибок `timestamptz` и остановки outbox.
