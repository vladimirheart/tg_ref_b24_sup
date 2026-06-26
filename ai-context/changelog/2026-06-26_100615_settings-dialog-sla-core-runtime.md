# 2026-06-26 10:06:15 - settings dialog sla core runtime

## Промпты пользователя

- `давай следующий шаг по задаче 01-129`

## Что изменено

- продолжен `01-129`: создан
  `spring-panel/src/main/resources/static/js/settings-dialog-sla-core-runtime.js`,
  который забрал большой init/hydration/helper-слой вокруг `dialog SLA`,
  `workspace single mode`, auto-assign example bindings и соседних dialog form
  controls;
- `spring-panel/src/main/resources/templates/settings/index.html` переведён на
  mount нового SLA core runtime: удалён inline init-блок `getDialogSlaInputs()`,
  `initDialogSlaControls()`, `initWorkspaceSingleModeControls()`,
  `validateSlaAutoAssignRules()` и `bindAutoAssignRulesHelpers()`, а
  remaining collect-layer теперь получает inputs/auto-assign validation через
  runtime API;
- `spring-panel/src/main/resources/static/js/settings-page-shell.js` очищен от
  bootstrap entry-points `initDialogSlaControls`,
  `initWorkspaceSingleModeControls` и `bindAutoAssignRulesHelpers`, потому что
  их ownership переехал в новый runtime;
- карточка `ai-context/tasks/task-details/01-129.md` обновлена: зафиксирован
  вынос init/hydration слоя `dialog SLA`, а remaining scope сужен до
  collect/serialization слоя вокруг `SLA/workspace/macro/client-context`.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-dialog-sla-core-runtime.js`
- `git diff --check`
