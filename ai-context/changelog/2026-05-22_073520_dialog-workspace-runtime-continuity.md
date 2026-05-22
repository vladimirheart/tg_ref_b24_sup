## 2026-05-22 07:35:20 — dialog-workspace runtime continuity

- Добавлен `DialogWorkspaceIntegrationTest` для live
  `SpringBootTest + SQLite` contract вокруг
  `/api/dialogs/{ticketId}/workspace`.
- В integration-пакете закреплены `messages` pagination,
  `replyPreview`, `last_read_at` read receipt, `permissions/composer`
  parity, inline navigation и default `context/history/related_events`
  projection на реальном workspace route.
- `DialogWorkspaceControllerWebMvcTest` расширен на default envelope path,
  чтобы controller boundary отдельно фиксировал omitted
  `channelId/include/limit/cursor`.
- Audit и roadmap синхронизированы: следующий focus в dialog
  read/workspace зоне смещён с runtime bootstrap на `details` continuity и
  settings-driven context-contract edge cases.
