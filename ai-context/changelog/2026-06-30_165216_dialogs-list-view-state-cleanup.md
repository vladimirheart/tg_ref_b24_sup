# 2026-06-30 16:52:16 - dialogs list view-state cleanup

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-list-runtime.js`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промт пользователя:
```text
тогда продолжай по "Добить остаточные тонкие helper-хвосты в dialogs.js, только если вынос ещё даёт реальную пользу: в первую очередь normalizePageSize, normalizeDialogView и мелкий column/view-state glue."
```
- Что сделано:
  в `dialogs-list-runtime.js` перенесён bounded list view-state cleanup:
  `normalizePageSize`, `normalizeDialogView` и binding для `pageSize`,
  `slaWindow`, `sortMode` и `viewTabs`.
- Что сделано:
  `dialogs.js` очищен от этих inline list helper'ов и event wiring; размер
  entrypoint снижен примерно до `4130` строк.
- Что сделано:
  audit/roadmap обновлены под новый размер и narrowed remaining focus вокруг
  тонкого column/layout glue и финального regression pass.
