# 2026-06-29 12:11:33 - dialogs workspace telemetry contract slice

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-workspace-runtime.js`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий шаг по задаче
```
- Что сделано:
  в `dialogs-workspace-runtime.js` перенесён более широкий workspace
  transport/telemetry slice: telemetry POST transport, disclosure-wiring,
  contract preload/retry/validation и fallback-reason resolver.
- Что сделано:
  `dialogs.js` очищен от этого inline workspace cluster и оставлен с thin
  delegate для внешних telemetry вызовов, а размер entrypoint снижен примерно
  до `4985` строк.
- Что сделано:
  audit/roadmap обновлены под новый размер и новый remaining focus вокруг
  workspace reply/action orchestration без возврата transport/route/cooldown
  логики обратно в `dialogs.js`.
