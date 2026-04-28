# 2026-04-28 14:56:56 — DialogService rollout assessment split

## Что сделано

- из giant `DialogService` вынесен ещё один rollout bounded context в новый
  `DialogWorkspaceRolloutAssessmentService`;
- новый сервис теперь владеет rollout decision logic, scorecard assembly и
  external checkpoint itemization;
- `DialogService` переведён на новый bounded service и больше не держит
  constructor dependency на `DialogWorkspaceExternalKpiService`;
- добавлен `DialogWorkspaceRolloutAssessmentServiceTest`;
- `DialogServiceTest` синхронизирован с новым constructor contract;
- актуализированы `01-024`, roadmap и architecture audit под новое
  фактическое сжатие giant service примерно до `2533` строк.

## Проверка

- `.\mvnw.cmd -q "-Dtest=DialogWorkspaceRolloutAssessmentServiceTest,DialogServiceTest,DialogMacroGovernanceAuditServiceTest,DialogWorkspaceTelemetryAnalyticsServiceTest" test`
- `.\mvnw.cmd -q "-Dtest=SupportPanelIntegrationTests#workspaceTelemetrySummaryBuildsRolloutScorecardWithUtcTimestamps,SupportPanelIntegrationTests#workspaceTelemetrySummaryBuildsGovernancePacketWithOwnerSignoffInUtc,SupportPanelIntegrationTests#workspaceTelemetrySummaryExpandsExternalCheckpointScorecardItems" test`
- `.\mvnw.cmd -q -DskipTests compile`

## Заметки

- `logs/spring-panel.log` и `spring-panel/settings.db` обновились от локальных Maven/integration прогонов и вручную не редактировались.
