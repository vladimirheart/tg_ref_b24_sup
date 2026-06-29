# 2026-06-29 09:05:04 - dialog templates and parameters registry

## Промпты пользователя

- `давай следующий более широкий шаг по задаче 01-129`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-dialog-templates-runtime.js`
  теперь регистрирует весь declarative callback слой `dialog templates / auto-close`
  в `SettingsPageCallbackRegistry`: `add/remove/toggle` callbacks для template,
  rows, macro tags и auto-close больше не завязаны только на legacy globals;
- `spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js`
  теперь публикует `loadParameters()` через `SettingsPageCallbackRegistry`, так
  что bootstrap-хук из `settings-page-shell.js` может вызывать его без опоры на
  несуществующий legacy `window.loadParameters`;
- `ai-context/tasks/task-details/01-129.md` обновлён: зафиксировано расширение
  registry-покрытия на dialog-template callbacks и parameter-shell bootstrap hook.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-dialog-templates-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-dialog-templates-runtime.js spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js ai-context/tasks/task-details/01-129.md`
