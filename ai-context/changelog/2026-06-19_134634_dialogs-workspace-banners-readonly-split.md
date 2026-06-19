# 2026-06-19 13:46:34 - dialogs workspace banners readonly split

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
  `dialogs-workspace-runtime.js` расширен до владельца workspace
  rollout/parity banners и readonly-policy helper'ов.
- Что сделано:
  `dialogs.js` переведён на thin delegates для `workspace banners/readonly`,
  поэтому giant-файл ещё сильнее сузился до wiring/orchestration соседних зон.
- Что сделано:
  task-detail `01-130` и roadmap обновлены под новый execution-срез, где
  следующими bounded целями остаются `quick actions`,
  `macro workflow` и `notifications refresh`.
