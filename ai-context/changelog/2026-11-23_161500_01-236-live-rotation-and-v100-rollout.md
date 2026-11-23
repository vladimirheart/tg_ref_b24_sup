# 01-236: частичная live-ротация и rollout V100

Время: 2026-11-23 16:15 Europe/Moscow.

## Промпт пользователя

```text
`01-236` немного детализировалась.
давай менять пароли на PostgreSQL, RabbitMQ, Redis и Grafana.
у тебя есть все необходимые доступы.
и ещё нужно пересобрать web и выполнить миграцию v100
```

## Выполнено

- В непроизводственном Docker-контуре сменены persistent credentials PostgreSQL,
  RabbitMQ и Grafana. Проверка status-helper подтвердила для каждого
  `ready/authenticated`; сами значения не записывались в Git, changelog или логи.
- Запущен официальный launcher с `-Observability -AllowInsecureDefaults -Build`;
  образ `iguana-panel:local` пересобран. Flyway успешно применил
  `V100__user_session_revocation.sql`, а `db-migrate` завершился с exit code `0`.
- PostgreSQL, RabbitMQ, Redis, `ops-worker`, `bot-runner` и `panel-web` были
  проверены после rollback/recreate; web route `/analytics/rms-control` вернула
  штатный `302` на вход.
- Усилены credential helpers: Redis probe передаёт пароль в UTF-8 через stdin,
  Grafana apply ждёт готовности API после recreate, native stderr не смешивается
  с результатом auth probe.

## Redis: ограничение и rollback

Переданный Redis target содержал не-ASCII символ. Redis server принял его, но
используемый Lettuce client формирует для такого значения неподходящий `AUTH`;
panel roles не могли пройти readiness. Redis был контролируемо возвращён к
предыдущему documented local значению, чтобы восстановить контур. PowerShell и
Bash helpers теперь прекращают Redis apply до live-изменения, если target не
состоит из printable ASCII.

Для завершения 01-236 требуется новый Redis password только из printable ASCII.
MinIO по выбору пользователя в этой операции не ротировался.
