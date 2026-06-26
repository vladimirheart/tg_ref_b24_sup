# 2026-06-26 14:13:10 - settings dialog sla macro client context collect

## Промпты пользователя

- `давай следующий шаг по задаче 01-129`

## Что изменено

- продолжен `01-129`: в `spring-panel/src/main/resources/static/js/settings-dialog-sla-core-runtime.js`
  добавлен `collectMacroClientContextConfig()`, который забрал из template remaining
  collect/validation слой для `macro/client-context`, macro governance и workspace
  client thresholds;
- `spring-panel/src/main/resources/templates/settings/index.html` упрощён:
  `collectDialogSlaConfig()` больше не сериализует этот payload inline, а только
  оркестрирует `settingsDialogSlaCoreRuntime`, `settingsDialogWorkspaceGovernanceRuntime`
  и `settingsDialogWorkspaceExternalKpiRuntime`;
- карточка `ai-context/tasks/task-details/01-129.md` обновлена под новое состояние
  задачи: зафиксировано, что `macro/client-context` уже вынесен в runtime, а
  remaining scope сузился до page-level bridge / orchestration хвостов.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-dialog-sla-core-runtime.js`
- `node --check %TEMP%/settings-index-inline-check.js`
- `git diff --check` (`CRLF` warnings only)
