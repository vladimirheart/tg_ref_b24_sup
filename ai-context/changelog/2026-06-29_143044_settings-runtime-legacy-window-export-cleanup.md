# 2026-06-29 14:30:44 - settings runtime legacy window export cleanup

## Промпты пользователя

- `давай следующий шаг по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-admin-shell-runtime.js`,
  `settings-reporting-manager-bindings.js`,
  `settings-network-profiles-runtime.js`,
  `settings-location-wizard-runtime.js`,
  `settings-appearance-runtime.js`,
  `settings-page-bootstrap-runtime.js` и
  `settings-dialog-templates-runtime.js` очищены от legacy `window.*`
  compatibility exports для page-shell callback'ов, которые уже покрыты
  `SettingsPageCallbackRegistry` и `Settings*Runtime` namespace contracts;
- регистрация callback'ов в `SettingsPageCallbackRegistry` в этих runtime теперь
  идёт напрямую через методы runtime, без промежуточных global wrappers;
- `ai-context/tasks/task-details/01-129.md` обновлён: в задаче зафиксирован
  переход от построения fallback-слоя к фактическому удалению части legacy
  global exports.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-admin-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-reporting-manager-bindings.js`
- `node --check spring-panel/src/main/resources/static/js/settings-network-profiles-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-location-wizard-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-appearance-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-dialog-templates-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-admin-shell-runtime.js spring-panel/src/main/resources/static/js/settings-reporting-manager-bindings.js spring-panel/src/main/resources/static/js/settings-network-profiles-runtime.js spring-panel/src/main/resources/static/js/settings-location-wizard-runtime.js spring-panel/src/main/resources/static/js/settings-appearance-runtime.js spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js spring-panel/src/main/resources/static/js/settings-dialog-templates-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-29_143044_settings-runtime-legacy-window-export-cleanup.md`
