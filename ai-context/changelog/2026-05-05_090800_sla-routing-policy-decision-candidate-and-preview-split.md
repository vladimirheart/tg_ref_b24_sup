# 2026-05-05 09:08:00 — sla routing policy decision, candidate and preview split

## Что сделано
- Добавлен `SlaRoutingPolicyCandidateBuilderService` для candidate payload assembly.
- Добавлен `SlaRoutingPolicyPreviewSummaryService` для preview summary текста.
- Добавлен `SlaRoutingPolicyDecisionService` для critical snapshot decision tail:
  assigned reassign guard, auto-assign preview, webhook-only fallback и
  manual review fallback.
- `SlaRoutingPolicySnapshotService` переведён на coordinator-модель поверх
  config parsing и нового decision слоя.

## Проверка
- `./mvnw.cmd -q -DskipTests compile`
- `./mvnw.cmd -q "-Dtest=SlaRoutingPolicyCandidateBuilderServiceTest,SlaRoutingPolicyPreviewSummaryServiceTest,SlaRoutingPolicyDecisionServiceTest,SlaRoutingPolicySnapshotServiceTest,SlaRoutingGovernanceCheckpointServiceTest,SlaRoutingGovernanceAuditPayloadAssemblerServiceTest,SlaRoutingGovernanceSummaryServiceTest,SlaRoutingPolicyServiceTest,SlaRoutingPolicyConfigServiceTest,SlaRoutingGovernanceReviewServiceTest,SlaRoutingRuleScalarParserServiceTest,SlaRoutingRuleMatchNormalizerServiceTest,SlaRoutingRuleParserServiceTest,SlaRoutingGovernanceIssueServiceTest,SlaRoutingRuleAuditServiceTest,SlaEscalationWebhookNotifierTest,SlaEscalationCandidateServiceTest,SlaEscalationAutoAssignServiceTest,SlaEscalationWebhookDeliveryServiceTest" test`

## Итог
- `SlaRoutingPolicySnapshotService` сжат примерно до `136` строк.
- Новый remaining notifier/runtime hotspot смещён в
  `SlaRoutingGovernanceCheckpointService` (~`189` строк) и вторично в
  `SlaRoutingPolicySnapshotService` (~`136` строк).
