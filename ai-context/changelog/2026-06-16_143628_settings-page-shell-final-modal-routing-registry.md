# 2026-06-16 14:36:28 - settings page shell final modal routing registry

## Промпты пользователя

- `давай дальше`

## Что изменено

- в `spring-panel/src/main/resources/static/js/settings-page-shell.js` расширен `DEFAULT_SETTINGS_MODAL_ACTIONS`: туда вынесены оставшиеся простые modal open/hide маршруты для password reset requests, location wizard, IT add modals, panel design shortcuts и add channel;
- shell runtime теперь полностью разрешает modal routing для этих триггеров через registry selectors и больше не требует `data-settings-open-modal`, `data-settings-hide-modal` и `data-settings-action-callback` в template;
- в `spring-panel/src/main/resources/templates/settings/index.html` modal routing переведён на доменные атрибуты `data-users-open-password-reset`, `data-location-wizard-open`, `data-it-equipment-add-open`, `data-it-connection-add-open`, `data-panel-design-open-*`, `data-add-channel-open`;
- после этого в template не осталось `data-settings-open-modal`, `data-settings-hide-modal` и `data-settings-action-callback`;
- блоки `publicform` и `questions_cfg` не затрагивались.

## Проверка

- `rg -n "data-settings-open-modal|data-settings-hide-modal|data-settings-action-callback" spring-panel/src/main/resources/templates/settings/index.html`
- `Select-String -Path 'spring-panel/src/main/resources/static/js/settings-page-shell.js' -Pattern 'data-users-open-password-reset|data-location-wizard-open|data-it-equipment-add-open|data-panel-design-open-appearance|data-add-channel-open'`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`
