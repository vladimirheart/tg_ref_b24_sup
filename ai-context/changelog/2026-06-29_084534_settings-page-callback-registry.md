# 2026-06-29 08:45:34 - settings page callback registry

## Промпты пользователя

- `давай следующий более широкий шаг по задаче 01-129`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-page-callback-registry.js`
  добавлен новый registry для именованных callback'ов settings page с API
  `register`, `registerMany`, `resolve` и `unregister`;
- `spring-panel/src/main/resources/static/js/settings-page-shell.js`
  переведён на `registry-first` резолвинг для modal action callbacks,
  declarative callbacks, modal lifecycle hooks и page bootstrap, с fallback на
  legacy `window[...]`; из default bootstrap списка убраны уже устаревшие
  entry-points `initAutoCloseTemplates`, `initDialogTemplates`,
  `initTimeMetricsControls`, `initWorkspaceGovernanceUtcTimestampFields`,
  `initDialogStatusBadges` и `renderNetworkProfiles`;
- `spring-panel/src/main/resources/templates/settings/index.html`
  теперь подключает `settings-page-callback-registry.js` до
  `settings-page-shell.js`;
- ключевые runtime-модули (`settings-page-bootstrap-runtime.js`,
  `settings-appearance-runtime.js`, `settings-admin-shell-runtime.js`,
  `settings-location-wizard-runtime.js`, `settings-locations-iiko-runtime.js`,
  `settings-locations-tree-runtime.js`, `settings-reporting-manager-bindings.js`,
  `settings-network-profiles-runtime.js`, `settings-channels-shell-runtime.js`,
  `settings-partner-contacts-runtime.js`, `settings-parameters-runtime.js`,
  `settings-legal-entities-runtime.js`, `settings-it-connections-runtime.js`,
  `settings-it-equipment-runtime.js`, `settings-integration-network-runtime.js`,
  `settings-channel-editor-shell-runtime.js`,
  `settings-channels-catalog-runtime.js`) теперь регистрируют свой runtime API
  в `SettingsPageCallbackRegistry`, не ломая существующие global exports;
- `ai-context/tasks/task-details/01-129.md` обновлён: в карточке задачи
  зафиксирован новый callback-registry слой и уточнён оставшийся scope.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-callback-registry.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-appearance-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-admin-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-location-wizard-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-reporting-manager-bindings.js`
- `node --check spring-panel/src/main/resources/static/js/settings-network-profiles-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-partner-contacts-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-parameters-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-legal-entities-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-it-connections-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-it-equipment-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-integration-network-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channel-editor-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channels-catalog-runtime.js`
- `git diff --check`
