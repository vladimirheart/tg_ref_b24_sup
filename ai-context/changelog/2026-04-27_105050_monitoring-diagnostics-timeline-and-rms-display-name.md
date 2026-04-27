# 2026-04-27 10:50:50 — Monitoring diagnostics timeline and RMS display name

## Что изменено

- Добавлен общий history-слой `monitoring_check_history` в `monitoring.db` для результатов проверок RMS и iiko.
- RMS API и UI переведены на вычисляемое `server_name_display`, чтобы страница не зависела только от сырого `server_name`.
- В RMS diagnostics и iiko diagnostics добавлена временная шкала последних проверок с датой, статусом и краткой сводкой.
- В `ai-context/rules` добавлено проектное правило, что мониторинговая диагностика должна показывать историю состояний, а не только последнее состояние.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/repository/MonitoringCheckHistoryRepository.java`
- `spring-panel/src/main/java/com/example/panel/service/MonitoringDatabaseBootstrapService.java`
- `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/service/IikoApiMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/controller/RmsLicenseMonitoringApiController.java`
- `spring-panel/src/main/java/com/example/panel/controller/IikoApiMonitoringApiController.java`
- `spring-panel/src/main/resources/static/js/rms-monitoring.js`
- `spring-panel/src/main/resources/static/js/iiko-api-monitoring.js`
- `spring-panel/src/main/resources/templates/analytics/rms-control.html`
- `spring-panel/src/main/resources/templates/analytics/iiko-api-monitoring.html`
- `ai-context/rules/backend/02-monitoring-diagnostics-timeline.md`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-056.md`

## Проверка

- `spring-panel\\mvnw.cmd -q -DskipTests compile`
