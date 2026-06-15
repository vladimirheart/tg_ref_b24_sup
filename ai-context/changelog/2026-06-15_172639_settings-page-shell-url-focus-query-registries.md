# 2026-06-15 17:26:39 - settings page shell url focus query registries

## Промпты пользователя

- `давай дальше`

## Что изменено

- в `spring-panel/src/main/resources/static/js/settings-page-shell.js` добавлены runtime-регистры `DEFAULT_SETTINGS_URL_MODAL_NAMES`, `DEFAULT_SETTINGS_MODAL_DEFAULT_TABS`, `DEFAULT_SETTINGS_MODAL_FOCUS_TARGETS`, `DEFAULT_SETTINGS_QUERY_DRIVEN_MODALS` и `DEFAULT_SETTINGS_URL_PARAMS`;
- shell теперь разрешает URL modal aliases, default tab reset, autofocus и query-driven modal open через runtime по `modal.id`, оставляя `data-settings-*` только как override-механизм;
- из `spring-panel/src/main/resources/templates/settings/index.html` убраны `data-settings-url-modal`, `data-settings-url-open-param`, `data-settings-url-legacy-param`, `data-settings-reset-tab`, `data-settings-focus-target`, `data-settings-focus-select`, `data-settings-query-open` и `data-settings-query-clear-param`;
- блоки `publicform` и `questions_cfg` не менялись.

## Проверка

- `rg -n "data-settings-(reset-tab|focus-target|focus-select|query-open|query-clear-param|url-modal|url-open-param|url-legacy-param)" spring-panel/src/main/resources/templates/settings/index.html`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `Select-String -Path 'spring-panel/src/main/resources/static/js/settings-page-shell.js' -Pattern 'DEFAULT_SETTINGS_URL_MODAL_NAMES|DEFAULT_SETTINGS_MODAL_FOCUS_TARGETS|DEFAULT_SETTINGS_MODAL_DEFAULT_TABS|DEFAULT_SETTINGS_QUERY_DRIVEN_MODALS'`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`
