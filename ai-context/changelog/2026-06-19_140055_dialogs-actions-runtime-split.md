# 2026-06-19 14:00:55 - dialogs actions runtime split

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-actions-runtime.js`,
  `spring-panel/src/main/resources/templates/dialogs/index.html`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий шаг
```
- Что сделано:
  добавлен bounded runtime `dialogs-actions-runtime.js`, который забрал
  row/details/workspace quick actions, action-menu wiring и button-state sync.
- Что сделано:
  `dialogs.js` переведён на thin delegates для `take/snooze/resolve/reopen/spam`
  helper'ов и больше не держит primary inline ownership этих action flows.
- Что сделано:
  task-detail `01-130` и roadmap обновлены под новый execution-срез, где
  следующими bounded целями остаются `macro workflow`,
  `notifications refresh` и дочистка orchestration drift.
