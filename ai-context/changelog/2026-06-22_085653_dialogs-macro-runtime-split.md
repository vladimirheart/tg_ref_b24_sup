# 2026-06-22 08:56:53 - dialogs macro runtime split

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-macro-runtime.js`,
  `spring-panel/src/main/resources/static/js/dialogs-actions-runtime.js`,
  `spring-panel/src/main/resources/templates/dialogs/index.html`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий шаг
```
- Что сделано:
  добавлен bounded runtime `dialogs-macro-runtime.js`, который забрал
  macro search/preview, variable catalog/default resolution и apply/workflow
  orchestration для details/workspace surfaces.
- Что сделано:
  `dialogs.js` переведён на thin delegates для macro helpers и больше не
  держит primary inline ownership macro-state, macro apply handlers и
  workspace composer macro wiring.
- Что сделано:
  попутно исправлен небезопасный optional-call в `dialogs-actions-runtime.js`,
  чтобы runtime не падал при отсутствии `loadDialogParticipants`.
- Что сделано:
  task-detail `01-130` и roadmap обновлены под новый execution-срез, где
  следующей bounded целью остаётся `notifications refresh`.
