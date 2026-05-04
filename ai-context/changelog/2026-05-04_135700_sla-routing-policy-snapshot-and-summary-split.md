## 2026-05-04 13:57:00

### Что сделано
- добавлен `SlaRoutingPolicySnapshotService` для полного routing policy
  snapshot preview flow;
- добавлен `SlaRoutingGovernanceSummaryService` для финального audit summary,
  checkpoint path и advisory-path assembly;
- `SlaRoutingPolicyService` переписан как thin facade/coordinator над snapshot,
  candidate scan, rule audit, config parsing и governance review services.

### Проверка
- `.\mvnw.cmd -q -DskipTests compile`
- `.\mvnw.cmd -q "-Dtest=SlaRoutingPolicySnapshotServiceTest,SlaRoutingGovernanceSummaryServiceTest,SlaRoutingPolicyServiceTest,SlaRoutingPolicyConfigServiceTest,SlaRoutingGovernanceReviewServiceTest,SlaRoutingRuleScalarParserServiceTest,SlaRoutingRuleMatchNormalizerServiceTest,SlaRoutingRuleParserServiceTest,SlaRoutingGovernanceIssueServiceTest,SlaRoutingRuleAuditServiceTest,SlaEscalationWebhookNotifierTest,SlaEscalationCandidateServiceTest,SlaEscalationAutoAssignServiceTest,SlaEscalationWebhookDeliveryServiceTest" test`

### Итог
- `SlaRoutingPolicyService` сжат примерно до `123` строк;
- remaining notifier/runtime hotspot смещён из facade в
  `SlaRoutingGovernanceSummaryService` (~223 строки) и вторично в
  `SlaRoutingPolicySnapshotService` (~181 строк).
