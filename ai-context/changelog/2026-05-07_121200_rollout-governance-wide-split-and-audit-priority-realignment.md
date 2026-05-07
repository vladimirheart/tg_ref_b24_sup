# 2026-05-07 12:12 rollout governance wide split and audit priority realignment

- `DialogWorkspaceRolloutGovernanceService` переведён на bounded services:
  `DialogWorkspaceRolloutGovernanceConfigService`,
  `DialogWorkspaceRolloutParityService`,
  `DialogWorkspaceRolloutLegacyInventoryService`,
  `DialogWorkspaceRolloutContextContractService` и
  `DialogWorkspaceRolloutLegacyUsagePolicyService`.
- Возвращён legacy compatibility contract для rollout governance packet:
  generic `source_of_truth` / `priority_block` playbooks снова покрывают
  scoped context keys, parity fallback снова отдаёт
  `error=telemetry_unavailable`, а `invalid_utc_items` учитывает section-level
  review timestamp flags.
- Focused rollout integration regressions из `SupportPanelIntegrationTests`
  снова зелёные.
- `ARCHITECTURE_AUDIT_2026-04-08.md`, `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`
  и `01-024.md` синхронизированы: `Phase 3` и `Phase 4` остаются выполненными,
  а главный next-step focus смещён с `DialogWorkspaceRolloutGovernanceService`
  на `DialogAiAssistantService`, `PublicFormService` и cross-module
  runtime/shared-config contract.
