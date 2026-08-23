# 2026-08-23 23:15 - incident ops reporting v30

## Промпт пользователя

- `репо апнул. проверяй и давай дальше`

## Что проверено

- HEAD после settings save isolation v29: `b583ebdd3ef36c5d052fabb6f12af601cd7fe557`;
- v29 лежит ровно одним commit поверх `de2f9dcd650800057d49de9b1c798cd5a330655c`;
- settings save scopes `dialogs / locations / auto-close` и platform startup log routing присутствуют в repo;
- current production gap audit указывает следующий scope как reporting / alert routing / debug maturity, а по incidents — production analytics/reporting и richer automation.

## Что изменено

- добавлен `IncidentOpsMetricsService` без новой migration/schema;
- `IncidentRepository` и `IncidentRouteRepository` получили bounded aggregate/read methods для ops summary;
- добавлен `GET /api/incidents/ops-summary`;
- incident workbench получил компактную глобальную KPI-сводку:
  - active;
  - critical active;
  - active older than 60 minutes;
  - failed route deliveries;
  - created/resolved in 24h;
  - average acknowledge/resolve durations over 7 days;
- summary refresh идёт отдельно от list/detail state и не ломает пользовательский draft карточки;
- добавлен `IncidentOpsMetricsServiceTest`;
- SCSS остаётся Calm Operations: плотная сетка, без hover lift/movement.

## Проверка после применения

- `node --check spring-panel/src/main/resources/static/js/incidents-workbench.js`;
- `git diff --check`;
- `spring-panel/.mvnw.cmd -q generate-resources`;
- `spring-panel/.mvnw.cmd -q -DskipTests test-compile`;
- `spring-panel/.mvnw.cmd -q -Dtest=IncidentOpsMetricsServiceTest test`;
- ручной smoke `/incidents` и `GET /api/incidents/ops-summary`.