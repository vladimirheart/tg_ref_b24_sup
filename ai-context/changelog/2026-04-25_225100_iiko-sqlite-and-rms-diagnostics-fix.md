# 2026-04-25 22:51:00 — iiko sqlite insert and rms diagnostics fix

## Что изменено

- Исправлена вставка новых записей мониторинга в SQLite без использования
  `getGeneratedKeys`.
- RMS-мониторинг переведён на целевую лицензию `RMS (Front Fast Food)` `id=100`.
- В UI RMS добавлен просмотр диагностики: raw ping, трассировка и
  лицензионный debug excerpt.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/repository/IikoApiMonitorRepository.java`
- `spring-panel/src/main/java/com/example/panel/repository/SslCertificateMonitorRepository.java`
- `spring-panel/src/main/java/com/example/panel/repository/RmsLicenseMonitorRepository.java`
- `spring-panel/src/main/java/com/example/panel/entity/RmsLicenseMonitor.java`
- `spring-panel/src/main/java/com/example/panel/service/MonitoringDatabaseBootstrapService.java`
- `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/controller/RmsLicenseMonitoringApiController.java`
- `spring-panel/src/main/resources/templates/analytics/rms-control.html`
- `spring-panel/src/main/resources/static/js/rms-monitoring.js`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-052.md`

## Кратко

Теперь создание iiko monitor не упирается в ограничение SQLite JDBC driver, а
RMS-страница показывает диагностику из UI и считает срок именно по лицензии
`RMS (Front Fast Food)` с `id=100`. Успешный ping также теперь показывает
резолвленный IP-адрес, если он есть в output команды.
