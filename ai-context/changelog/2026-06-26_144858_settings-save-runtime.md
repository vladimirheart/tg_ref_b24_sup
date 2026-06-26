# 2026-06-26 14:48:58 - settings save runtime

## Промпты пользователя

- `давай следующий шаг по задаче 01-129`

## Что изменено

- добавлен `spring-panel/src/main/resources/static/js/settings-save-runtime.js`
  как отдельный page-level runtime для общего `/settings` save flow;
- `spring-panel/src/main/resources/templates/settings/index.html` больше не
  держит большое inline-тело `saveSettings()`: orchestration auto-close,
  time metrics, dialog shell, summary badges, dialog templates, client statuses,
  locations sync payload и network profiles перенесены в `settingsSaveRuntime`;
- карточка `ai-context/tasks/task-details/01-129.md` обновлена: зафиксирован
  новый runtime-слой для общего save orchestration и further narrowing remaining
  template scope.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-save-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-dialog-shell-runtime.js`
- `node --check %TEMP%/settings-index-inline-check.js`
- `git diff --check` (`CRLF` warnings only)
