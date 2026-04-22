# 2026-04-22 10:13:00

## Что сделано

- переведён production-конструктор `SlaEscalationWebhookNotifier` на
  `DialogLookupReadService`, `DialogResponsibilityService` и
  `DialogAuditService` без обязательной зависимости от `DialogService`;
- сохранён legacy-compatible fallback через `DialogService` только для
  старых unit-тестов и точечных сценариев без полного dependency graph;
- обновлены roadmap, architecture audit и task-detail `01-024` под новый
  notifier/runtime boundary.

## Проверка

- `spring-panel\mvnw.cmd -q -DskipTests compile`
- `spring-panel\mvnw.cmd -q "-Dtest=SlaEscalationWebhookNotifierTest,DialogWorkspaceTelemetryControllerWebMvcTest" test`

## Примечания

- `compile` прошёл успешно;
- `DialogWorkspaceTelemetryControllerWebMvcTest` прошёл успешно;
- в `SlaEscalationWebhookNotifierTest` остались legacy-падения по
  `route naming/review-path`, не связанные с самим новым constructor wiring;
- `logs/spring-panel.log` обновился от локальных прогонов и не редактировался вручную.
