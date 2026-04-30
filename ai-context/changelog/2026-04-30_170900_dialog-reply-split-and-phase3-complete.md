# 2026-04-30 17:09:00 — dialog reply split, telemetry summary assembler и закрытие Phase 3

## Что сделано

- вынесен `DialogReplyTargetService` для target lookup, ticket activity,
  web-form fallback и reply persistence;
- вынесен `DialogReplyTransportService` для Telegram/VK/MAX text/media
  transport;
- `DialogReplyService` переведён на thin orchestration contract поверх новых
  bounded dependencies;
- вынесен `DialogWorkspaceTelemetrySummaryAssemblerService`, а старый
  `DialogWorkspaceTelemetrySummaryBridgeService` удалён;
- `DialogWorkspaceTelemetrySummaryService` и `DialogService` переведены на
  прямое делегирование в новый summary assembler;
- обновлены targeted unit/WebMvc/integration tests для reply и telemetry
  summary слоя;
- синхронизированы `01-024`, roadmap и architecture audit;
- по исходной цели giant dialog split `Phase 3` отмечена как выполненная:
  `DialogService` доведён до thin facade, а следующий фокус смещён в
  `DialogWorkspaceService`, notifier/reply consumers и adjacent runtime
  boundaries.

## Проверка

- `.\mvnw.cmd -q "-Dtest=DialogReplyServiceTest,DialogWorkspaceTelemetrySummaryAssemblerServiceTest,DialogWorkspaceTelemetrySummaryServiceTest,DialogServiceTest,DialogWorkspaceControllerWebMvcTest,DialogQuickActionsControllerWebMvcTest" test`
- `.\mvnw.cmd -q "-Dtest=SupportPanelIntegrationTests#workspaceTelemetrySummaryBuildsRolloutScorecardWithUtcTimestamps,SupportPanelIntegrationTests#workspaceTelemetrySummaryBuildsGovernancePacketWithOwnerSignoffInUtc,SupportPanelIntegrationTests#workspaceTelemetrySummaryExpandsExternalCheckpointScorecardItems,SupportPanelIntegrationTests#replyToWebFormSessionPersistsOperatorMessageWithoutBotToken" test`
- `.\mvnw.cmd -q -DskipTests compile`
