# 2026-05-01 00:51:04 — telemetry / guardrail / sla delivery hardening

## Что сделано
- вынесен `DialogWorkspaceTelemetryControlService`:
  `DialogWorkspaceTelemetryService` больше не держит внутри себя
  `p1_operational_control`, `sla_review_path_control`,
  `p2_governance_control` и `weekly_review_focus` builder-логику;
- вынесены `WorkspaceGuardrailWebhookCommandService` и
  `WorkspaceGuardrailWebhookDeliveryService`:
  `WorkspaceGuardrailWebhookNotifier` стал thin scheduled wrapper над
  command/delivery split;
- вынесен `SlaEscalationWebhookDeliveryService`:
  `SlaEscalationWebhookNotifier` больше не держит внутри себя webhook
  endpoint resolution, dedup, retry/fanout и transport delivery слой;
- добавлены targeted tests:
  `DialogWorkspaceTelemetryControlServiceTest`,
  `WorkspaceGuardrailWebhookCommandServiceTest`,
  `SlaEscalationWebhookDeliveryServiceTest`;
- синхронизированы `ARCHITECTURE_AUDIT_2026-04-08.md`,
  `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и `01-024.md`.

## Зачем
- сузить post-`Phase 3` hotspot уже не вокруг `DialogService`, а вокруг
  telemetry/notifier/runtime boundaries;
- сделать следующий рефакторинг более предметным:
  главный remaining tail теперь локализован в giant
  `SlaEscalationWebhookNotifier`, а не размазан по нескольким adjacent
  сервисам.

## Проверка
- `spring-panel\\mvnw.cmd -q -DskipTests compile`
- `spring-panel\\mvnw.cmd -q "-Dtest=DialogWorkspaceTelemetryControlServiceTest,WorkspaceGuardrailWebhookCommandServiceTest,SlaEscalationWebhookDeliveryServiceTest,DialogWorkspaceTelemetryControllerWebMvcTest,SlaEscalationWebhookNotifierTest" test`
