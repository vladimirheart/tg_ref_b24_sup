# 2026-06-29 17:08:21 - settings confirm dialog boundary cleanup

## Промпты пользователя

- `давай следующий более по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-parameters-runtime.js`,
  `settings-it-connections-runtime.js`,
  `settings-it-equipment-runtime.js`,
  `settings-network-profiles-runtime.js` и
  `settings-channel-editor-persistence-runtime.js` больше не используют прямой
  `window.confirm(...)` fallback: confirm-сценарии теперь идут только через
  injected `confirmDialog` contract;
- `spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js`
  и `settings-channels-shell-runtime.js` тоже перестали держать собственный
  direct browser-confirm fallback внутри нижележащих runtime wiring, оставляя
  этот fallback только на page bootstrap boundary;
- `ai-context/tasks/task-details/01-129.md` обновлён: в задаче зафиксировано,
  что confirm-layer внутри settings runtime тоже централизован, а remaining
  scope ещё сильнее смещён к `settings-page-init-runtime.js`, page-level helper
  boundary и дальнейшему cross-runtime cleanup.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-parameters-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-it-connections-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-it-equipment-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-network-profiles-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channel-editor-persistence-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-parameters-runtime.js spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js spring-panel/src/main/resources/static/js/settings-it-connections-runtime.js spring-panel/src/main/resources/static/js/settings-it-equipment-runtime.js spring-panel/src/main/resources/static/js/settings-network-profiles-runtime.js spring-panel/src/main/resources/static/js/settings-channel-editor-persistence-runtime.js spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-29_170821_settings-confirm-dialog-boundary-cleanup.md`
