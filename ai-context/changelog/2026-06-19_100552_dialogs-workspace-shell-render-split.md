# 2026-06-19 10:05:52 - dialogs workspace shell render split

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-workspace-runtime.js`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий шаг
```
- Что сделано:
  `dialogs-workspace-runtime.js` расширен до владельца `renderWorkspaceShell`,
  включая workspace messages/history/events/SLA shell rendering и composer
  readonly/navigation orchestration.
- Что сделано:
  `dialogs.js` переведён на thin wrapper для `renderWorkspaceShell` и теперь
  держит в workspace-зоне в основном callback-helper'ы уровня
  `client profile/context renderer`, categories/actions wiring и telemetry.
- Что сделано:
  task-detail `01-130` и roadmap обновлены под новый execution-срез, чтобы
  следующий проход целился уже в `workspace client/context render` или в
  соседние bounded slices `quick actions` / `macro workflow`.
