## 2026-05-25 23:16:22 — dialog-workspace settings contract runtime

- `DialogWorkspaceIntegrationTest` расширен на live
  `SpringBootTest + SQLite` scenario для settings-driven `workspace`
  context contract вокруг rollout-required `billing` кейса.
- В runtime-пакете закреплены `mandatory_field`, `source_of_truth`,
  `priority_block` violations, playbook projection и живой
  `invalid_utc` source status для `crm`, чтобы workspace boundary
  проверял не только базовый envelope, но и реальный config-driven drift.
- `DialogWorkspaceContextContractServiceTest` добран unit-сценарием
  для `source_of_truth:phone:crm:invalid_utc` со scoped playbook,
  чтобы service-level contract совпадал с live runtime поведением.
- Audit и roadmap синхронизированы: следующий focus в dialog
  read/workspace зоне смещён с basic settings bootstrap на
  parity/related projections и operator-facing workspace context edges.
