# 2026-06-29 17:30:51 - settings page init helper boundary cleanup

## Промпты пользователя

- `давай следующий более по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`
  больше не зависит напрямую от `window.getCookieValue`, `window.showPopup` и
  `window.showNotification`: добавлены локальный cookie parser и helper
  resolver'ы с fallback через `CommonUtils`;
- `settings-page-init-runtime.js` сохраняет close-flow через
  `SettingsPageShell.requestCloseModal(...)`, но page-specific helper boundary
  теперь уже опирается на namespace contract и локальные fallback'и, а не на
  plain-global функции;
- `spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
  переведён с `window.confirm` на native `globalThis.confirm` fallback, чтобы
  confirm boundary тоже не зависел от page-specific global wiring;
- `ai-context/tasks/task-details/01-129.md` обновлён: в задаче зафиксировано,
  что верхний helper/init boundary ещё сильнее сужен, а remaining scope
  сместился к namespace-level contracts и возможному финальному payload
  cleanup.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-init-runtime.js spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-29_173051_settings-page-init-helper-boundary-cleanup.md`
