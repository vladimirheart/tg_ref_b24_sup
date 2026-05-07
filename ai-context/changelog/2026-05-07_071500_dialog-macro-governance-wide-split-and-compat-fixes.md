# 2026-05-07 07:15 — Dialog macro governance wide split and compatibility fixes

## Что сделано

- разрезан macro-governance audit slice на
  `DialogMacroGovernanceConfigService`,
  `DialogMacroGovernanceTemplateAuditService`,
  `DialogMacroGovernanceCheckpointService` и
  `DialogMacroGovernanceAuditPayloadService`;
- `DialogMacroGovernanceAuditService` переведён на thin coordinator contract;
- в `DialogMacroGovernanceSupportService` убран fragile SQL-count path:
  usage aggregation теперь корректно работает и для mixed SQLite
  epoch-millis timestamps;
- в `DialogMacroGovernanceConfigService` добавлен epoch-millis parsing для
  review timestamps;
- в `SettingsMacroTemplateService` сохранён входной `deprecated_at` для новых
  deprecated macro templates;
- в `DialogMacroGovernanceCheckpointService` возвращён compatibility fallback
  для minimum required review/external catalog checkpoints и historical
  freshness metrics;
- в `DialogMacroGovernanceAuditPayloadService` восстановлена historical noise
  heuristic через advisory-load over active macro pool.

## Проверка

- `spring-panel\.\mvnw.cmd -q -DskipTests compile`
- `spring-panel\.\mvnw.cmd -q "-Dtest=DialogMacroGovernanceSupportServiceTest,DialogMacroGovernanceConfigServiceTest,SettingsMacroTemplateServiceTest,DialogMacroGovernanceTemplateAuditServiceTest,DialogMacroGovernanceCheckpointServiceTest,DialogMacroGovernanceAuditPayloadServiceTest,DialogMacroGovernanceAuditServiceTest,SupportPanelIntegrationTests#macroGovernanceAuditHighlightsOwnershipReviewAndUsageGaps" test`
- `spring-panel\.\mvnw.cmd -q "-Dtest=DialogMacroGovernanceSupportServiceTest,DialogMacroGovernanceConfigServiceTest,SettingsMacroTemplateServiceTest,DialogMacroGovernanceTemplateAuditServiceTest,DialogMacroGovernanceCheckpointServiceTest,DialogMacroGovernanceAuditPayloadServiceTest,DialogMacroGovernanceAuditServiceTest,DialogWorkspaceProfileEnrichmentServiceTest,DialogWorkspaceContextGraphServiceTest,SupportPanelIntegrationTests#macroGovernanceAuditHighlightsOwnershipReviewAndUsageGaps" test`

## Что это меняет

- macro-governance audit больше не является крупным mixed helper layer;
- post-phase hardening фокус смещён обратно в более тяжёлые bounded contexts:
  `DialogWorkspaceRolloutGovernanceService`,
  `DialogAiAssistantService` и `PublicFormService`;
- notifier/runtime остаётся в режиме локального hardening и
  integration-quality, а не нового giant split.
