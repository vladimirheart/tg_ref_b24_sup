## 2026-05-06 10:49 - SLA routing runtime/parser/evaluation-context wide split

- added `SlaRoutingPolicySnapshotSettingsService` for dialog-config extraction,
  orchestration flags, mode resolution and base snapshot payload assembly;
- added `SlaRoutingPolicySnapshotContextService` for missing-ticket and
  final dialog snapshot runtime context assembly;
- added `SlaRoutingRuleDefinitionMatchService` and
  `SlaRoutingRuleWinnerSelectionService` to decouple parser/usage-analysis
  from direct candidate matching and winner selection logic;
- added `SlaRoutingRuleEvaluationContextService` for reusable per-rule
  coverage/conflict/broad-rule context used by audit evaluation;
- refactored `SlaRoutingPolicySnapshotRuntimeService`,
  `SlaRoutingRuleParserService`, `SlaRoutingRuleUsageAnalysisService` and
  `SlaRoutingRuleAuditEvaluationService` into thinner coordinator-style
  services;
- added targeted tests for snapshot settings/context, definition match,
  winner selection and evaluation context;
- updated architecture audit, UI refactoring roadmap and task detail `01-024`
  to reflect the new post-phase hardening baseline.
