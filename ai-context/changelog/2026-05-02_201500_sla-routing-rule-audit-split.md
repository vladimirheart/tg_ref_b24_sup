# 2026-05-02 20:15:00 — sla routing rule audit split

## Что сделано
- из `SlaRoutingPolicyService` вынесен rule-audit bounded context в новый
  `SlaRoutingRuleAuditService`;
- новый сервис теперь держит rule-definition parsing, conflict/broad-rule
  analysis, issue classification и rule-level audit payload assembly;
- `SlaRoutingPolicyService` переведён на thin governance overlay поверх
  `critical candidates`, `governance review` и итоговых audit-метрик;
- fallback-конструктор `SlaEscalationWebhookNotifier` и targeted tests
  синхронизированы с новым constructor contract.

## Проверка
- `spring-panel\\mvnw.cmd -q -DskipTests compile`
- `spring-panel\\mvnw.cmd -q "-Dtest=SlaRoutingRuleAuditServiceTest,SlaRoutingPolicyServiceTest,SlaEscalationWebhookNotifierTest,SlaEscalationCandidateServiceTest,SlaEscalationAutoAssignServiceTest,SlaEscalationWebhookDeliveryServiceTest" test`

## Итог
- `SlaRoutingPolicyService` больше не является главным notifier/runtime
  hotspot сам по себе;
- следующий remaining hotspot в этом периметре смещён в
  `SlaRoutingRuleAuditService`.
