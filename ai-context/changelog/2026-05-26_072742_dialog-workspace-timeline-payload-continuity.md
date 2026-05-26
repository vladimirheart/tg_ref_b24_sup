## 2026-05-26 07:27:42 — dialog-workspace timeline payload continuity

- `DialogWorkspaceIntegrationTest` расширен на live
  `SpringBootTest + SQLite` scenario для rich `workspace` timeline payload:
  media-only message preview, edited reply, deleted follow-up и
  attachment URL routing теперь проверяются прямо через
  `/api/dialogs/{ticketId}/workspace`.
- В runtime-контракте закреплены `replyPreview` fallback для media-only
  сообщений, `originalMessage`, `editedAt`, `deletedAt`,
  `forwardedFrom`, ticket attachment URL и `by-path` attachment routing,
  чтобы эти semantics не оставались покрытыми только в `details/read`.
- `DialogWorkspacePayloadAssemblerServiceTest` добран на full included
  payload с `messages/context/sla/meta`, escalation state и parity/meta
  envelope, чтобы `workspace` payload continuity была закреплена и на
  unit assembly слое.
- Audit и roadmap синхронизированы: следующий focus в dialog
  read/workspace зоне смещён с timeline payload continuity на deeper
  operator workflow parity и adjacent projection drift edge cases.
