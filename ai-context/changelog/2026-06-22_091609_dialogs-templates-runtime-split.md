# 2026-06-22 09:16:09 - dialogs templates runtime split

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-templates-runtime.js`,
  `spring-panel/src/main/resources/templates/dialogs/index.html`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий шаг
```
- Что сделано:
  добавлен bounded runtime `dialogs-templates-runtime.js`, который забрал
  category/question/completion template UI, emoji panel wiring и category
  modal reopen/toggle semantics для legacy details/workspace surfaces.
- Что сделано:
  `dialogs.js` переведён на thin delegates для template-adjacent helpers и
  больше не держит primary inline ownership category/emoji/template wiring.
- Что сделано:
  task-detail `01-130` и roadmap обновлены под новый execution-срез, где
  следующим шагом остаётся дочистка orchestration drift вокруг keyboard
  shortcuts и legacy details/workspace modal flows.
