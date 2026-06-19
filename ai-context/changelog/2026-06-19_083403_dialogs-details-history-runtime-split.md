# 2026-06-19 08:34:03 - dialogs details history runtime split

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js`,
  `spring-panel/src/main/resources/templates/dialogs/index.html`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий шаг
продолжи
продолжи прогон
```
- Что сделано:
  добавлен bounded runtime `dialogs-details-history-runtime.js`, который
  забрал history polling, previous-history batches, history rendering and
  filtering, media preview/audio interactions и media send helpers.
- Что сделано:
  `dialogs.js` переведён на thin wrappers и orchestration вокруг нового
  details/history runtime, а legacy-файл больше не хранит собственный
  history/media state и дублирующие media handlers.
- Что сделано:
  task-detail `01-130` и roadmap обновлены под новый execution-срез, чтобы
  следующий проход фокусировался уже на `workspace shell`, `quick actions`,
  `macro workflow` и `notifications refresh`.
