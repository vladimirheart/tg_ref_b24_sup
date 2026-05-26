## 2026-05-26 06:53:46 — dialog-workspace permissions parity contract

- `DialogWorkspaceIntegrationTest` расширен на live
  `SpringBootTest + SQLite` scenario для `/api/dialogs/{ticketId}/workspace`
  с explicit denied operator permissions и operator-facing parity payload.
- В runtime-пакете закреплено различие между incomplete permission contract
  и explicit boolean deny: missing permission envelope остаётся `blocked`,
  а полный boolean contract с denied actions даёт `attention` вместе с
  missing `reply_threading/media_reply`, но без деградации
  `operator_actions`.
- `DialogWorkspaceParityServiceTest` добран ветками на composer disable
  без `can_reply` и на parity semantics для explicit denied permissions,
  чтобы `workspace` contract был прикрыт и на unit, и на integration слое.
- Audit и roadmap синхронизированы: следующий focus в dialog
  read/workspace зоне смещён с базовой parity semantics на deeper
  operator-facing projection drift и adjacent read-model/composer edges.
