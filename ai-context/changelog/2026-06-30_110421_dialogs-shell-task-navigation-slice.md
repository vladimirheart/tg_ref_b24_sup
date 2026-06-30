# 2026-06-30 11:04:21 - dialogs shell task navigation slice

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-shell-runtime.js`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промт пользователя:
```text
давай следующий небольшой шаг по задаче.
```
- Что сделано:
  в `dialogs-shell-runtime.js` перенесён небольшой task-navigation slice:
  `setTaskDraft`, `buildTaskCreateUrl` и единый `openTaskCreateSurface`.
- Что сделано:
  `dialogs.js` очищен от inline task draft/navigation helper'ов и трёх
  повторяющихся переходов на создание задачи; размер entrypoint снижен
  примерно до `4316` строк.
- Что сделано:
  audit/roadmap обновлены под новый размер и оставшийся focus вокруг
  preferences helper-срезов и финального regression pass.
