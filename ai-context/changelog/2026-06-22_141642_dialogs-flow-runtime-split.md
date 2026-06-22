# 2026-06-22 14:16:42 - dialogs flow runtime split

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-flow-runtime.js`,
  `spring-panel/src/main/resources/templates/dialogs/index.html`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий шаг
```
- Что сделано:
  добавлен bounded runtime `dialogs-flow-runtime.js`, который забрал global
  hotkeys, dialog entry navigation, details modal lifecycle reset и workspace
  abandon telemetry wiring.
- Что сделано:
  `dialogs.js` переведён на thin delegates для flow-orchestration helper'ов и
  перестал держать primary inline cluster вокруг keyboard shortcuts и details
  modal lifecycle.
- Что сделано:
  task-detail `01-130` и roadmap обновлены под новый execution-срез, где
  следующим шагом остаётся дочистка remaining orchestration drift вокруг
  legacy modal flows и соседнего UI wiring.
