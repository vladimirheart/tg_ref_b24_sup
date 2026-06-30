# 2026-06-30 16:42:11 - dialogs list triage preferences slice

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-list-runtime.js`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промт пользователя:
```text
давай следующий более крупный шаг по задаче.
что в целом осталось сделать чтобы считать задача выполненной?
```
- Что сделано:
  в `dialogs-list-runtime.js` перенесён более крупный list
  preferences-slice: `loadPageSize`, `configureSlaWindowSelect`,
  `persistPageSize`, `restoreDialogPreferences`,
  `persistDialogPreferences`, `loadServerTriagePreferences` и внутренний
  debounce/save state для server-backed triage preferences.
- Что сделано:
  `dialogs.js` очищен от этого inline list preference/state cluster и оставлен
  с thin delegate-обёртками; размер entrypoint снижен примерно до `4179`
  строк.
- Что сделано:
  audit/roadmap обновлены под новый размер и narrowed remaining focus вокруг
  тонких list/shared helper'ов и финального regression pass.
