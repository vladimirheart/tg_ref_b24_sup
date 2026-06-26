# 2026-06-26 07:24:52 - dialogs shell runtime split

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-shell-runtime.js`,
  `spring-panel/src/main/resources/templates/dialogs/index.html`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий шаг по задаче
```
- Что сделано:
  добавлен bounded runtime `dialogs-shell-runtime.js`, который забрал modal
  safety helpers, fallback dismiss/scroll containment, avatar hydration helpers
  и единый `openDialogSurface` для page-shell orchestration.
- Что сделано:
  `dialogs.js` переведён на thin delegates для shell utility-кластера и
  перестал быть primary owner для этих modal/avatar/open helpers.
- Что сделано:
  audit-документы обновлены под новый shell-slice; следующий hotspot сужен до
  remaining details/workspace orchestration поверх уже вынесенных runtime
  utilities.
