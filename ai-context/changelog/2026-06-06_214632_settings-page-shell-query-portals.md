# 2026-06-06 21:46:32 - settings page shell query portals

## Промпты пользователя

- `давай дальше`

## Что изменено

- продолжен `01-128`: в `settings-page-shell.js` добавлен generic `data-settings-portal-body`
  bootstrap для modal-порталов в `document.body`, чтобы shell-слой больше не жил в inline script;
- `partnerContactEditorModal` в `settings/index.html` переведён на декларативный hook
  `data-settings-portal-body`, а локальный `appendChild(document.body)` удалён из template runtime;
- в `settings-page-shell.js` добавлен generic query-driven modal open через
  `data-settings-query-open`, `data-settings-query-param` и `data-settings-query-clear-param`;
- `itEquipmentAddModal` теперь открывается из URL через декларативный query hook, а локальный
  helper `openItEquipmentModalFromQuery()` и fallback-bootstrap через `itConnectionsModal`
  удалены из giant inline script;
- generic URL bootstrap страницы настроек теперь умеет не только primary `data-settings-url-modal`,
  но и secondary query-driven модалки с очисткой query-параметра через `history.replaceState`.

## Проверка

- `C:\Program Files\nodejs\node.exe --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/changelog/2026-06-06_214632_settings-page-shell-query-portals.md`
