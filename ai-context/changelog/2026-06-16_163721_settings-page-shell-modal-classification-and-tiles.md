# 2026-06-16 16:37:21 - settings page shell modal classification and tiles

## Промпты пользователя

- `давай дальше более широким пакетом`

## Что изменено

- в `spring-panel/src/main/resources/static/js/settings-page-shell.js` добавлены runtime-регистры `DEFAULT_SETTINGS_PRIMARY_MODAL_IDS`, `DEFAULT_SETTINGS_SHEET_MODAL_IDS`, `DEFAULT_SETTINGS_BODY_PORTAL_MODAL_IDS` и `DEFAULT_SETTINGS_TILE_MODAL_TARGETS`;
- shell runtime теперь определяет primary modals, sheet modals, portal-to-body modals и overview tile routing по runtime-картам, а не по обязательным `data-settings-*` атрибутам в template;
- `initSettingsPrimaryModals()`, `initSettingsBodyPortals()`, tile activation и tile open flow переведены на новые runtime-resolver helpers;
- в `spring-panel/src/main/resources/templates/settings/index.html` убраны `data-settings-primary-modal`, `data-settings-sheet`, `data-settings-portal-body` и `data-settings-tile-target`, а overview tiles переведены на компактные key-значения в `data-settings-tile`;
- блоки `publicform` и `questions_cfg` не затрагивались.

## Проверка

- `rg -n "data-settings-primary-modal|data-settings-sheet|data-settings-portal-body|data-settings-tile-target" spring-panel/src/main/resources/templates/settings/index.html`
- `Select-String -Path 'spring-panel/src/main/resources/static/js/settings-page-shell.js' -Pattern 'DEFAULT_SETTINGS_PRIMARY_MODAL_IDS|DEFAULT_SETTINGS_SHEET_MODAL_IDS|DEFAULT_SETTINGS_BODY_PORTAL_MODAL_IDS|DEFAULT_SETTINGS_TILE_MODAL_TARGETS|resolveSettingsTileModalTarget|isSettingsPrimaryModal|isSettingsSheetModal|shouldPortalSettingsModalToBody'`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`
