# 2026-06-15 16:33:07 - settings page shell close request helper

## Промпты пользователя

- `продолжай`

## Что изменено

- в `spring-panel/src/main/resources/templates/settings/index.html` добавлен локальный helper `requestSettingsModalClose(...)`, который отправляет bubbling-событие `settings:close-modal` вместо прямого вызова shell API;
- success-path и delete-path в модалках partner contacts, network profiles, IT add forms, location wizard, VK webhook и channel shell переведены с `window.SettingsPageShell.requestCloseModal(...)` на этот helper;
- giant inline script больше не содержит прямых обращений к `window.SettingsPageShell`, поэтому template ещё слабее связан с runtime-слоем page shell;
- блоки `publicform` и `questions_cfg` в этом пакете не затрагивались.

## Проверка

- `rg -n "SettingsPageShell\\." spring-panel/src/main/resources/templates/settings/index.html`
- `rg -n "requestSettingsModalClose\\(" spring-panel/src/main/resources/templates/settings/index.html`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js`
