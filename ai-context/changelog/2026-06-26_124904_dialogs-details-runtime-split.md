# 2026-06-26 12:49:04 - dialogs details runtime split

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-details-runtime.js`,
  `spring-panel/src/main/resources/templates/dialogs/index.html`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий шаг по задаче
```
- Что сделано:
  добавлен bounded runtime `dialogs-details-runtime.js`, который забрал
  details open-flow, status/responsible summary rendering и time-metrics
  orchestration для legacy details modal.
- Что сделано:
  `dialogs.js` переведён на thin delegates для details/status helpers и больше
  не держит inline реализацию этого кластера.
- Что сделано:
  audit-документы обновлены до нового размера `dialogs.js` (`~5757` строк), а
  remaining hotspot сужен до workspace/reply orchestration и continuity между
  details/list/notifications.
