# 2026-06-26 08:52:20 - dialogs participants runtime split

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-participants-runtime.js`,
  `spring-panel/src/main/resources/templates/dialogs/index.html`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий шаг по задаче
```
- Что сделано:
  добавлен bounded runtime `dialogs-participants-runtime.js`, который забрал
  participants/reassign state, rendering, operator loading и event wiring для
  details/workspace participant-management flows.
- Что сделано:
  `dialogs.js` переведён на thin delegates для participant-management cluster и
  перестал быть primary owner для этого details/workspace orchestration слоя.
- Что сделано:
  audit-документы обновлены под новый participants-slice; следующий hotspot
  сужен до remaining details/workspace orchestration поверх уже вынесенных
  shell/participants/runtime utilities.
