# 2026-06-15 12:02:56 - settings page shell close nearest modal

## Промпты пользователя

- `продолжи выполнять задачу 01-128`

## Что изменено

- продолжен `01-128` generic close-flow пакетом: в `settings-page-shell.js` добавлен helper `hideClosestModal(...)`, чтобы giant inline script мог закрывать текущую settings-модалку по DOM-контексту, а не по жёстко прошитому modal id/element;
- `window.SettingsPageShell` расширен `getClosestModalElement` и `hideClosestModal`, так что modal shell теперь сам держит логику поиска ближайшей `.modal`;
- success-path-и и runtime-fallback-и в `settings/index.html` переведены с прямых `hideModal(partnerContactEditorModalEl | networkProfileModalEl | ... )` на `hideClosestModal(...)` через форму, кнопку или editor-card контекст;
- после миграции giant inline script перестал знать о конкретных modal element переменных в типовых close-after-success сценариях partner contacts, network profiles, IT add modals, location wizard и channel shell.

## Проверка

- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`
- `rg -n "SettingsPageShell\\.(showModal|hideModal|hideClosestModal)\\(" spring-panel/src/main/resources/templates/settings/index.html`
- `rg -n "hideClosestModal|getClosestModalElement" spring-panel/src/main/resources/static/js/settings-page-shell.js`
