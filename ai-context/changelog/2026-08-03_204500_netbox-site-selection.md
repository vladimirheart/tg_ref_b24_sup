# 2026-08-03 20:45:00 - netbox site selection

- Затронутые области:
  - `spring-panel/src/main/java/com/example/panel/service/NetBoxSyncSettingsService.java`
  - `spring-panel/src/main/java/com/example/panel/service/NetBoxObjectPassportSyncService.java`
  - `spring-panel/src/main/java/com/example/panel/controller/SettingsNetBoxSyncController.java`
  - `spring-panel/src/main/resources/static/js/settings-netbox-sync-runtime.js`
  - `spring-panel/src/main/resources/templates/settings/index.html`
  - `spring-panel/src/test/java/com/example/panel/service/NetBoxSyncSettingsServiceTest.java`
  - `ai-context/tasks/task-list.md`
  - `ai-context/tasks/task-details/01-180.md`
- Промпты пользователя:
  - `а давай ещё научим панель, какие именно sites нужно загрузить. то есть при синхронизации сначала возвращает список sites а пользователь сам укажет, какие именно нужно загрузить. дп, в списке нужно возвращать их состояние из поля "Status"`
- Что сделано:
  - в настройки NetBox sync добавлен выбор конкретных `sites` для импорта с предварительной загрузкой списка из NetBox;
  - список `sites` возвращает `id`, имя и `Status`, чтобы можно было выбирать объекты осознанно;
  - выбранные `site_id` сохраняются в конфигурации и используются самим sync, при пустом выборе остаётся fallback на импорт всех `sites`.
