# 2026-05-05 09:40:00 — sla routing checkpoint review-path and signal split

## Что сделано
- Добавлен `SlaRoutingGovernanceReviewPathService` для minimum required review
  path и advisory checkpoint builder.
- Добавлен `SlaRoutingGovernanceSignalService` для noise/churn/lead-time и
  weekly-review priority signals.
- `SlaRoutingGovernanceCheckpointService` переведён на coordinator-модель
  поверх review-path и signal bounded services.

## Проверка
- `./mvnw.cmd -q -DskipTests compile`
- `./mvnw.cmd -q "-Dtest=SlaRoutingGovernanceReviewPathServiceTest,SlaRoutingGovernanceSignalServiceTest,SlaRoutingGovernanceCheckpointServiceTest,SlaRoutingPolicyCandidateBuilderServiceTest,SlaRoutingPolicyPreviewSummaryServiceTest,SlaRoutingPolicyDecisionServiceTest,SlaRoutingPolicySnapshotServiceTest,SlaRoutingGovernanceAuditPayloadAssemblerServiceTest,SlaRoutingGovernanceSummaryServiceTest,SlaRoutingPolicyServiceTest,SlaRoutingPolicyConfigServiceTest,SlaRoutingGovernanceReviewServiceTest,SlaRoutingRuleScalarParserServiceTest,SlaRoutingRuleMatchNormalizerServiceTest,SlaRoutingRuleParserServiceTest,SlaRoutingGovernanceIssueServiceTest,SlaRoutingRuleAuditServiceTest,SlaEscalationWebhookNotifierTest,SlaEscalationCandidateServiceTest,SlaEscalationAutoAssignServiceTest,SlaEscalationWebhookDeliveryServiceTest" test`

## Итог
- `SlaRoutingGovernanceCheckpointService` сжат примерно до `127` строк.
- Новый remaining notifier/runtime hotspot смещён в
  `SlaRoutingPolicySnapshotService` (~`136` строк) и вторично в
  `SlaRoutingGovernanceSignalService` (~`132` строки).
