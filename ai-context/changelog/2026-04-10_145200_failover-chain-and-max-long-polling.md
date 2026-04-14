# Changelog 2026-04-10 14:52

## Что изменено

1. Добавлен failover сетевых маршрутов на стороне панели.
- `IntegrationNetworkService` теперь поддерживает:
  - `profile_ids` (цепочка профилей по приоритету),
  - `failover_downtime_seconds` (TTL недоступности маршрута),
  - health-check доступности (`host:port`) и переключение на следующий маршрут.
- В process env добавлены:
  - `APP_NETWORK_PROFILE_IDS`,
  - `APP_NETWORK_FAILOVER_DOWNTIME_SECONDS`.

2. Обновлён UI настроек сетевых маршрутов.
- Для проектного, ботного и канального маршрутов профиль переведён в multi-select (цепочка failover).
- Добавлены поля TTL недоступности маршрута.
- Добавлены валидации для profile-mode (требуется хотя бы один профиль) и сохранены проверки VLESS token.
- Тексты summary/hint обновлены под failover-логику.

3. MAX переведён на long polling.
- Добавлен `MaxLongPollingLifecycle`, который опрашивает `GET /updates` и передаёт события в обработчик.
- В `MaxApiClient` добавлен `fetchUpdates` и модель `PollBatch`.
- В `MaxWebhookController` webhook-аннотации закомментированы (код сохранён для rollback).
- `MaxBotApplication` переключён на `--spring.main.web-application-type=none`.
- Тексты в настройках панели обновлены: для MAX теперь указан long polling вместо webhook.

## Проверка

- `spring-panel`: `.\mvnw.cmd -DskipTests compile` — SUCCESS.
- `java-bot` (`bot-max`): `.\mvnw.cmd -pl bot-max -am -DskipTests compile` — SUCCESS.
