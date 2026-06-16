# 2026-06-16 10:25:58 - settings page shell global close helper

## Промпты пользователя

- `так выполняй эти задачи`
- `продолжай работу по задаче 01-128`

## Что изменено

- в `spring-panel/src/main/resources/static/js/settings-page-shell.js` добавлен глобальный helper `window.requestSettingsModalClose`, который проксирует close-request в shell runtime через уже существующий `requestCloseSettingsModal(...)`;
- из `spring-panel/src/main/resources/templates/settings/index.html` удалён локальный `function requestSettingsModalClose(...)`, который раньше сам собирал `CustomEvent('settings:close-modal', ...)`;
- giant inline script страницы настроек продолжает вызывать `requestSettingsModalClose(...)`, но больше не содержит внутри себя shell event plumbing и не конструирует shell-события напрямую;
- блоки `publicform` и `questions_cfg` не менялись.

## Проверка

- `rg -n "function requestSettingsModalClose|requestSettingsModalClose\(" spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`
