# 2026-04-28 14:23:34 — DialogService external KPI split and rollout test stabilization

## Что сделано

- вынесен rollout bounded context `external_kpi_signal` из `DialogService`
  в новый `DialogWorkspaceExternalKpiService`;
- `DialogService` переведён на dependency этого сервиса и потерял ещё один
  крупный orchestration/helper блок;
- добавлен `DialogWorkspaceExternalKpiServiceTest`;
- `DialogServiceTest` синхронизирован с новым constructor contract;
- стабилизированы time-sensitive integration tests в
  `SupportPanelIntegrationTests`:
  rollout scorecard, governance packet и external checkpoint сценарии
  переведены на fresh UTC timestamps вместо устаревающих fixed дат;
- актуализированы `01-024`, roadmap и architecture audit под реальное
  сжатие giant service.

## Проверка

- `.\mvnw.cmd -q "-Dtest=DialogWorkspaceExternalKpiServiceTest,DialogServiceTest,DialogMacroGovernanceAuditServiceTest,DialogWorkspaceTelemetryAnalyticsServiceTest" test`
- `.\mvnw.cmd -q "-Dtest=SupportPanelIntegrationTests#workspaceTelemetrySummaryBuildsRolloutScorecardWithUtcTimestamps,SupportPanelIntegrationTests#workspaceTelemetrySummaryBuildsGovernancePacketWithOwnerSignoffInUtc,SupportPanelIntegrationTests#workspaceTelemetrySummaryExpandsExternalCheckpointScorecardItems" test`
- `.\mvnw.cmd -q -DskipTests compile`

## Заметки

- `logs/spring-panel.log` обновился от локальных прогонов Maven и вручную не редактировался.
