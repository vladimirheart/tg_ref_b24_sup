# 2026-06-11 15:02:39 - settings page shell direct modal api cleanup

## Промпты пользователя

- `давай дальше но небольшим пакетом`

## Что изменено

- продолжен `01-128` маленьким cleanup-пакетом без захода в большой split: `parameterItemsModal` больше не кэширует bootstrap modal instance внутри giant inline script и открывается напрямую через `window.SettingsPageShell.showModal(...)`;
- `networkProfileEditorModal` тоже переведён с cached instance на прямые вызовы `window.SettingsPageShell.showModal(...)` / `hideModal(...)`, чтобы template меньше зависел от локального хранения bootstrap modal objects;
- проверки перерисовки `parameterModalType` теперь опираются на наличие `parameterModalEl`, а не на заранее созданный modal instance;
- открытие `integrationNetworkProfileEditorModal` тоже переведено на прямой `showModal(...)`, но существующий cached instance для сценариев закрытия пока оставлен как есть, чтобы пакет остался небольшим и без лишнего риска.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html`
