# 2026-06-30 09:22:57 - settings runtime access layer

## Промпты пользователя

- `давай следующий более широкий шаг по задаче`

## Что изменено

- Добавлен `spring-panel/src/main/resources/static/js/settings-runtime-access.js`:
  он централизует `resolveRuntimeApi(...)`, `resolveRuntimeMethod(...)`,
  `mountRuntime(...)` и `buildPageConfig(...)` для верхнего settings runtime слоя;
- `spring-panel/src/main/resources/templates/settings/index.html` теперь
  подключает `settings-runtime-access.js` перед `settings-page-shell.js`, чтобы
  общий access-layer был доступен всем page-level runtime и legacy bridge
  scripts;
- `spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`,
  `settings-page-bootstrap-runtime.js`,
  `settings-parameters-shell-runtime.js`,
  `settings-channels-shell-runtime.js` и `settings-page-shell.js` переведены на
  `SettingsRuntimeAccess`: локальные дубли `mountSettingsRuntime(...)` и
  `resolveSettingsRuntimeApi(...)` удалены, а runtime lookup/mount теперь идёт
  через единый helper;
- `spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`
  дополнительно перестал напрямую ходить в `window.CommonUtils` и
  `window.SettingsPageShell?.requestCloseModal`, а `bot-settings.js` —
  напрямую в `window.SettingsPageConfigRuntime`;
- `ai-context/tasks/task-details/01-129.md` обновлён: в задаче зафиксировано,
  что после resolver cleanup верхний settings layer уже получил отдельный
  `SettingsRuntimeAccess`, а remaining scope ещё сильнее сместился к
  payload/data contract cleanup.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-runtime-access.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `node --check spring-panel/src/main/resources/static/js/bot-settings.js`
