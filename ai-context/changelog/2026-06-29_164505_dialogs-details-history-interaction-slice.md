# 2026-06-29 16:45:05 - dialogs details history interaction slice

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий шаг по задаче
```
- Что сделано:
  в `dialogs-details-history-runtime.js` перенесён более широкий
  details-history interaction slice: history menu wiring, reply-target state,
  edit/delete/reply actions и details media/preview interaction binding.
- Что сделано:
  `dialogs.js` очищен от этого inline history interaction cluster и оставлен с
  thin delegates/runtime bootstrap; размер entrypoint снижен примерно до
  `4648` строк.
- Что сделано:
  audit/roadmap обновлены под новый размер и новый remaining focus вокруг
  workspace reply/action, notifications continuity и residual
  workspace/media-retry wiring.
