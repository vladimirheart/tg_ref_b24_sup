# 2026-06-11 14:52:09 - settings page shell collapse navigation

## Промпты пользователя

- `хорошо, давай дальше`

## Что изменено

- продолжен `01-128`: в `settings-page-shell.js` добавлен общий declarative runtime для collapse-navigation через `data-settings-open-collapse`;
- shell теперь сам открывает нужный `collapse`, поддерживает keyboard activation для небуттонных trigger-элементов, умеет скроллить к секции и синхронизировать active-state внутри `data-settings-collapse-nav`;
- IT overview tiles в `settings/index.html` переведены с локального inline wiring на `data-settings-collapse-nav` + `data-settings-open-collapse`, а старый блок `activateItSectionTile`/`itSectionTilesContainer` удалён из giant inline script;
- quick actions в блоке `channels` тоже переведены на `data-settings-open-collapse`, поэтому из inline runtime удалены `openChannelsManageSection()` и привязка `data-channels-manage-open`.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`
