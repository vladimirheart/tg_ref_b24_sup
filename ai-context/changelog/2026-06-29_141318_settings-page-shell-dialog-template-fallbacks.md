# 2026-06-29 14:13:18 - settings page shell dialog template fallbacks

## Промпты пользователя

- `хорошо, давай дальше`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-dialog-templates-runtime.js`
  получил полный namespace-object API поверх mount-слоя: наружу теперь
  проброшены forwarders для `add/remove*Template`, `add/remove*Row`,
  `addAutoCloseTemplate`, `removeAutoCloseTemplate` и
  `toggleDialogTemplateEditor()`;
- `spring-panel/src/main/resources/static/js/settings-page-shell.js`
  расширен последним оставшимся declarative callback block для
  `dialog templates`, так что page shell теперь умеет резолвить через
  `SettingsDialogTemplatesRuntime` и этот кластер, а не только через registry
  или legacy `window[...]`;
- отдельной проверкой подтверждено, что после пакета для declarative
  callbacks/lifecycle/bootstrap слоя `settings-page-shell` больше не осталось
  unmapped callback-имён;
- `ai-context/tasks/task-details/01-129.md` обновлён: в карточке задачи
  зафиксирован новый рубеж и скорректирован практический остаток по `01-129`.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-dialog-templates-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-dialog-templates-runtime.js spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-29_141318_settings-page-shell-dialog-template-fallbacks.md`
