# 2026-06-26 14:44:43 - settings dialog shell runtime

## Промпты пользователя

- `давай следующий шаг по задаче 01-129`

## Что изменено

- добавлен `spring-panel/src/main/resources/static/js/settings-dialog-shell-runtime.js`
  как отдельный page-level runtime для orchestration dialog settings collect flow;
- `spring-panel/src/main/resources/templates/settings/index.html` больше не
  держит inline `collectDialogSlaConfig()`: общий payload для
  `SLA/core + macro/client-context + workspace governance + external KPI`
  теперь собирается через `settingsDialogShellRuntime.collectDialogSlaConfig()`;
- обновлена карточка `ai-context/tasks/task-details/01-129.md`: зафиксирован
  новый слой `settings-dialog-shell-runtime.js` и сужение remaining scope до
  других page-level bridge/global wrapper хвостов.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-dialog-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-dialog-sla-core-runtime.js`
- `node --check %TEMP%/settings-index-inline-check.js`
- `git diff --check` (`CRLF` warnings only)
