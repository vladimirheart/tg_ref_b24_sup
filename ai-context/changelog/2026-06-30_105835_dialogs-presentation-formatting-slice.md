# 2026-06-30 10:58:35 - dialogs presentation formatting slice

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-presentation-runtime.js`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промт пользователя:
```text
давай следующий небольшой шаг по задаче.
```
- Что сделано:
  в `dialogs-presentation-runtime.js` перенесён небольшой shared
  formatting-срез: `normalizeMessageSenderByType`, `resolveSenderLabel`,
  `parseUtcDateValue`, `formatUtcDate` и `formatTimestamp`.
- Что сделано:
  `dialogs.js` очищен от этого inline formatting cluster и оставлен с thin
  delegate-обёртками; размер entrypoint снижен примерно до `4330` строк.
- Что сделано:
  audit/roadmap обновлены под новый размер и оставшийся focus вокруг
  preferences/task helper-срезов и финального regression pass.
