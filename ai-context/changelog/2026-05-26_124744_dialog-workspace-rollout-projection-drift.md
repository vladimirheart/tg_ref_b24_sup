## 2026-05-26 12:47:44 — dialog-workspace rollout projection drift

- `DialogWorkspaceService` расширен: `meta.rollout` теперь получает не
  только base rollout/fallback flags, но и `external_kpi_signal`
  вместе с compact governance summary из rollout bounded services.
- `DialogWorkspaceIntegrationTest` добран live
  `SpringBootTest + SQLite` scenario на `/api/dialogs/{ticketId}/workspace`
  с проверкой experiment metadata, external KPI readiness/risk,
  governance gates и legacy manual policy в одном runtime payload.
- Проверка замыкает projection drift между `workspace` endpoint и
  rollout telemetry/assessment/governance bounded слоями: operator-facing
  rollout contract теперь доступен прямо из рабочего payload, а не
  только через отдельные summary endpoints.
- Audit и roadmap синхронизированы: следующий focus в dialog
  read/workspace зоне смещён с rollout projection plumbing на deeper
  operator workflow parity и adjacent action/projection drift edge cases.
