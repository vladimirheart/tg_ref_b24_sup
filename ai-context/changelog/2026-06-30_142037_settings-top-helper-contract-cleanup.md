# 2026-06-30 14:20:37 - settings top helper contract cleanup

## Промпты пользователя

- `тогда давай дочищать верхние fallback/helper хвосты`

## Что изменено

- `spring-panel/src/main/resources/static/js/common.js`
  расширен export `CommonUtils`: туда добавлен `getCookieValue`, чтобы верхний
  settings init слой больше не держал отдельный дублирующий helper только ради
  чтения cookie;
- `spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`
  перестроен в единый provider верхнего helper-contract: теперь он
  централизованно собирает и прокидывает в bootstrap уже разрешённые
  `getCookieValue`, `requestSettingsModalClose`, `showPopup`,
  `showNotification`, `confirmDialog` и `promptDialog`;
- `spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
  подсушен: локальный второй слой browser/global fallback'ов снят, bootstrap
  теперь использует уже переданные helper-функции как входной contract и
  держит только безопасные noop/null fallback'и на случай ручного вызова вне
  обычного init-flow;
- `ai-context/tasks/task-details/01-129.md` обновлён: зафиксировано, что
  верхний helper-layer уже централизован в `SettingsPageInitRuntime`, а
  remaining scope ещё сильнее сместился к остаточным page hooks и совместимости
  вокруг `bot-settings.js`, а не к bootstrap/runtime wiring.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/common.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/common.js spring-panel/src/main/resources/static/js/settings-page-init-runtime.js spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-30_142037_settings-top-helper-contract-cleanup.md`
