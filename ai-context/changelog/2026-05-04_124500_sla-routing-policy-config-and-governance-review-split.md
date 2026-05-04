## 2026-05-04 12:45:00

### Что сделано
- добавлен `SlaRoutingPolicyConfigService` для shared config/runtime parsing:
  dialog_config extraction, booleans, positive ints, orchestration mode,
  lifecycle state, UTC normalization и minutes-left calculation;
- добавлен `SlaRoutingGovernanceReviewService` для governance review state,
  governance issues, requirements payload и governance_review payload;
- `SlaRoutingPolicyService` переписан как более тонкий orchestration слой
  поверх candidate scan, auto-assign preview, rule audit, config parsing и
  governance review overlay.

### Проверка
- `.\mvnw.cmd -q -DskipTests compile`
- `.\mvnw.cmd -q "-Dtest=SlaRoutingPolicyConfigServiceTest,SlaRoutingGovernanceReviewServiceTest,SlaRoutingPolicyServiceTest,SlaRoutingRuleScalarParserServiceTest,SlaRoutingRuleMatchNormalizerServiceTest,SlaRoutingRuleParserServiceTest,SlaRoutingGovernanceIssueServiceTest,SlaRoutingRuleAuditServiceTest,SlaEscalationWebhookNotifierTest,SlaEscalationCandidateServiceTest,SlaEscalationAutoAssignServiceTest,SlaEscalationWebhookDeliveryServiceTest" test`

### Итог
- `SlaRoutingPolicyService` сжат примерно до `448` строк;
- remaining notifier/runtime hotspot смещён в summary/checkpoint orchestration
  tail внутри `SlaRoutingPolicyService`, а не в config/review parsing.
