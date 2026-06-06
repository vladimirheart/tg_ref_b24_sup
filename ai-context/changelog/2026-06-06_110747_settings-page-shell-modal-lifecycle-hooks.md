# 2026-06-06 11:07:47 - settings page shell modal lifecycle hooks

## Промпты пользователя

- `давай дальше`

## Что изменено

- продолжен `01-128`: в `settings-page-shell.js` добавлен generic lifecycle dispatcher для modal-хуков
  `data-settings-on-show`, `data-settings-on-shown`, `data-settings-on-hide`, `data-settings-on-hidden`;
- в `settings-page-shell.js` добавлен generic reset стартовой вкладки через
  `data-settings-reset-tab`, чтобы modal UI-bootstrap больше не жил в giant inline script;
- `legalEntitiesModal`, `parametersModal`, `partnerContactEditorModal` и `channelEditorModal`
  в `settings/index.html` переведены на декларативные `data-*` hooks вместо локальных
  `show/hidden` listener-блоков;
- доменные render/reset сценарии для `legal entities`, `partner contacts` и `channel editor`
  оставлены в template как тонкие `window.*` callback entrypoints, а orchestration вынесен
  во внешний page-shell runtime;
- из giant inline script удалены прямые lifecycle-listener'ы для `legalEntitiesModal`,
  `parametersModal`, `partnerContactEditorModal` и `channelEditorModal`, а также убраны
  больше не нужные локальные ссылки на эти modal-bootstrap hook'и.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-06_110747_settings-page-shell-modal-lifecycle-hooks.md`
