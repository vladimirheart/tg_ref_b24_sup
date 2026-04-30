# 2026-05-01 01:48:06 — SLA routing policy service split

## Что сделано
- из `SlaEscalationWebhookNotifier` вынесен governance/routing bounded context в `SlaRoutingPolicyService`;
- `buildRoutingPolicySnapshot(...)` и `buildRoutingGovernanceAudit(...)` теперь живут в отдельном service-слое;
- `SlaEscalationWebhookNotifier` переписан как thin orchestration/delivery wrapper;
- добавлен `SlaRoutingPolicyServiceTest` и сохранён зелёный regression через `SlaEscalationWebhookNotifierTest`.

## Проверка
- `spring-panel\\.\\mvnw.cmd -q -DskipTests compile`
- `spring-panel\\.\\mvnw.cmd -q "-Dtest=SlaRoutingPolicyServiceTest,SlaEscalationWebhookNotifierTest" test`

## Эффект
- главный notifier/runtime hotspot смещён из `SlaEscalationWebhookNotifier` в `SlaRoutingPolicyService`;
- следующий логичный split: governance review packet, issue classification и rule-definition parsing.
