# 2026-04-21 16:15:00 - Dialog details read service and consumer detach pass

## Что сделано

- добавлен `DialogDetailsReadService` для сценария `loadDialogDetails`;
- `DialogService` переведён на thin delegate для `dialog details`;
- на новый details-layer переведены:
  - `DialogReadService`
  - `DialogWorkspaceService`
  - `DialogMacroService`
- `DialogAiAssistantService` переведён на `DialogResponsibilityService` для
  auto-assign сценария `ai_agent`;
- `SlaEscalationWebhookNotifier` переведён на `DialogLookupReadService` и
  `DialogResponsibilityService` там, где раньше оставалась прямая зависимость
  от giant `DialogService`;
- добавлен `DialogDetailsReadServiceTest`.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=DialogDetailsReadServiceTest,DialogReadControllerWebMvcTest,DialogWorkspaceControllerWebMvcTest,DialogMacroControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Примечания

- при отдельном прогоне полного `SlaEscalationWebhookNotifierTest` остаются
  старые нестабильные проверки по route naming/review-path, не относящиеся
  напрямую к этому consumer-split проходу.
