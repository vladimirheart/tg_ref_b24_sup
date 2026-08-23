# 2026-08-23 23:55 - incident alert routing v31

## Промпт пользователя

- `репо апнул. проверяй и давай дальше`

## Что проверено

- новый HEAD после Incident Ops reporting v30: `632c3040df8ad30dd4efb0fa77044b392b72a159`;
- v30 лежит ровно одним commit поверх `b583ebdd3ef36c5d052fabb6f12af601cd7fe557`;
- backend ops-summary, KPI UI, unit test, generated CSS и task/changelog присутствуют;
- current `RuntimeCoordinationService` уже предоставляет shared Redis lease/cooldown primitives;
- existing incident lifecycle уже умеет notifications + durable route outbox, поэтому отдельный alert transport не нужен.

## Что изменено

- добавлен `IncidentOpsEscalationService`;
- policy `critical`, `aged`, `route_delivery_failed` выполняются bounded выборками;
- critical incident не получает duplicate aged escalation в тот же проход;
- каждый `incident + policy` защищён shared cooldown;
- весь scheduled pass защищён shared lease;
- `IncidentService.escalateIncident(...)` пишет chronology event, уведомляет participants и отправляет escalation через существующие incident routes;
- failed route policy route-ит на incident level по всем маршрутам, а не пытается уведомлять только через уже сломанный route;
- operational defaults доступны через `PANEL_INCIDENT_ESCALATION_*` env vars;
- новый scheduler направлен в `incidents.log` с `additivity=true`.

## Проверка после применения

- `git diff --check`;
- `spring-panel/.mvnw.cmd -q -DskipTests test-compile`;
- `spring-panel/.mvnw.cmd -q -Dtest=IncidentOpsEscalationServiceTest test`;
- smoke: critical / aged / failed-route incident создаёт не более одной escalation на policy в пределах cooldown;
- повторный backend instance не дублирует scheduled pass при Redis coordination.
