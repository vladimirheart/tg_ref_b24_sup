# 2026-06-15 12:32:57 - settings page shell close request event

## Промпты пользователя

- `давай дальше`

## Что изменено

- продолжен `01-128` ещё одним шагом по ослаблению coupling giant inline script к shell API: в `settings-page-shell.js` добавлен event-driven close request через `settings:close-modal` и публичный helper `requestCloseModal(...)`;
- shell теперь сам обрабатывает bubbling-запрос закрытия и решает, какую ближайшую `.modal` закрыть, вместо того чтобы template напрямую вызывал скрытие модалки;
- success-path-и и fallback-и в `settings/index.html` переведены с `hideClosestModal(...)` на более слабую семантику `requestCloseModal(...)` для partner contacts, network profiles, IT add modals, location wizard и channel shell;
- после миграции giant inline script больше не содержит прямых вызовов `hideModal(...)` и не управляет even “closest modal hide” как операцией shell-уровня, а только отправляет запрос на закрытие текущего modal context.

## Проверка

- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`
- `rg -n "SettingsPageShell\\.(hideClosestModal|requestCloseModal|hideModal)\\(" spring-panel/src/main/resources/templates/settings/index.html`
- `rg -n "settings:close-modal|requestCloseModal|hideClosestModal" spring-panel/src/main/resources/static/js/settings-page-shell.js`
