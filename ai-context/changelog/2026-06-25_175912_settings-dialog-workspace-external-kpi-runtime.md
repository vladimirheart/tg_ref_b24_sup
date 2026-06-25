# 2026-06-25 17:59:12 - settings dialog workspace external kpi runtime

## Промпты пользователя

- `давай следующий шаг по задаче`

## Что изменено

- продолжен `01-129`: создан
  `spring-panel/src/main/resources/static/js/settings-dialog-workspace-external-kpi-runtime.js`,
  который забрал subdomain `workspace rollout / external KPI` вместе с
  hydration, validation, UTC timestamp presentation/summary, cross-product
  dashboard bindings, datamart contract preview и payload collection для
  сохранения настроек;
- `spring-panel/src/main/resources/templates/settings/index.html` переведён на
  mount нового external KPI runtime: удалены inline DOM-bindings, hydration,
  UTC/helper-слой и collect/payload блок `workspace rollout / external KPI`, а
  сохранение теперь получает этот payload через runtime API;
- `spring-panel/src/main/resources/static/js/settings-page-shell.js` очищен от
  устаревших bootstrap entry-points `initExternalKpiUtcTimestampFields` и
  `initWorkspaceExternalKpiDatamartContractPreview`, так как их ownership
  переехал в новый runtime;
- карточка `ai-context/tasks/task-details/01-129.md` обновлена: зафиксирован
  новый runtime-split пакет, а остаточный scope сужен до `SLA core` и
  оставшихся page-level bridge entry-points.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-dialog-workspace-external-kpi-runtime.js`
- `rg -n "workspaceExternalKpi|crossProductOmnichannelDashboard|crossProductFinanceDashboard|EXTERNAL_KPI_UTC_TIMESTAMP_FIELDS|normalizeDataContractFieldList|syncWorkspaceExternalKpiDatamartContractState|populateExternalKpiUtcTimestampField|syncAllExternalKpiUtcTimestampFields|isValidExternalReferenceUrl|parseUtcDateInput|toUtcIsoString|toIsoUtcString|normalizeDatetimeLocalValue|initExternalKpiUtcTimestampFields|initWorkspaceExternalKpiDatamartContractPreview" spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-dialog-workspace-external-kpi-runtime.js spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/tasks/task-details/01-129.md`
