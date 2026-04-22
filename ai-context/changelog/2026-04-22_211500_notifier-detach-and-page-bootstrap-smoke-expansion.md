# 2026-04-22 21:15:00 — notifier detach and page bootstrap smoke expansion

## Что сделано

- `SlaEscalationWebhookNotifier` окончательно отвязан от `DialogService`:
  удалён legacy fallback-конструктор/field-ветка, notifier теперь работает
  только через `DialogLookupReadService`, `DialogResponsibilityService` и
  `DialogAuditService`;
- `SlaEscalationWebhookNotifierTest` переведён на прямые зависимости
  `DialogLookupReadService` для routing/load-сценариев;
- в шаблоны добавлены explicit `data-ui-page` для:
  `channels`, `tasks`, `users`, `passports`, `public form`,
  `analytics/certificates`, `analytics/rms-control`;
- расширен page bootstrap smoke-пакет через `ManagementControllerWebMvcTest`,
  `AnalyticsControllerWebMvcTest` и новый
  `PublicFormControllerWebMvcTest`.

## Проверка

- `.\mvnw.cmd -q "-Dtest=SlaEscalationWebhookNotifierTest,ManagementControllerWebMvcTest,AnalyticsControllerWebMvcTest,PublicFormControllerWebMvcTest" test`
- `.\mvnw.cmd -q -DskipTests compile`

## Эффект

- notifier/runtime boundary больше не держит даже legacy direct dependency на
  giant `DialogService`;
- `Phase 6` расширен на дополнительный слой UI preset/bootstrap contract для
  remaining ключевых страниц панели.
