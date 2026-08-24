# 2026-08-24 00:35 - incident route delivery diagnostics v33

## Промпт пользователя

- `обновил репо. проверяй и давай дальше`

## Что проверено

- новый HEAD после v32: `501f1894656448f784bfa27f0ceb6ec9bc01432b`;
- v32 лежит ровно одним commit поверх `240ff182b73372e04728c8b9908215431ddcd1d3`;
- operator escalation control, shared mute TTL, audit chronology и corrected Mockito TTL fixture присутствуют в repo;
- existing route delivery outbox уже хранит status / attempts / last_error / retry / delivered timestamps и имеет incident+route history index;
- synthetic webhook probe не требуется и сознательно не добавляется.

## Что изменено

- добавлен `IncidentRouteDeliveryDiagnosticsService` без migration/schema churn;
- добавлен `GET /api/incidents/{id}/route-delivery-health`;
- 24h summary считает delivered / failed / pending и terminal success rate;
- per-route health показывает current route state и 24h counters;
- bounded history (80 events) показывает attempts, retry timestamps, requested_by, raw last_error и error classification;
- добавлен отдельный `incident-route-delivery-health.js`, который вставляет read-only diagnostics рядом с блоком маршрутов;
- добавлен Calm Operations SCSS и unit test aggregation/classification logic;
- task `01-183` обновлён remaining production scope.

## Проверка после применения

- `node --check spring-panel/src/main/resources/static/js/incident-route-delivery-health.js`;
- `git diff --check`;
- `spring-panel/.mvnw.cmd -q generate-resources`;
- `spring-panel/.mvnw.cmd -q -DskipTests test-compile`;
- `spring-panel/.mvnw.cmd -q -Dtest=IncidentRouteDeliveryDiagnosticsServiceTest test`;
- smoke `/incidents`: route health card появляется для выбранного incident, failed event показывает raw cause/error kind, redelivery после refresh отражается в status/history.