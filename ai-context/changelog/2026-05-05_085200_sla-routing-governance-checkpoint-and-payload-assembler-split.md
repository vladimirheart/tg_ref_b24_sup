# 2026-05-05 08:52:00 — sla routing governance checkpoint and payload assembler split

## Что сделано
- Добавлен `SlaRoutingGovernanceCheckpointService` для required/advisory
  checkpoint metrics, weekly review priority, lead-time summaries и
  advisory-path reduction signals.
- Добавлен `SlaRoutingGovernanceAuditPayloadAssemblerService` для финальной
  сборки `routing governance audit` payload.
- `SlaRoutingGovernanceSummaryService` переведён на coordinator-модель поверх
  `SlaRoutingGovernanceReviewService`, `SlaRoutingGovernanceCheckpointService`
  и `SlaRoutingGovernanceAuditPayloadAssemblerService`.

## Проверка
- `./mvnw.cmd -q -DskipTests compile`
- `./mvnw.cmd -q "-Dtest=SlaRoutingGovernanceCheckpointServiceTest,SlaRoutingGovernanceAuditPayloadAssemblerServiceTest,SlaRoutingGovernanceSummaryServiceTest,SlaRoutingPolicySnapshotServiceTest,SlaRoutingPolicyServiceTest,SlaRoutingPolicyConfigServiceTest,SlaRoutingGovernanceReviewServiceTest,SlaRoutingRuleScalarParserServiceTest,SlaRoutingRuleMatchNormalizerServiceTest,SlaRoutingRuleParserServiceTest,SlaRoutingGovernanceIssueServiceTest,SlaRoutingRuleAuditServiceTest,SlaEscalationWebhookNotifierTest,SlaEscalationCandidateServiceTest,SlaEscalationAutoAssignServiceTest,SlaEscalationWebhookDeliveryServiceTest" test`

## Итог
- `SlaRoutingGovernanceSummaryService` сжат примерно до `127` строк.
- Новый remaining notifier/runtime hotspot смещён в
  `SlaRoutingPolicySnapshotService` (~`202` строки) и вторично в
  `SlaRoutingGovernanceCheckpointService` (~`189` строк).
