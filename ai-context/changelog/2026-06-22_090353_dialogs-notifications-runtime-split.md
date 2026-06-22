# 2026-06-22 09:03:53 - dialogs notifications runtime split

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `spring-panel/src/main/resources/static/js/dialogs-notifications-runtime.js`,
  `spring-panel/src/main/resources/templates/dialogs/index.html`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий шаг
```
- Что сделано:
  добавлен bounded runtime `dialogs-notifications-runtime.js`, который забрал
  unread/bell sync и sidebar refresh bridge между dialog runtime и
  `sidebar.js`.
- Что сделано:
  `dialogs.js` переведён на thin delegates для `setRowUnreadCount`,
  `updateDialogUnreadCount` и `requestSidebarNotificationRefresh`, поэтому
  notification bridge больше не живёт как primary helper-слой giant runtime.
- Что сделано:
  task-detail `01-130` и roadmap обновлены под новый execution-срез, где
  следующим шагом остаётся дочистка orchestration drift вокруг legacy modal
  flows, keyboard shortcuts и соседнего UI wiring.
