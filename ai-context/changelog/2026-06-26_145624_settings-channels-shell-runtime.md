# 2026-06-26 14:56:24 - settings channels shell runtime

## Промпты пользователя

- `давай следующий, но крупнее предыдущего, шаг по задаче 01-129`

## Что изменено

- добавлен `spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js`
  как крупный page-level shell для subdomain `channels`;
- в новый runtime перенесены channel registry/editor state, mount-цепочка вокруг
  `channel templates / catalog / bot runtime / integration network / editor shell /
  editor persistence / editor controls`, helper-слой для
  `buildChannelQuestionsCfgPayload()` и весь orchestration/listener пакет для
  `initChannelsManagement()`;
- `spring-panel/src/main/resources/templates/settings/index.html` радикально
  упрощён: большой inline channel shell блок заменён на один
  `SettingsChannelsShellRuntime.mount(...)`;
- карточка `ai-context/tasks/task-details/01-129.md` обновлена под новый
  remaining scope после выноса channel shell.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-save-runtime.js`
- `node --check %TEMP%/settings-index-inline-check.js`
- `git diff --check` (`CRLF` warnings only)
