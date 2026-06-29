# 2026-06-29 17:03:16 - dialogs notifications delegate cleanup

- Файлы:
  `spring-panel/src/main/resources/static/js/dialogs.js`,
  `ai-context/tasks/task-details/01-130.md`,
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
- Промты пользователя:
```text
давай следующий небольшой шаг по задаче
```
- Что сделано:
  соседние runtime'ы в `dialogs.js` переведены на прямое подключение к
  `dialogs-notifications-runtime.js` без промежуточных thin wrappers.
- Что сделано:
  из `dialogs.js` удалены локальные delegates `setRowUnreadCount`,
  `updateDialogUnreadCount` и `requestSidebarNotificationRefresh`.
- Что сделано:
  audit/roadmap обновлены под новый размер entrypoint (`~4636` строк) и
  зафиксировали закрытие маленького notifications delegate cleanup шага.
