# 2026-06-30 10:25:53 - settings runtime dialog helpers

## Промпты пользователя

- `давай следующий шаг по задаче.`
- `что ещё осталось дожать чтобы считать завершённой задачу?`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`
  расширен верхним `promptDialog` fallback рядом с уже существующим
  `confirmDialog`, чтобы page init слой мог централизованно поставлять
  browser-dialog helpers вниз по runtime contract;
- `spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
  теперь прокидывает общий `promptDialog`/`confirmDialog` contract в
  `SettingsParametersShellRuntime`, `SettingsAppearanceRuntime` и
  `SettingsLocationsTreeRuntime`, вместо того чтобы leaf runtime напрямую
  падали на `globalThis.confirm/prompt`;
- `spring-panel/src/main/resources/static/js/settings-appearance-runtime.js`
  больше не вызывает `confirm(...)` напрямую при сохранении пустого списка
  статусов, а использует injected `confirmDialog`;
- `spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
  переведён с прямых `confirm/prompt` на injected `confirmDialog/promptDialog`
  для удаления и создания бизнесов/типов/городов/локаций;
- `spring-panel/src/main/resources/static/js/settings-it-connections-runtime.js`
  больше не вызывает `window.prompt(...)` напрямую при создании новой
  категории подключения, а использует injected `promptDialog`;
- `spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js`
  дотянут до этого же helper-contract и прокидывает `promptDialog` вниз в
  `SettingsItConnectionsRuntime`;
- `ai-context/tasks/task-details/01-129.md` обновлён: зафиксирован ещё один
  снятый helper-boundary хвост и уточнён remaining scope по задаче.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-appearance-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-it-connections-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js`
- `rg -n --glob "settings-*.js" "confirm\\(|prompt\\(" spring-panel/src/main/resources/static/js`
