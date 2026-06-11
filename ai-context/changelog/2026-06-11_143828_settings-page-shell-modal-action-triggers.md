# 2026-06-11 14:38:28 - settings page shell modal action triggers

## Промпты пользователя

- `давай дальше с расширенным пакетом`

## Что изменено

- продолжен `01-128`: в `settings-page-shell.js` добавлен общий runtime-слой для declarative modal actions через `data-settings-open-modal` и `data-settings-hide-modal`;
- shell теперь умеет делать modal handoff без bootstrap data-api: сначала закрывать текущую settings-модалку, а после `hidden.bs.modal` открывать следующую;
- из `settings/index.html` на этот слой переведены shell-навигационные открытия модалок: заявки на восстановление, quick action добавления канала, кнопки добавления IT-сущностей и переходы из блока оформления;
- из `resolveSettingsTileForModal()` убран legacy fallback на `.settings-tile[data-bs-target="#..."]`, потому что overview tiles уже работают через собственный `data-settings-tile-target` contract.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/templates/settings/index.html`
