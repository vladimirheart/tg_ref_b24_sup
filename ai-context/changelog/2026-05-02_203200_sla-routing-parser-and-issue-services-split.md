# 2026-05-02 20:32:00 — sla routing parser and issue services split

## Что сделано
- из `SlaRoutingRuleAuditService` вынесены два bounded context:
  `SlaRoutingRuleParserService` и `SlaRoutingGovernanceIssueService`;
- `SlaRoutingRuleParserService` теперь держит rule normalization,
  candidate-match и rule-definition parsing;
- `SlaRoutingGovernanceIssueService` теперь держит governance issue matrix и
  rule-level payload assembly;
- `SlaRoutingRuleAuditService` после этого переведён на coordinator-модель.

## Проверка
- `spring-panel\\mvnw.cmd -q -DskipTests compile`
- `spring-panel\\mvnw.cmd -q "-Dtest=SlaRoutingRuleParserServiceTest,SlaRoutingGovernanceIssueServiceTest,SlaRoutingRuleAuditServiceTest,SlaRoutingPolicyServiceTest,SlaEscalationWebhookNotifierTest" test`
- `spring-panel\\mvnw.cmd -q "-Dtest=SlaRoutingRuleParserServiceTest,SlaRoutingGovernanceIssueServiceTest,SlaRoutingRuleAuditServiceTest,SlaRoutingPolicyServiceTest,SlaEscalationWebhookNotifierTest,SlaEscalationCandidateServiceTest,SlaEscalationAutoAssignServiceTest,SlaEscalationWebhookDeliveryServiceTest" test`

## Итог
- `SlaRoutingRuleAuditService` больше не выглядит как hotspot сам по себе;
- новый remaining hotspot в этом периметре смещён в
  `SlaRoutingRuleParserService`.
