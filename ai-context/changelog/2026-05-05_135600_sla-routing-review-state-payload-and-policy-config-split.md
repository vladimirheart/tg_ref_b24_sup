# 2026-05-05 13:56:00 — sla routing review state, payload and policy config split

## Что сделано
- Добавлен `SlaRoutingGovernanceReviewStateService` для governance review
  state и issue evaluation.
- Добавлен `SlaRoutingGovernanceReviewPayloadService` для requirements и
  governance review payload builders.
- Добавлен `SlaRoutingPolicyTimeService` для UTC/minutes-left parsing.
- Добавлен `SlaRoutingLifecycleStateService` для lifecycle normalization.
- `SlaRoutingGovernanceReviewService` и `SlaRoutingPolicyConfigService`
  переведены на более тонкую coordinator-модель.

## Проверка
- `./mvnw.cmd -q -DskipTests compile`
- `./mvnw.cmd -q "-Dtest=SlaRoutingGovernanceReviewStateServiceTest,SlaRoutingGovernanceReviewPayloadServiceTest,SlaRoutingPolicyTimeServiceTest,SlaRoutingLifecycleStateServiceTest,SlaRoutingGovernanceLeadTimeServiceTest,SlaRoutingGovernancePriorityServiceTest,SlaRoutingPolicySnapshotStateServiceTest,SlaRoutingGovernanceReviewPathServiceTest,SlaRoutingGovernanceSignalServiceTest,SlaRoutingGovernanceCheckpointServiceTest,SlaRoutingPolicyCandidateBuilderServiceTest,SlaRoutingPolicyPreviewSummaryServiceTest,SlaRoutingPolicyDecisionServiceTest,SlaRoutingPolicySnapshotServiceTest,SlaRoutingGovernanceAuditPayloadAssemblerServiceTest,SlaRoutingGovernanceSummaryServiceTest,SlaRoutingPolicyServiceTest,SlaRoutingPolicyConfigServiceTest,SlaRoutingGovernanceReviewServiceTest,SlaRoutingRuleScalarParserServiceTest,SlaRoutingRuleMatchNormalizerServiceTest,SlaRoutingRuleParserServiceTest,SlaRoutingGovernanceIssueServiceTest,SlaRoutingRuleAuditServiceTest,SlaEscalationWebhookNotifierTest,SlaEscalationCandidateServiceTest,SlaEscalationAutoAssignServiceTest,SlaEscalationWebhookDeliveryServiceTest" test`

## Итог
- `SlaRoutingGovernanceReviewService` сжат примерно до `86` строк.
- `SlaRoutingPolicyConfigService` сжат примерно до `89` строк.
- Remaining notifier/runtime hotspot смещён в
  `SlaRoutingGovernanceReviewStateService` (~`167` строк) и соседние
  rule normalization/types bounded contexts.
