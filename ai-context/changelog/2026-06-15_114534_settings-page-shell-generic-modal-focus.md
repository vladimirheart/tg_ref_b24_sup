# 2026-06-15 11:45:34 - settings page shell generic modal focus

## Промпты пользователя

- `давай дальше`

## Что изменено

- продолжен `01-128` generic shell-пакетом: в `settings-page-shell.js` добавлен контракт `data-settings-focus-target`, чтобы child modal мог описывать автофокус декларативно, без отдельной `focus...SettingsModal` callback-функции в giant inline script;
- `networkProfileEditorModal` переведён на новый focus-контракт через `data-settings-focus-target` и `data-settings-focus-select`, поэтому его старый `focusNetworkProfileSettingsModal` больше не нужен;
- `itConnectionAddModal` и `itEquipmentAddModal` тоже переведены на декларативный shell autofocus, а их отдельные focus-callback-ветки удалены из template script;
- для `itConnectionAddModal` и `itEquipmentAddModal` добавлены явные `data-settings-suspend-parent="itConnectionsModal"`, чтобы IT child modals использовали тот же parent/child shell-контур, что и остальные вложенные settings-модалки.

## Проверка

- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`
- `Select-String -Path 'spring-panel/src/main/resources/templates/settings/index.html' -Pattern 'data-settings-focus-target','data-settings-focus-select','data-settings-suspend-parent="itConnectionsModal"'`
- `rg -n -F "data-settings-focus-target" spring-panel/src/main/resources/static/js/settings-page-shell.js`
