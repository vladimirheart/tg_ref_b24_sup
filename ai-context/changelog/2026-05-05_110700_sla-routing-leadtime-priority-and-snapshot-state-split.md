# 2026-05-05 11:07:00 — sla routing leadtime, priority and snapshot state split

## Что сделано
- Добавлен `SlaRoutingGovernanceLeadTimeService` для lead-time/risk evaluation.
- Добавлен `SlaRoutingGovernancePriorityService` для weekly-review priority и
  checkpoint closure policy.
- Добавлен `SlaRoutingPolicySnapshotStateService` для base snapshot header и
  pre-critical state payloads.
- `SlaRoutingGovernanceSignalService` и `SlaRoutingPolicySnapshotService`
  переведены на более тонкую coordinator-модель.

## Проверка
- `./mvnw.cmd -q -DskipTests compile`
- `./mvnw.cmd -q "-Dtest=SlaRoutingGovernanceLeadTimeServiceTest,SlaRoutingGovernancePriorityServiceTest,SlaRoutingPolicySnapshotStateServiceTest,SlaRoutingGovernanceReviewPathServiceTest,SlaRoutingGovernanceSignalServiceTest,SlaRoutingGovernanceCheckpointServiceTest,SlaRoutingPolicyCandidateBuilderServiceTest,SlaRoutingPolicyPreviewSummaryServiceTest,SlaRoutingPolicyDecisionServiceTest,SlaRoutingPolicySnapshotServiceTest,SlaRoutingGovernanceAuditPayloadAssemblerServiceTest,SlaRoutingGovernanceSummaryServiceTest,SlaRoutingPolicyServiceTest,SlaRoutingPolicyConfigServiceTest,SlaRoutingGovernanceReviewServiceTest,SlaRoutingRuleScalarParserServiceTest,SlaRoutingRuleMatchNormalizerServiceTest,SlaRoutingRuleParserServiceTest,SlaRoutingGovernanceIssueServiceTest,SlaRoutingRuleAuditServiceTest,SlaEscalationWebhookNotifierTest,SlaEscalationCandidateServiceTest,SlaEscalationAutoAssignServiceTest,SlaEscalationWebhookDeliveryServiceTest" test`

## Итог
- `SlaRoutingGovernanceSignalService` сжат примерно до `96` строк.
- `SlaRoutingPolicySnapshotService` сжат примерно до `121` строки.
- Remaining notifier/runtime hotspot смещён уже в локальные bounded services,
  а не в общий routing hardening wrapper.
