# 2026-06-26 18:04:43 - dialogs presentation runtime split

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-presentation-runtime.js`,
  `spring-panel/src/main/resources/templates/dialogs/index.html`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий шаг по задаче
```
- Что сделано:
  добавлен presentation helper runtime `dialogs-presentation-runtime.js`,
  который забрал channel/rating/meta formatting и
  `renderWorkspaceMessageItem` для shared workspace/list presentation surface.
- Что сделано:
  `dialogs.js` переведён на thin delegates для presentation helper-кластера и
  перестал держать inline реализацию этих render/format helper'ов.
- Что сделано:
  audit-документы обновлены до нового размера `dialogs.js` (`~5551` строк), а
  remaining hotspot сужен до workspace/reply orchestration, notifications
  continuity и остаточных history/workspace render helpers.
