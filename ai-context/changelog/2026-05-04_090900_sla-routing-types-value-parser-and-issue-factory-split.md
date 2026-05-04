# 2026-05-04 09:09:00 — sla routing types, value parser and issue factory split

## Что сделано
- из `SlaRoutingRuleParserService` вынесены общие rule DTO/enum типы в
  `SlaRoutingRuleTypes`;
- normalization/value parsing вынесены в
  `SlaRoutingRuleValueParserService`;
- issue payload factory вынесена в
  `SlaRoutingGovernanceIssueFactoryService`;
- `SlaRoutingRuleParserService` и `SlaRoutingGovernanceIssueService`
  переписаны как более тонкие bounded services поверх новых зависимостей.

## Проверка
- `spring-panel\\mvnw.cmd -q -DskipTests compile`
- `spring-panel\\mvnw.cmd -q "-Dtest=SlaRoutingRuleValueParserServiceTest,SlaRoutingGovernanceIssueFactoryServiceTest,SlaRoutingRuleParserServiceTest,SlaRoutingGovernanceIssueServiceTest,SlaRoutingRuleAuditServiceTest,SlaRoutingPolicyServiceTest,SlaEscalationWebhookNotifierTest" test`
- `spring-panel\\mvnw.cmd -q "-Dtest=SlaRoutingRuleValueParserServiceTest,SlaRoutingGovernanceIssueFactoryServiceTest,SlaRoutingRuleParserServiceTest,SlaRoutingGovernanceIssueServiceTest,SlaRoutingRuleAuditServiceTest,SlaRoutingPolicyServiceTest,SlaEscalationWebhookNotifierTest,SlaEscalationCandidateServiceTest,SlaEscalationAutoAssignServiceTest,SlaEscalationWebhookDeliveryServiceTest" test`

## Итог
- `SlaRoutingRuleParserService` больше не выглядит как hotspot сам по себе;
- новый remaining hotspot в notifier/runtime routing boundary смещён в
  `SlaRoutingRuleValueParserService`.
