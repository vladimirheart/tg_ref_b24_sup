# 2026-04-22 08:59:00 — telemetry and notifier boundary services pass

## Что сделано

- добавлен `DialogWorkspaceTelemetrySummaryService` как отдельный boundary-слой
  для summary-запросов `workspace telemetry`
- добавлен `DialogMacroGovernanceAuditService` как отдельный boundary-слой
  для `macro governance audit`
- `DialogWorkspaceTelemetryService` переведён на новые boundary-сервисы и
  больше не зависит от `DialogService` напрямую
- `WorkspaceGuardrailWebhookNotifier` переведён на
  `DialogWorkspaceTelemetrySummaryService` и тоже больше не зависит от
  `DialogService` напрямую
- добавлены targeted tests:
  - `DialogWorkspaceTelemetrySummaryServiceTest`
  - `DialogMacroGovernanceAuditServiceTest`

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=DialogWorkspaceTelemetrySummaryServiceTest,DialogMacroGovernanceAuditServiceTest,DialogWorkspaceTelemetryControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Зачем

Этот проход не завершает real split telemetry/macro логики из giant
`DialogService`, но убирает ещё два прямых consumer-зависимых хвоста вокруг
`workspace/notifier` слоя и подготавливает безопасную точку для следующего
полноценного выноса.
