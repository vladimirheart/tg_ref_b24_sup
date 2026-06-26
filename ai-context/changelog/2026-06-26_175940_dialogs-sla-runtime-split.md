# 2026-06-26 17:59:40 - dialogs sla runtime split

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-sla-runtime.js`,
  `spring-panel/src/main/resources/templates/dialogs/index.html`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий шаг по задаче
```
- Что сделано:
  добавлен list helper runtime `dialogs-sla-runtime.js`, который забрал SLA
  badge calculation/rendering для dialog rows и связанный update-loop.
- Что сделано:
  `dialogs.js` переведён на thin delegates для SLA helper-кластера и перестал
  держать inline расчёт/рендер SLA badge логики.
- Что сделано:
  audit-документы обновлены до нового размера `dialogs.js` (`~5589` строк), а
  remaining hotspot сужен до workspace/reply orchestration, notifications
  continuity и остаточных history/workspace render helpers.
