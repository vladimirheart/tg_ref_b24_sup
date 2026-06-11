# 2026-06-11 21:45:17 - settings page shell integration modal hide cleanup

## Промпты пользователя

- `дальше`

## Что изменено

- продолжен `01-128` очень маленьким пакетом: `integrationNetworkProfileEditorModal` больше не кэширует bootstrap modal instance внутри giant inline script;
- оба сценария закрытия editor-модалки профиля маршрута теперь идут через прямой `window.SettingsPageShell.hideModal(integrationNetworkProfileEditorModalEl)`;
- локальный runtime `settings/index.html` стал на один cached modal object проще, без изменения доменной логики формы профиля маршрута.

## Проверка

- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html`
