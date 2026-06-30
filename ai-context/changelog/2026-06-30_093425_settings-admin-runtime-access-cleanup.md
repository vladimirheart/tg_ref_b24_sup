# 2026-06-30 09:34:25 - settings admin runtime access cleanup

## Промпты пользователя

- `давай следующий шаг по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-runtime-access.js`
  расширен до общего method invoke слоя: добавлен `invokeRuntimeMethod(...)`,
  а `mountRuntime(...)` теперь поддерживает произвольные аргументы вызова,
  а не только один `options`-объект;
- `spring-panel/src/main/resources/static/js/settings-admin-shell-runtime.js`
  переведён на `SettingsRuntimeAccess`: admin shell больше не монтирует
  `SettingsReportingManagerBindings` и `AuthManagement` через прямые
  `window.*.mount(...)`, а использует общий runtime access contract;
- `ai-context/tasks/task-details/01-129.md` обновлён: в задаче зафиксировано,
  что admin-shell bridge тоже снят с прямого global mount доступа, а remaining
  scope ещё сильнее сместился к payload/data contract cleanup.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-runtime-access.js`
- `node --check spring-panel/src/main/resources/static/js/settings-admin-shell-runtime.js`
- `rg -n "SettingsReportingManagerBindings\\?\\.mount|window\\.AuthManagement|AuthManagement\\.mount|window\\.SettingsReportingManagerBindings" spring-panel/src/main/resources/static/js`
