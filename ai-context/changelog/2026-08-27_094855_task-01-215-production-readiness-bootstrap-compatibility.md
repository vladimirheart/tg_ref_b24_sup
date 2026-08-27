# 01-215 — production readiness must distinguish local PostgreSQL bootstrap from full production contour

## Промпт пользователя

- на странице настроек есть `Production readiness`, но почему-то в нём не запущены:
  - `Redis coordination`
  - `RabbitMQ transport`
  - `MinIO / S3`
- да и в логе spring полно ошибок

## Симптом

`Production readiness` трактовал local PostgreSQL bootstrap как полноценный production contour только потому, что `APP_DB_MODE=postgresql`.

Из-за этого:

- `Redis coordination` помечался как проблемный, хотя launcher явно выставлял `APP_COORDINATION_REQUIRED_FOR_POSTGRESQL=false` и `APP_COORDINATION_MODE=direct`;
- `MinIO / S3` помечался как проблемный, хотя launcher явно выставлял `APP_STORAGE_OBJECT_REQUIRED_FOR_POSTGRESQL=false` и контур использовал `local_fs`;
- UI показывал сырые технические детали (`transport_mode`, `inbound_dlq_messages`) и англоязычные статусы, из-за чего оператору было трудно понять, что реально сломано, а что лишь не требуется в bootstrap-контуре.

## Причина

- `spring-panel/src/main/java/com/example/panel/service/ProductionReadinessService.java`
  - readiness определял canonical/prod-контур только по `app.datasource.mode=postgresql`;
  - optional bootstrap-флаги для Redis и object storage не учитывались;
  - `overall` и связанные observability/health выводы наследовали эту ошибочную классификацию.
- `spring-panel/src/main/resources/static/js/settings-production-readiness.js`
  - UI показывал англоязычные статусы и сырые snake_case ключи без операторского перевода.

## Что изменено

- `spring-panel/src/main/java/com/example/panel/service/ProductionReadinessService.java`
  - добавлен учёт `ObjectStorageProperties`;
  - full production contour теперь определяется не только по `postgresql`, но и по обязательности Redis/S3 и фактическому `rabbitmq` transport;
  - Redis probe возвращает `compatibility`, если `APP_COORDINATION_REQUIRED_FOR_POSTGRESQL=false`;
  - object storage probe возвращает `compatibility`, если `APP_STORAGE_OBJECT_REQUIRED_FOR_POSTGRESQL=false`;
  - в payload добавлен верхнеуровневый `contour`;
  - RabbitMQ details дополнены именами DLQ, а summary для ненулевых DLQ сделан понятнее.
- `spring-panel/src/main/resources/static/js/settings-production-readiness.js`
  - статусы переведены на русский;
  - compatibility явно маркируется как локально допустимый контур;
  - детали получают человекочитаемые подписи вместо сырых ключей;
  - meta/header теперь показывает тип контура.
- `spring-panel/src/test/java/com/example/panel/service/ProductionReadinessServiceTest.java`
  - обновлён unit coverage для нового contour-контракта и bootstrap-флагов.
- `ai-context/tasks/task-list.md`
  - добавлена и переведена в `🟣` задача `01-215`.
- `ai-context/tasks/task-details/01-215.md`
  - зафиксированы контекст, реализация, проверка и разбор spring-логов.

## Разбор spring-логов

Во время диагностики подтверждено, что readiness-bug и spring log noise — не одно и то же:

- `FATAL: terminating connection due to administrator command` и каскад Hikari WARN были следствием административного рестарта PostgreSQL контейнера;
- `Scheduled Notion knowledge sync failed: Не задан integration token Notion` — отдельный integration warning;
- `SSL monitor check failed` и предупреждения про legacy Telegram proxy route — отдельные внешние сигналы;
- эти сообщения не означают, что `Production readiness` сам по себе не запущен.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-production-readiness.js`
- `spring-panel\mvnw.cmd -q -Dtest=ProductionReadinessServiceTest,ProductionReadinessHealthIndicatorTest,ProductionReadinessObservationCacheTest test`

Completion alert через `bash ai-context/baseline/scripts/show-completion-alert.sh ...` может быть недоступен в этом Windows-окружении без `/bin/bash`.
