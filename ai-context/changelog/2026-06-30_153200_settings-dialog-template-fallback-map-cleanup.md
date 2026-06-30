# 2026-06-30 15:32:00 — settings dialog template fallback map cleanup

## user prompt

> давай следующий шаг

## what changed

- в `spring-panel/src/main/resources/static/js/settings-page-shell.js` убраны
  runtime namespace fallback entries для dialog template callback-имён
  (`add/remove/toggle` operations), которые уже публикуются через
  `SettingsPageCallbackRegistry` внутри `settings-dialog-templates-runtime.js`;
- после этого для dialog template user-actions page shell больше не держит
  дублирующий `SettingsDialogTemplatesRuntime` fallback-слой поверх registry,
  а опирается на единый registry-contract;
- в `ai-context/tasks/task-details/01-129.md` зафиксирован этот шаг и
  уточнён remaining scope: дальше дочищать остальные subdomain wrapper/fallback
  слои по тому же паттерну.

## verification

- `rg -n "addAutoCloseTemplate|toggleDialogTemplateEditor|removeAutoCloseTemplate" spring-panel/src/main/resources/static/js/settings-page-shell.js spring-panel/src/main/resources/static/js/settings-dialog-templates-runtime.js -S`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
