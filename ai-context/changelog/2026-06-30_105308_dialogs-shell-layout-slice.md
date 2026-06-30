# 2026-06-30 10:53:08 - dialogs shell layout slice

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-shell-runtime.js`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
хорошо, давай тогда по остаточным bounded-срезам
я не вижу никаких изменений в файлах
```
- Что сделано:
  в `dialogs-shell-runtime.js` перенесён bounded shell/layout slice:
  compact/list-only mode persistence, column width persistence/restore и
  column/details resize wiring.
- Что сделано:
  `dialogs.js` очищен от этого inline page-shell layout cluster и оставлен с
  thin delegate-обёртками; размер entrypoint снижен примерно до `4382` строк.
- Что сделано:
  audit/roadmap обновлены под новый размер и суженный remaining focus вокруг
  preference/task/date+sender helper-срезов и финального regression pass.
