# 2026-09-01 14:17 — задача 01-234: PostgreSQL runtime для production-ботов

## Пользовательский запрос

`продолжай`

## Выполнено

- RabbitMQ transport mode больше не включает SQLite worker datasource у Telegram, VK и MAX bot runtime.
- Launcher передаёт всем production-ботам `APP_DB_MODE=postgresql`, JDBC URL и доступы к канонической PostgreSQL базе.
- Production compose-профили Telegram/VK/MAX получили тот же PostgreSQL datasource-контракт.
- Contract-тесты обновлены и проверяют PostgreSQL-параметры в RabbitMQ режиме.

## Проверка

- `spring-panel\\mvnw.cmd -Dtest=BotRuntimeContractServiceTest,BotProcessServiceTest,RmsLicenseMonitoringServiceTest test` — 48 tests, 0 failures.
- `docker compose -f docker-compose.production-contour.yml config -q` — успешно.

## Ограничение развёртывания

Боты не перезапускались в рамках этой задачи: в Telegram уже подтверждён `409 Conflict` от параллельного long polling. Перезапуск безопасно выполнять после устранения дубля в задаче `01-232`.
