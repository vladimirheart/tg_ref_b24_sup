## 2026-05-26 08:38:48 — dialog-workspace rollout governance fallback contract

- `DialogWorkspaceRolloutServiceTest` расширен на `cohort_rollout`,
  stale review и invalid review timestamp semantics для
  `legacy_manual_open_policy`, включая experiment metadata,
  operator segment и reason catalog contract.
- `DialogWorkspaceIntegrationTest` добран live
  `SpringBootTest + SQLite` scenario для `/api/dialogs/{ticketId}/workspace`
  с `meta.rollout` projection: `cohort_rollout`, experiment metadata,
  fallback availability и blocked manual-open policy теперь проверяются
  прямо через runtime payload.
- В `workspace` contract зафиксирован operator-facing fallback semantics
  слой: `legacy modal` manual open policy больше не покрывается только на
  config-parser уровне, а проходит через реальный endpoint envelope.
- Audit и roadmap синхронизированы: следующий focus в dialog
  read/workspace зоне смещён с rollout/bootstrap semantics на deeper
  operator workflow parity и adjacent projection drift edge cases.
