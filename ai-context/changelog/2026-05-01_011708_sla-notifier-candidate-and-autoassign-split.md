# 2026-05-01 01:17:08 — sla notifier candidate and autoassign split

## Что сделано

- из `SlaEscalationWebhookNotifier` вынесен candidate scan в
  `SlaEscalationCandidateService`;
- из `SlaEscalationWebhookNotifier` вынесен auto-assign bounded context в
  `SlaEscalationAutoAssignService`;
- `SlaEscalationWebhookNotifier` переведён на новые bounded services без смены
  внешнего test contract;
- добавлены targeted tests:
  `SlaEscalationCandidateServiceTest` и
  `SlaEscalationAutoAssignServiceTest`;
- как regression net переподтверждён полный
  `SlaEscalationWebhookNotifierTest`.

## Зачем

Это следующий post-phase hardening шаг после выноса webhook delivery. Основной
notifier hotspot сузился: candidate filtering и auto-assign routing больше не
живут внутри giant notifier, а следующий remaining bounded context теперь
локализован вокруг governance-audit / routing-policy review.

## Проверка

- `.\mvnw.cmd -q "-Dtest=SlaEscalationCandidateServiceTest,SlaEscalationAutoAssignServiceTest,SlaEscalationWebhookNotifierTest" test`
- `.\mvnw.cmd -q -DskipTests compile`
