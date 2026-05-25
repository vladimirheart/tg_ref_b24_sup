## 2026-05-25 15:47:00 — dialog-details runtime continuity

- Добавлен `DialogDetailsIntegrationTest` для live
  `SpringBootTest + SQLite` contract вокруг `/api/dialogs/{ticketId}`.
- В details runtime-пакете закреплены summary/history/categories,
  responsible profile projection, embedded
  `replyPreview/originalMessage/editedAt/forwardedFrom`,
  `last_read_at` read receipt и explicit `404` payload.
- `DialogReadControllerWebMvcTest` расширен на not-found transport ветку, а
  `DialogDetailsReadServiceTest` добран miss-path short-circuit сценарием,
  чтобы `details` boundary был прикрыт и на controller/service уровне.
- Audit и roadmap синхронизированы: следующий focus в dialog
  read/workspace зоне смещён с basic details continuity на
  settings-driven context-contract, parity и related projection edge cases.
