# 2026-06-29 15:21:07 - settings page legacy globals final cleanup

## Промпты пользователя

- `давай следующий более широкий шаг по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-page-config-runtime.js`
  больше не публикует `BOT_SETTINGS_INITIAL` и
  `BOT_PRESET_DEFINITIONS` в `window.*`; вместо этого runtime хранит последний
  нормализованный config и отдаёт bot settings данные через namespace getters;
- `spring-panel/src/main/resources/static/js/bot-settings.js` переведён на
  чтение bot-конфига через `SettingsPageConfigRuntime`, с fallback на старые
  globals только как мягкий compatibility path;
- `spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`,
  `settings-page-bootstrap-runtime.js`,
  `settings-location-wizard-runtime.js` и
  `settings-page-shell.js` очищены от plain-global слоя
  `requestSettingsModalClose`: close flow теперь идёт через injected shell API
  и `SettingsPageShell.requestCloseModal(...)`, а не через отдельный global
  helper;
- `ai-context/tasks/task-details/01-129.md` обновлён: в задаче зафиксировано,
  что и финальный page-level global compatibility слой уже в основном снят.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-config-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-location-wizard-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `node --check spring-panel/src/main/resources/static/js/bot-settings.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-config-runtime.js spring-panel/src/main/resources/static/js/settings-page-init-runtime.js spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js spring-panel/src/main/resources/static/js/settings-location-wizard-runtime.js spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/static/js/bot-settings.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-29_152107_settings-page-legacy-globals-final-cleanup.md`
