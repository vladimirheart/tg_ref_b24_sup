# 2026-05-05 14:12:00 - SLA routing rule audit and policy decision wide split

## Что сделано

- добавлен `SlaRoutingRuleMatchService` для match/empty-rule логики;
- добавлен `SlaRoutingRuleDescriptorService` для specificity, route и assignee-target descriptors;
- добавлен `SlaRoutingRuleUsageAnalysisService` для usage/conflict analysis по кандидатам;
- добавлен `SlaRoutingRuleAuditMetricsService` для aggregate metrics и issue counters;
- добавлен `SlaRoutingPolicyDecisionPayloadService` для ready/manual-review/webhook-only payload assembly;
- `SlaRoutingRuleBehaviorService`, `SlaRoutingRuleAuditService` и `SlaRoutingPolicyDecisionService` переведены на thin coordinator-модель поверх новых bounded services.

## Тесты

- `.\mvnw.cmd -q -DskipTests compile`
- `.\mvnw.cmd -q "-Dtest=SlaRoutingRuleMatchServiceTest,SlaRoutingRuleDescriptorServiceTest,SlaRoutingRuleUsageAnalysisServiceTest,SlaRoutingRuleAuditMetricsServiceTest,SlaRoutingPolicyDecisionPayloadServiceTest,SlaRoutingRuleBehaviorServiceTest,SlaRoutingRuleAuditServiceTest,SlaRoutingRuleParserServiceTest,SlaRoutingGovernanceIssueServiceTest,SlaRoutingGovernanceReviewDecisionServiceTest,SlaRoutingGovernanceReviewIssueServiceTest,SlaRoutingGovernanceReviewStateServiceTest,SlaRoutingGovernanceReviewPayloadServiceTest,SlaRoutingGovernanceReviewServiceTest,SlaRoutingPolicyDecisionServiceTest,SlaRoutingPolicySnapshotServiceTest,SlaRoutingGovernanceCheckpointServiceTest,SlaRoutingGovernanceSummaryServiceTest,SlaRoutingPolicyServiceTest,SlaRoutingPolicyConfigServiceTest,SlaRoutingRuleScalarParserServiceTest,SlaRoutingRuleMatchNormalizerServiceTest,SlaRoutingGovernanceLeadTimeServiceTest,SlaRoutingGovernancePriorityServiceTest,SlaRoutingPolicySnapshotStateServiceTest,SlaRoutingGovernanceReviewPathServiceTest,SlaRoutingGovernanceSignalServiceTest,SlaRoutingGovernanceAuditPayloadAssemblerServiceTest,SlaRoutingRuleAuditServiceTest,SlaEscalationWebhookNotifierTest,SlaEscalationCandidateServiceTest,SlaEscalationAutoAssignServiceTest,SlaEscalationWebhookDeliveryServiceTest" test`

## Итог

- `Phase 3` и `Phase 4` остаются выполненными;
- remaining notifier/runtime hotspot смещён в `SlaRoutingRuleParserService`, `SlaRoutingRuleMatchService` и вторично `SlaRoutingPolicySnapshotService`;
- `spring-panel.log` мог обновиться от локальных Maven-прогонов.
