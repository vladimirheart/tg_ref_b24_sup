# 01-214 — local PostgreSQL bootstrap must not require Redis UI fanout

## Промпт пользователя

- лог запуска из `spring-panel\\run-windows.bat`, где `spring-panel` падала на старте после PostgreSQL/RabbitMQ preflight

## Симптом

Локальный Windows launcher успешно проходил preflight и поднимал PostgreSQL/RabbitMQ, но `spring-panel` завершалась с:

- `Failed to start bean 'uiEventRedisMessageListenerContainer'`
- `Unable to connect to Redis`
- `Connection refused: localhost/127.0.0.1:6379`

## Причина

- launcher уже подставлял локальные compatibility defaults для PostgreSQL bootstrap (`APP_COORDINATION_MODE=direct`);
- при этом UI fanout subscriber всё равно стартовал безусловно;
- `APP_UI_EVENT_FANOUT_MODE=auto` в compatibility `role=all` фактически должен оставаться local/process-local режимом, а не жёсткой Redis-зависимостью.

## Что изменено

- `spring-panel/src/main/java/com/example/panel/service/UiEventFanoutPublisher.java`
  - `AUTO`/`LOCAL` больше не пытаются публиковать в Redis;
  - Redis fanout используется только при явном `APP_UI_EVENT_FANOUT_MODE=redis`.
- `spring-panel/src/main/java/com/example/panel/runtime/UiEventRedisSubscriptionConfiguration.java`
  - Redis listener container создаётся только при `app.ui-events.fanout.mode=redis`.
- `spring-panel/src/test/java/com/example/panel/service/UiEventFanoutPublisherTest.java`
  - обновлён тест на новый compatibility-контракт.
- `spring-panel/src/test/java/com/example/panel/runtime/UiEventRedisSubscriptionConfigurationTest.java`
  - добавлен тест, что в `AUTO` Redis listener не создаётся.
- `spring-panel/src/test/java/com/example/panel/runtime/ProductionBackupContourSourceContractTest.java`
  - уже был синхронизирован ранее под dynamic backup policy runner и не блокировал текущий старт launcher.

## Проверка

- `spring-panel\\mvnw.cmd -q -Dtest=UiEventFanoutPublisherTest,RuntimeRoleSafetyValidatorTest,UiEventRedisSubscriptionConfigurationTest test`
- реальный `spring-panel\\run-windows.bat`
- после старта `http://localhost:8080/` ответил `HTTP 200`

Completion alert через `bash ai-context/baseline/scripts/show-completion-alert.sh ...` недоступен в этом окружении, потому что отсутствует `/bin/bash`.
