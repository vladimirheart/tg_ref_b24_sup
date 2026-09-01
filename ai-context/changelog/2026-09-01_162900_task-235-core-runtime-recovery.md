# 2026-09-01 16:29 — задача 01-235: восстановление core-контура и подготовка bot rollout

## Пользовательский запрос

`продолжай`

## Выполнено

- Восстановлены PostgreSQL и RabbitMQ из persistent volumes; оба прошли healthcheck.
- Исправлены PostgreSQL-несовместимые выражения `is_deleted = 0` в UI preferences и NetBox sync.
- Исправлена DI-регрессия RMS после добавления constructor overload: production-конструктор помечен `@Autowired`.
- Пересобран `iguana-panel:local` с panel и prebuilt JAR Telegram/VK/MAX.
- Успешно выполнен `db-migrate`; `panel-web` и `ops-worker` healthy, свежие логи без `boolean = integer` и application startup errors.

## Проверка

- `spring-panel\\mvnw.cmd -Dtest=NetBoxObjectPassportSyncServiceTest test` — 6 tests, 0 failures.
- `spring-panel\\mvnw.cmd -Dtest=RmsLicenseMonitoringServiceTest,BotRuntimeContractServiceTest test` — 29 tests, 0 failures.

## Открытый блокер

`TELEGRAM_BOT_TOKEN` отсутствует в `.env`; профиль `bot-telegram` намеренно не запущен. После безопасной передачи token в production-конфигурацию задача продолжит rollout и end-to-end проверку.
