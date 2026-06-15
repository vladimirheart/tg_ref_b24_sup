# 2026-06-15 17:24:06 - settings page shell modal lifecycle registry

## Промпты пользователя

- `продолжай`

## Что изменено

- в `spring-panel/src/main/resources/static/js/settings-page-shell.js` добавлен `DEFAULT_SETTINGS_MODAL_LIFECYCLE_CALLBACKS` с runtime-регистром lifecycle callback-ов для settings-модалок;
- shell теперь разрешает lifecycle callback по приоритету `data-settings-on-*` override -> runtime registry по `modal.id`, поэтому orchestration можно держать вне template markup;
- из `spring-panel/src/main/resources/templates/settings/index.html` убраны `data-settings-on-show`, `data-settings-on-shown` и `data-settings-on-hidden` для users, locations, parameters, legal entities, reporting, manager bindings, channel shell и связанных child-модалок;
- блоки `publicform` и `questions_cfg` не затрагивались.

## Проверка

- `rg -n "data-settings-on-show|data-settings-on-shown|data-settings-on-hide|data-settings-on-hidden" spring-panel/src/main/resources/templates/settings/index.html`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `Select-String -Path 'spring-panel/src/main/resources/static/js/settings-page-shell.js' -Pattern 'DEFAULT_SETTINGS_MODAL_LIFECYCLE_CALLBACKS|resolveSettingsLifecycleCallbackName'`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`
