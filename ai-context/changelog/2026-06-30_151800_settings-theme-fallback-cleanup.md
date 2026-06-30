# 2026-06-30 15:18:00 — settings theme fallback cleanup

## user prompt

> давай следующий шаг. и расскажи что именно осталось.

## what changed

- в `spring-panel/src/main/resources/static/js/settings-page-shell.js` удалён
  legacy fallback на `globalThis.iguanaTheme` и `globalThis.iguanaThemePalette`
  внутри `resolveThemeRuntime()`;
- settings page теперь в theme-sync слое опирается только на явный
  `ThemeRuntime` contract через `SettingsRuntimeAccess`, без старого
  compatibility-resolver внутри shell;
- в `ai-context/tasks/task-details/01-129.md` обновлено описание этого
  подэтапа: зафиксировано, что last-mile fallback в theme resolver уже снят.

## verification

- `rg -n "iguanaTheme|iguanaThemePalette" spring-panel/src/main/resources/static/js/settings-page-shell.js -S`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
