# 2026-04-30 11:48:00 — DialogService dead legacy tail removed

## Что сделано

- из `DialogService` удалён уже мёртвый private legacy/support слой,
  который целиком дублировался в `DialogLookupReadService` и
  `DialogResponsibilityService`;
- из самого фасада убраны старые `loadDialogsLegacy/findDialogLegacy`,
  responsible-profile enrichment helper’ы, users-table inspection и
  legacy responsibility-update private methods;
- из constructor `DialogService` убраны уже ненужные
  `JdbcTemplate/usersJdbcTemplate/sharedConfigService`, а `DialogServiceTest`
  синхронизирован с новым thin-constructor contract;
- `DialogService` сжался примерно до `275` строк и фактически стал thin
  orchestration facade;
- `01-024`, roadmap и architecture audit пересобраны так, чтобы следующий
  фокус был уже не в самом `DialogService`, а в `DialogWorkspaceService`,
  notifier/reply consumers и remaining settings/runtime tails.

## Проверка

- `.\mvnw.cmd -q -DskipTests compile`
- `.\mvnw.cmd -q "-Dtest=DialogServiceTest,DialogLookupReadServiceTest,DialogResponsibilityServiceTest,DialogWorkspaceRolloutGovernanceServiceTest,DialogWorkspaceRolloutAssessmentServiceTest,DialogMacroGovernanceAuditServiceTest,DialogWorkspaceTelemetryAnalyticsServiceTest" test`
- `.\mvnw.cmd -q "-Dtest=SupportPanelIntegrationTests#workspaceTelemetrySummaryBuildsRolloutScorecardWithUtcTimestamps,SupportPanelIntegrationTests#workspaceTelemetrySummaryBuildsGovernancePacketWithOwnerSignoffInUtc,SupportPanelIntegrationTests#workspaceTelemetrySummaryExpandsExternalCheckpointScorecardItems" test`

## Заметки

- `logs/spring-panel.log` и `spring-panel/settings.db` обновились от локальных Maven/integration прогонов и вручную не редактировались.
