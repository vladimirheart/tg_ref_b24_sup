# 2026-06-30 17:21:12 - dialogs shell column state cleanup

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-shell-runtime.js`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промт пользователя:
```text
теперь давай "добрать ещё тонкий column/layout glue, если вынос даст заметную пользу;"
```
- Что сделано:
  в `dialogs-shell-runtime.js` перенесён bounded column/layout cleanup:
  `loadColumnState`, `persistColumnState`, `applyColumnState`,
  `buildColumnsList`, `syncColumnsList` и wiring модалки колонок.
- Что сделано:
  `dialogs.js` очищен от inline column state/layout glue; размер entrypoint
  снижен примерно до `4070` строк.
- Что сделано:
  audit/roadmap обновлены под новый размер и финальный remaining focus вокруг
  regression pass по уже вынесенным runtime-модулям.
