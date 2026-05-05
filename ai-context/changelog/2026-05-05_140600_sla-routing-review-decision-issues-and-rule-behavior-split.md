# 2026-05-05 14:06:00 - SLA routing review decision/issues and rule behavior split

## Что сделано

- добавлен `SlaRoutingGovernanceReviewDecisionService` для governance review freshness, decision, TTL и policy-change evaluation;
- добавлен `SlaRoutingGovernanceReviewIssueService` для issue collection вокруг governance review;
- добавлен `SlaRoutingRuleBehaviorService` для matcher, specificity, route и assignee-target heuristics;
- `SlaRoutingGovernanceReviewStateService` переведён на thin coordinator-модель поверх новых review decision/issues bounded services;
- `SlaRoutingRuleTypes` очищен от поведенческих методов и оставлен как DTO/enum container;
- `SlaRoutingRuleParserService`, `SlaRoutingGovernanceIssueService` и `SlaRoutingRuleAuditService` переведены на `SlaRoutingRuleBehaviorService`.

## Тесты

- `.\mvnw.cmd -q -DskipTests compile`
- `.\mvnw.cmd -q "-Dtest=SlaRoutingGovernanceReviewDecisionServiceTest,SlaRoutingGovernanceReviewIssueServiceTest,SlaRoutingRuleBehaviorServiceTest,SlaRoutingGovernanceReviewStateServiceTest,SlaRoutingGovernanceReviewPayloadServiceTest,SlaRoutingGovernanceReviewServiceTest,SlaRoutingPolicyTimeServiceTest,SlaRoutingLifecycleStateServiceTest,SlaRoutingGovernanceLeadTimeServiceTest,SlaRoutingGovernancePriorityServiceTest,SlaRoutingPolicySnapshotStateServiceTest,SlaRoutingGovernanceReviewPathServiceTest,SlaRoutingGovernanceSignalServiceTest,SlaRoutingGovernanceCheckpointServiceTest,SlaRoutingPolicyCandidateBuilderServiceTest,SlaRoutingPolicyPreviewSummaryServiceTest,SlaRoutingPolicyDecisionServiceTest,SlaRoutingPolicySnapshotServiceTest,SlaRoutingGovernanceAuditPayloadAssemblerServiceTest,SlaRoutingGovernanceSummaryServiceTest,SlaRoutingPolicyServiceTest,SlaRoutingPolicyConfigServiceTest,SlaRoutingRuleScalarParserServiceTest,SlaRoutingRuleMatchNormalizerServiceTest,SlaRoutingRuleParserServiceTest,SlaRoutingGovernanceIssueServiceTest,SlaRoutingRuleAuditServiceTest,SlaEscalationWebhookNotifierTest,SlaEscalationCandidateServiceTest,SlaEscalationAutoAssignServiceTest,SlaEscalationWebhookDeliveryServiceTest" test`

## Итог

- `Phase 3` и `Phase 4` остаются выполненными;
- remaining hotspot смещён в `SlaRoutingRuleBehaviorService` и вторично в `SlaRoutingRuleAuditService`;
- `spring-panel.log` мог обновиться от локальных Maven-прогонов.
