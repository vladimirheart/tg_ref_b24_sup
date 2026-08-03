# 2026-08-03 17:35:00 - netbox token normalization

- Затронутые области:
  - `spring-panel/src/main/java/com/example/panel/service/NetBoxSyncSettingsService.java`
  - `spring-panel/src/main/java/com/example/panel/service/NetBoxObjectPassportSyncService.java`
  - `spring-panel/src/main/resources/static/js/settings-netbox-sync-runtime.js`
  - `spring-panel/src/test/java/com/example/panel/service/NetBoxSyncSettingsServiceTest.java`
  - `ai-context/tasks/task-list.md`
  - `ai-context/tasks/task-details/01-175.md`
- Промпты пользователя:
  - `ввёл данные и сохранил их, но при запуске синка ... invalid header value: "Token Укажите API token NetBox"`
- Что сделано:
  - добавлена нормализация NetBox API token, которая отбрасывает UI-подсказки и placeholder-значения вместо секрета;
  - усилена проверка NetBox settings перед sync, чтобы невалидный token останавливался понятной продуктовой ошибкой до построения HTTP-запроса;
  - frontend runtime теперь явно синхронизирует password-input token с состоянием и очищает поле после сохранения;
  - добавлены тесты на сохранение и чтение NetBox token с защитой от текстов-подсказок.
