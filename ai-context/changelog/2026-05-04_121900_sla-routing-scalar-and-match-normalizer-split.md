## 2026-05-04 12:19:00

### Что сделано
- из `SlaRoutingRuleValueParserService` вынесены отдельные bounded services:
  `SlaRoutingRuleScalarParserService` для scalar/temporal parsing и
  `SlaRoutingRuleMatchNormalizerService` для match/category/state
  normalization;
- `SlaRoutingRuleParserService` переписан на новые зависимости и больше не
  держит composite value-parser;
- `SlaRoutingGovernanceIssueService` переведён на общий scalar parser для
  trim/blank normalization;
- из `SlaRoutingPolicyService` убран локальный `parseUtcInstant` tail и trim
  parsing для governance review checkpoints;
- старый `SlaRoutingRuleValueParserService` удалён;
- добавлены `SlaRoutingRuleScalarParserServiceTest` и
  `SlaRoutingRuleMatchNormalizerServiceTest`.

### Проверка
- `.\mvnw.cmd -q -DskipTests compile`
- `.\mvnw.cmd -q "-Dtest=SlaRoutingRuleScalarParserServiceTest,SlaRoutingRuleMatchNormalizerServiceTest,SlaRoutingRuleParserServiceTest,SlaRoutingGovernanceIssueServiceTest,SlaRoutingRuleAuditServiceTest,SlaRoutingPolicyServiceTest,SlaEscalationWebhookNotifierTest,SlaEscalationCandidateServiceTest,SlaEscalationAutoAssignServiceTest,SlaEscalationWebhookDeliveryServiceTest" test`

### Итог
- parser/value bounded context больше не является hotspot;
- новый remaining hotspot в notifier/runtime hardening смещён обратно в
  `SlaRoutingPolicyService` (~586 строк) с governance review / checkpoint
  summary overlay.
