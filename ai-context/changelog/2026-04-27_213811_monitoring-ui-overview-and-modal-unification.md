# 2026-04-27 21:38:11 — Унификация UI мониторингов

## Что изменено

- На странице RMS добавлены:
  - сортировка по столбцам;
  - фильтрация общего списка по статусу доступности из overview;
  - компактные иконки состояния записи/лицензии/сети в колонке `Статус RMS`;
  - подсветка строк для `IIKO_CHAIN`;
  - показ количества целевой лицензии `RMS (Front Fast Food)` с `id=100`;
  - сокращённое отображение expiry без времени и без логина в основном списке.
- На страницу SSL добавлены:
  - отдельная модалка создания записи вместо inline-формы;
  - верхняя плашка общей доступности с фильтрацией списка по сетевому статусу.
- На страницу iiko API добавлена отдельная модалка создания записи вместо inline-формы.
- Для SSL API-ответа добавлен `availability_overview`, а для RMS-списка в DTO добавлено `target_license_count`.

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-058.md`
- `spring-panel/src/main/java/com/example/panel/controller/RmsLicenseMonitoringApiController.java`
- `spring-panel/src/main/java/com/example/panel/controller/SslCertificateMonitoringApiController.java`
- `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/service/SslCertificateMonitoringService.java`
- `spring-panel/src/main/resources/templates/analytics/rms-control.html`
- `spring-panel/src/main/resources/templates/analytics/certificates.html`
- `spring-panel/src/main/resources/templates/analytics/iiko-api-monitoring.html`
- `spring-panel/src/main/resources/static/js/rms-monitoring.js`
- `spring-panel/src/main/resources/static/js/cert-monitoring.js`
- `spring-panel/src/main/resources/static/js/iiko-api-monitoring.js`

## Проверка

- `spring-panel\\mvnw.cmd -q -DskipTests compile`
