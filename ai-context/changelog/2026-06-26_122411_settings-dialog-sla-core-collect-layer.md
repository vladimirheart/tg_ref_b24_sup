# 2026-06-26 12:24:11 - settings dialog sla core collect layer

## Промпты пользователя

- `давай следующий шаг по задаче 01-129`

## Что изменено

- продолжен `01-129`: `spring-panel/src/main/resources/static/js/settings-dialog-sla-core-runtime.js`
  расширен новым `collectSlaCoreConfig()` и теперь держит не только
  init/hydration/helper-слой, но и первый крупный collect/validation пакет для
  `SLA/workspace shell/A-B/guardrails`;
- `spring-panel/src/main/resources/templates/settings/index.html` упрощён:
  `collectDialogSlaConfig()` больше не сериализует inline базовый SLA/workspace
  shell слой вручную, а получает этот payload через
  `settingsDialogSlaCoreRuntime.collectSlaCoreConfig()` и оставляет в template
  только remaining macro/client-context serialization плюс orchestration соседних
  runtime;
- карточка `ai-context/tasks/task-details/01-129.md` обновлена: зафиксирован
  перенос collect-layer для `SLA/workspace shell/A-B/guardrails`, а remaining
  scope сужен до `macro/client-context` payload и оставшихся bridge точек.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-dialog-sla-core-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `node --check <temp settings inline script extract>`
- `git diff --check`
