# 2026-08-03 16:52:00 - netbox run uses current form state

- Затронутые области:
  - `spring-panel/src/main/java/com/example/panel/controller/SettingsNetBoxSyncController.java`
  - `spring-panel/src/main/resources/static/js/settings-netbox-sync-runtime.js`
  - `ai-context/tasks/task-list.md`
  - `ai-context/tasks/task-details/01-173.md`
- Промпты пользователя:
  - `указал url, но вернуло ошибку "Укажите базовый URL NetBox... хотя url указан: https://netbox.sushivesla.su`
- Что сделано:
  - выявлена реальная причина ошибки: кнопка ручного запуска NetBox sync использовала только уже сохранённый `settings.json`, а не текущее значение формы в модалке;
  - endpoint `POST /api/settings/netbox-sync/run` обновлён так, чтобы он принимал текущий payload `netbox_sync`, сохранял его в shared settings и только после этого запускал sync;
  - frontend runtime NetBox обновлён: при ручном запуске он теперь отправляет текущее состояние формы, включая URL, token, интервал и флаг очистки токена;
  - после успешного ручного запуска локальное runtime-состояние NetBox помечается как сохранённое, чтобы UI не оставался в несогласованном состоянии.
- Проверки:
  - `spring-panel\\mvnw.cmd -q -DskipTests compile` - success
