# 2026-08-24 00:15 - incident escalation operator control v32

## Промпт пользователя

- `всё встало с первого раза. репо обновил. проверяй и давай дальше`

## Что проверено

- новый HEAD после v31: `240ff182b73372e04728c8b9908215431ddcd1d3`;
- v31 лежит ровно одним commit поверх `632c3040df8ad30dd4efb0fa77044b392b72a159`;
- critical/aged/failed-route policy, shared lease/cooldown, chronology + route escalation и unit test присутствуют;
- critical исключён из duplicate aged policy в том же pass;
- failed routes агрегируются по incident перед escalation.

## Что изменено

- `RuntimeCoordinationService` получил TTL inspection и clear для cooldown keys;
- `IncidentOpsEscalationService` получил operator control state и TTL mute по `incident + policy`;
- scheduler учитывает shared mute до обычного automatic cooldown;
- control state показывает threshold/cooldown, remaining cooldown, remaining mute и latest escalation timestamp;
- `IncidentService` пишет mute/unmute audit event без notification/route fan-out;
- `/api/incidents/{id}/escalation-control` и mute/unmute endpoints доступны через существующий incident permission contour;
- incident detail получил отдельный JS runtime для policy visibility/control;
- добавлено unit coverage для mute suppression и shared TTL state;
- SCSS остаётся в Calm Operations стиле без hover movement/lift.

## Проверка после применения

- `node --check spring-panel/src/main/resources/static/js/incidents-workbench.js`;
- `node --check spring-panel/src/main/resources/static/js/incident-escalation-controls.js`;
- `git diff --check`;
- `spring-panel/.mvnw.cmd -q generate-resources`;
- `spring-panel/.mvnw.cmd -q -DskipTests test-compile`;
- `spring-panel/.mvnw.cmd -q -Dtest=IncidentOpsEscalationServiceTest test`;
- smoke: mute одного policy не блокирует другие policy, survives second backend instance via Redis, unmute не очищает automatic cooldown.