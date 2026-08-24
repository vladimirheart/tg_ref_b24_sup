# 2026-08-24 07:03 - production readiness surface v34

## Промпт пользователя

- `обновил репо. проверяй и давай дальше`

## Что проверено

- новый HEAD после Incident Route Delivery Diagnostics v33: `f7c6d6868dd9731dc9dc5d52fb8e150ed91c7b4f`;
- v33 лежит ровно одним commit поверх `501f1894656448f784bfa27f0ceb6ec9bc01432b`;
- diagnostics endpoint incident-scoped, history bounded `LIMIT 80`, raw failure reason сохраняется рядом с classification;
- текущий startup PostgreSQL verifier уже проверяет Redis coordination и S3 object storage;
- panel transport mode считает canonical Rabbit mode строкой `rabbitmq`;
- Settings shell умеет открывать direct modal target без отдельного route wiring.

## Что изменено

- добавлен `ProductionReadinessService` с read-only on-demand probes PostgreSQL / Redis / RabbitMQ / MinIO-S3 / incident delivery;
- добавлен settings-only `GET /api/settings/production-readiness`;
- `AttachmentObjectStorageService` и `RuntimeCoordinationService` получили reusable `verifyAvailable()` probes для S3/Redis;
- Settings overview получил `Production readiness` tile/modal и отдельный JS runtime;
- UI не содержит restart/provision/mutation действий;
- non-canonical datasource mode маркируется `compatibility`;
- Rabbit `jdbc` mode в PostgreSQL contour маркируется degraded;
- Rabbit DLQ backlog и unresolved/stale incident delivery отражаются как degraded;
- runbook и task `01-183` обновлены.

## Проверка после применения

- `node --check spring-panel/src/main/resources/static/js/settings-production-readiness.js`;
- `git diff --check`;
- `spring-panel/.mvnw.cmd -q generate-resources`;
- `spring-panel/.mvnw.cmd -q -DskipTests test-compile`;
- `spring-panel/.mvnw.cmd -q -Dtest=ProductionReadinessServiceTest test`;
- smoke: Settings -> Production readiness открывает snapshot, refresh не меняет runtime state, unavailable component показывает причину.
