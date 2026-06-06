# 2026-06-06 21:37:15 - settings page shell secondary modal hooks

## Промпты пользователя

- `дальше`

## Что изменено

- продолжен `01-128`: вторичные modal lifecycle-сценарии страницы настроек переведены на
  декларативные `data-settings-on-*` hooks для `locationWizardModal`, `parameterItemsModal`,
  `networkProfileEditorModal`, `integrationNetworkProfileEditorModal`, `itConnectionAddModal`,
  `itEquipmentAddModal`, `reportingModal`, `managerBindingsModal` и `addChannelModal`;
- в `settings/index.html` оставлены только тонкие `window.*` callback entrypoint'ы для
  `reset/focus/prepare` сценариев этих модалок, а прямые `show/shown/hidden` listener-блоки
  удалены из giant inline script;
- `reporting` и `manager bindings` теперь тоже заходят в modal bootstrap через внешний
  page-shell runtime, а не регистрируют `show.bs.modal` listeners внутри `initReporting`;
- `settings-page-shell.js` обновлён так, чтобы generic lifecycle dispatcher безопасно
  обрабатывал асинхронные callback'и и логировал ошибки вместо unhandled promise rejection.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-06_213715_settings-page-shell-secondary-modal-hooks.md`
