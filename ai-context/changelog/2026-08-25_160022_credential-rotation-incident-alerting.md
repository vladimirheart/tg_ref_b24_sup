# 2026-08-25 16:00:22 — credential-rotation-incident-alerting

## Пользовательский промпт

> выполни задачу 01-204

## Что сделано

- Для `credential_rotation` добавлен incident-driven alerting lifecycle:
  - critical registry entries теперь автоматически открывают или обновляют signal incident;
  - при выходе записи из critical состояния связанный incident автоматически резолвится;
  - повторные scheduler/read циклы не создают лишний alert noise, если critical fingerprint не изменился.
- Расширен registry API и analytics UI:
  - в ответах `/api/monitoring/credential-rotation/entries` и `/refresh` появился `incident_alerting` summary;
  - каждая credential entry теперь возвращает `related_incidents`, `has_active_incident` и `active_incident_count`;
  - analytics page показывает active alerts / alerted entries и related incidents per row.
- Общий incident summary contract дополнен `signal_type` и `signal_key`, чтобы monitoring surfaces могли стабильно связывать свои сигналы с incident workbench.
- Обновлены и добавлены тесты:
  - `CredentialRotationRegistryServiceTest`;
  - `CredentialRotationRegistryApiControllerWebMvcTest`;
  - `IncidentApiControllerWebMvcTest` получил недостающие mocks для текущего controller constructor.
- Синхронизирован project task-flow:
  - создан detail-файл `01-204`;
  - задача `01-204` переведена в `🟣` как завершённая AI и ожидающая ручной проверки.

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-204.md`
- `spring-panel/src/main/java/com/example/panel/service/CredentialRotationRegistryService.java`
- `spring-panel/src/main/java/com/example/panel/service/IncidentService.java`
- `spring-panel/src/main/java/com/example/panel/controller/CredentialRotationRegistryApiController.java`
- `spring-panel/src/main/resources/templates/analytics/credential-rotation.html`
- `spring-panel/src/main/resources/static/js/credential-rotation-monitoring.js`
- `spring-panel/src/test/java/com/example/panel/service/CredentialRotationRegistryServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/CredentialRotationRegistryApiControllerWebMvcTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/IncidentApiControllerWebMvcTest.java`
