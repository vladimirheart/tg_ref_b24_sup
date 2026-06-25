# 2026-06-25 11:10:48 - dialogs experiment runtime split

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-experiment-runtime.js`,
  `spring-panel/src/main/resources/templates/dialogs/index.html`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий шаг по задаче аудита
```
- Что сделано:
  добавлен bounded runtime `dialogs-experiment-runtime.js`, который забрал
  experiment info panel, telemetry summary/guardrails, rollout packet,
  scorecard/decision и auto-refresh wiring для experiment modal.
- Что сделано:
  `dialogs.js` переведён на thin delegates для experiment telemetry/rendering,
  а `dialogs/index.html` теперь подключает новый runtime отдельно от legacy
  giant entrypoint.
- Что сделано:
  audit-документы `01-130` и roadmap очищены от избыточного пошагового шума,
  синхронизированы с новым `experiment`-срезом и сужают следующий hotspot до
  legacy modal/page orchestration и regression corridor вокруг pagination,
  dialog-open flow и avatar hydration.
