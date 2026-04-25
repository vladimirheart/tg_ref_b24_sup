# 2026-04-25 16:27:00 — iiko API monitoring

## Что изменено

- Добавлен новый контур мониторинга iiko API в `monitoring.db`.
- Добавлена отдельная страница аналитики для мониторинга выбранных запросов iiko API.
- Реализованы CRUD, очередь автопроверок, ручной запуск и просмотр последнего ответа.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/entity/IikoApiMonitor.java`
- `spring-panel/src/main/java/com/example/panel/repository/IikoApiMonitorRepository.java`
- `spring-panel/src/main/java/com/example/panel/service/IikoApiMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/service/IikoApiMonitoringScheduler.java`
- `spring-panel/src/main/java/com/example/panel/controller/IikoApiMonitoringApiController.java`
- `spring-panel/src/main/java/com/example/panel/controller/AnalyticsController.java`
- `spring-panel/src/main/java/com/example/panel/service/MonitoringDatabaseBootstrapService.java`
- `spring-panel/src/main/resources/templates/analytics/index.html`
- `spring-panel/src/main/resources/templates/analytics/iiko-api-monitoring.html`
- `spring-panel/src/main/resources/static/js/iiko-api-monitoring.js`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-051.md`

## Кратко

В аналитику добавлен новый раздел `Мониторинг iiko API`, который позволяет
заводить несколько записей с разными `apikey`, выбирать read-only метод из
официального набора iikoCloud API и видеть результат последней проверки:
статус, HTTP-код, длительность, сводку ответа и его сохранённый фрагмент.
