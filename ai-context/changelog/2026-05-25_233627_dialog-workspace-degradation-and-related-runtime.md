## 2026-05-25 23:36:27 — dialog-workspace degradation and related runtime

- `DialogWorkspaceIntegrationTest` расширен на live
  `SpringBootTest + SQLite` scenario для partial
  `include=context,permissions` вокруг `workspace` degradation/runtime слоя.
- В runtime-пакете закреплены settings-driven limits для
  `workspace_context_history_limit` и `workspace_context_related_events_limit`,
  disabled inline navigation и parity attention path без `messages/sla`,
  чтобы operator-facing workspace contract проверял не только happy-path.
- `DialogWorkspaceNavigationServiceTest` добран regression-сценарием для
  legacy local-datetime normalization в queue navigation items.
- Audit и roadmap синхронизированы: следующий focus в dialog
  read/workspace зоне смещён с базового parity/related bootstrap на
  deeper composer/permissions parity и adjacent read-model projection edges.
