# 2026-06-29 17:40:31 - settings theme runtime contract

## Промпты пользователя

- `давай следующий шаг по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/theme.js` теперь публикует
  `ThemeRuntime` как явный namespace contract поверх существующих
  `iguanaTheme`/`iguanaThemePalette` API;
- `spring-panel/src/main/resources/static/js/settings-page-shell.js`
  переведён на `ThemeRuntime` для theme/palette sync в settings page: shell
  больше не зависит напрямую от `window.iguanaTheme` и
  `window.iguanaThemePalette`, а использует единый runtime resolver;
- `ai-context/tasks/task-details/01-129.md` обновлён: в задаче зафиксировано,
  что theme-boundary в page shell тоже переведён на namespace contract и
  remaining scope ещё сильнее смещён к mount/runtime orchestration.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/theme.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/theme.js spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-29_174031_settings-theme-runtime-contract.md`
