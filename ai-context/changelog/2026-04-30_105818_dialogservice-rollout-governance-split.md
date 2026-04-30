# 2026-04-30 10:58:18 — DialogService rollout governance split

## Что сделано

- из giant `DialogService` вынесен ещё один rollout bounded context в новый
  `DialogWorkspaceRolloutGovernanceService`;
- новый сервис теперь владеет governance packet assembly, parity exit
  criteria, legacy-only inventory, legacy manual-open policy и
  context-contract rollout packet orchestration;
- `DialogService` переведён на thin delegate к новому bounded service и
  больше не держит локальный rollout governance helper-блок;
- добавлен `DialogWorkspaceRolloutGovernanceServiceTest`;
- `DialogServiceTest` синхронизирован с новым constructor contract;
- актуализированы `01-024`, roadmap и architecture audit под новое
  фактическое сжатие giant service примерно до `902` строк.

## Проверка

- `.\mvnw.cmd -q "-Dtest=DialogWorkspaceRolloutGovernanceServiceTest,DialogWorkspaceRolloutAssessmentServiceTest,DialogServiceTest,DialogMacroGovernanceAuditServiceTest,DialogWorkspaceTelemetryAnalyticsServiceTest" test`
- `.\mvnw.cmd -q "-Dtest=SupportPanelIntegrationTests#workspaceTelemetrySummaryBuildsRolloutScorecardWithUtcTimestamps,SupportPanelIntegrationTests#workspaceTelemetrySummaryBuildsGovernancePacketWithOwnerSignoffInUtc,SupportPanelIntegrationTests#workspaceTelemetrySummaryExpandsExternalCheckpointScorecardItems" test`
- `.\mvnw.cmd -q -DskipTests compile`

## Заметки

- `logs/spring-panel.log` и `spring-panel/settings.db` обновились от локальных Maven/integration прогонов и вручную не редактировались.
