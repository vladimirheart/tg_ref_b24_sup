# 2026-06-26 17:56:25 - dialogs avatar runtime split

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-avatar-runtime.js`,
  `spring-panel/src/main/resources/templates/dialogs/index.html`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий шаг по задаче
```
- Что сделано:
  добавлен shared helper runtime `dialogs-avatar-runtime.js`, который забрал
  responsible/message avatar rendering и avatar-spec resolution для
  details/history/workspace surface'ов.
- Что сделано:
  `dialogs.js` переведён на thin delegates для avatar/message helper-кластера
  и перестал держать inline реализацию этих helper'ов.
- Что сделано:
  audit-документы обновлены до нового размера `dialogs.js` (`~5628` строк), а
  remaining hotspot сужен до workspace/reply orchestration, notifications
  continuity и остаточных history/workspace render helpers.
