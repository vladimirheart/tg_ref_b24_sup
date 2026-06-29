# 2026-06-29 14:33:21 - settings channels legacy window export cleanup

## Промпты пользователя

- `давай следующий шаг по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js`,
  `settings-channels-catalog-runtime.js`,
  `settings-channel-editor-shell-runtime.js` и
  `settings-integration-network-runtime.js` очищены от legacy `window.*`
  compatibility exports для channels page-shell callback'ов;
- `SettingsPageCallbackRegistry` в этих runtime теперь получает прямые методы
  runtime для `initChannelsManagement`, `addChannel`,
  `prepareAddChannelSettingsModal`, `prepareVkWebhookSettingsTrigger`,
  `prepareChannelEditorSettingsTrigger`,
  `resetChannelEditorSettingsModal`,
  `prepareIntegrationNetworkProfileSettingsTrigger` и
  `resetIntegrationNetworkProfileSettingsModal`, без промежуточных global
  wrappers;
- `ai-context/tasks/task-details/01-129.md` обновлён: в задаче зафиксирован
  следующий cleanup-пакет и скорректирован оставшийся фронт работ.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channels-catalog-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channel-editor-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-integration-network-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js spring-panel/src/main/resources/static/js/settings-channels-catalog-runtime.js spring-panel/src/main/resources/static/js/settings-channel-editor-shell-runtime.js spring-panel/src/main/resources/static/js/settings-integration-network-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-29_143321_settings-channels-legacy-window-export-cleanup.md`
