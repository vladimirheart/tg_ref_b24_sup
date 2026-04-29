# 2026-04-29 22:18:54 — iiko organizations location sources

## Что изменено

- Добавлен флаг `locations_sync_enabled` для записей `iiko_api_monitors` в monitoring DB и прокинут через entity, repository, service и API-контроллер мониторинга.
- `IikoDepartmentLocationCatalogService` переведён с чтения `terminal_groups` на чтение активных `organizations`, при этом live-структура локаций строится только по monitor-записям с включённым флагом источника локаций.
- В `iiko-api-monitoring.js` добавлен клиентский переключатель `Источник структуры локаций`, который показывается только для `organizations`, попадает в payload сохранения и отмечается бейджем в списке monitor-записей.
- Добавлены и обновлены тесты на новый флаг мониторинга и выбор live-источников для структуры локаций.

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-066.md`
- `spring-panel/src/main/java/com/example/panel/controller/IikoApiMonitoringApiController.java`
- `spring-panel/src/main/java/com/example/panel/entity/IikoApiMonitor.java`
- `spring-panel/src/main/java/com/example/panel/repository/IikoApiMonitorRepository.java`
- `spring-panel/src/main/java/com/example/panel/service/IikoApiMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/service/IikoDepartmentLocationCatalogService.java`
- `spring-panel/src/main/java/com/example/panel/service/MonitoringDatabaseBootstrapService.java`
- `spring-panel/src/main/resources/static/js/iiko-api-monitoring.js`
- `spring-panel/src/test/java/com/example/panel/service/IikoApiMonitoringServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/IikoDepartmentLocationCatalogServiceTest.java`

## Проверка

- `spring-panel\\mvnw.cmd -q "-Dtest=IikoDepartmentLocationCatalogServiceTest,IikoApiMonitoringServiceTest" test`
- `spring-panel\\mvnw.cmd -q "-Dnet.bytebuddy.experimental=true" "-Dtest=IikoDepartmentLocationCatalogServiceTest,IikoApiMonitoringServiceTest,IikoDepartmentLocationsSyncSchedulerTest,ManagementControllerWebMvcTest" test`
