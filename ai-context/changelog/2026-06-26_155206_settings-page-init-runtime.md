# 2026-06-26 15:52:06 - settings page init runtime

## Промпты пользователя

- `давай следующий шаг по задаче 01-129`

## Что изменено

- добавлен `spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`
  как финальный init-chain runtime для settings page;
- в новый runtime перенесена связка
  `SettingsPageConfigRuntime.build(...) -> publishLegacyGlobals(...) -> SettingsPageBootstrapRuntime.mount(...)`;
- `spring-panel/src/main/resources/templates/settings/index.html` ещё упрощён:
  inline-скрипт больше не держит callback-обвязку и ручной `build/publish/mount`,
  а только передаёт raw Thymeleaf payload в `SettingsPageInitRuntime.mount(...)`;
- карточка `ai-context/tasks/task-details/01-129.md` обновлена: remaining scope
  по template теперь сводится к очень короткому raw payload bridge.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-init-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-config-runtime.js`
- `node --check %TEMP%/settings-index-inline-check.js`
- `git diff --check` (`CRLF` warnings only)
