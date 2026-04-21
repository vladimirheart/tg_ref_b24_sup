# 2026-04-21 15:59:00 - Dialog ticket lifecycle and audit service split

## Что сделано

- добавлены `DialogTicketLifecycleService` и `DialogAuditService` как новые
  bounded-context слои внутри домена `dialogs`;
- `DialogService` переведён на thin delegates для `resolve/reopen/categories`
  и `dialog action/workspace telemetry logging`;
- прямые потребители переведены на новые зависимости:
  `DialogQuickActionService`, `DialogAuthorizationService`,
  `DialogWorkspaceTelemetryService`, `AnalyticsController`,
  `DialogTriagePreferencesController`;
- `PublicFormService` переведён на `DialogAuditService` для события
  `public_form_submit`;
- `SlaEscalationWebhookNotifier` подготовлен к использованию нового
  audit-слоя с backward-compatible test constructor;
- добавлены `DialogAuditServiceTest` и `DialogTicketLifecycleServiceTest`;
- обновлены `AnalyticsControllerWebMvcTest` и
  `DialogTriagePreferencesControllerWebMvcTest`.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=DialogAuditServiceTest,DialogTicketLifecycleServiceTest,AnalyticsControllerWebMvcTest,DialogTriagePreferencesControllerWebMvcTest,DialogQuickActionsControllerWebMvcTest,DialogWorkspaceTelemetryControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Результат

- giant `DialogService` ещё не добит полностью, но из него вынесен ещё один
  самостоятельный write-side слой, а audit logging перестал быть привязанным
  к одной монолитной точке входа.
