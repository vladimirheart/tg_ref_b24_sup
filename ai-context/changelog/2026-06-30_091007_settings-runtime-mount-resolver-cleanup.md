# 2026-06-30 09:10:07 - settings runtime mount resolver cleanup

## Промпты пользователя

- `давай следующий шаг по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`,
  `settings-page-init-runtime.js`,
  `settings-parameters-shell-runtime.js` и
  `settings-channels-shell-runtime.js` переведены на единый
  `mountSettingsRuntime(...)` pattern вместо россыпи прямых
  `window.Settings*Runtime?.mount(...)` вызовов;
- `spring-panel/src/main/resources/static/js/settings-page-shell.js` получил
  `resolveSettingsRuntimeApi(...)`, так что runtime callback resolution теперь
  идёт через единый namespace resolver поверх `globalThis`, а не через прямой
  доступ к `window[target.runtimeName]`;
- `settings-page-init-runtime.js` заодно выделил `buildSettingsPageConfig(...)`
  для config build orchestration, чтобы верхний init-слой меньше зависел от
  scattered global namespace access;
- `ai-context/tasks/task-details/01-129.md` обновлён: в задаче зафиксировано,
  что shell/bootstrap namespace mount-orchestration уже собран в единый
  resolver pattern, а remaining scope ещё сильнее смещён к payload contract и
  возможной дальнейшей runtime registry консолидации.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js spring-panel/src/main/resources/static/js/settings-page-init-runtime.js spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-30_091007_settings-runtime-mount-resolver-cleanup.md`
