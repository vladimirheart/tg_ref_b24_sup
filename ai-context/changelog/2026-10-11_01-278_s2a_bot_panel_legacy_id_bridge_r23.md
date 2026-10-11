# 01-278: exact legacy ID at the bot -> Panel API bridge (R23)

Date: 2026-10-11. Stage S2A. Source-only, independent of pending S2B approvals.

`Prompts of user`: Earlier request `я обновил репо. проанализируй и давай дальше по задаче`, followed by the operator result
`TASK_01_278_S2A_R22_COMMIT_PUSH_RESULT=GREEN` and `R22_OPERATOR_RESULT=GREEN`.
User's long log is represented by those exact non-sensitive conclusion lines.

## Scope

- `java-bot/bot-core/src/main/java/com/example/supportbot/service/PanelTicketReadClient.java`: preserve the exact ID in two Panel GET paths.
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/PanelTicketWriteClient.java`: preserve the exact ID in five Panel mutation paths.
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/PanelTicketClientsExactLegacyIdTest.java`: local HTTP path+method tests and blank-input guard.
- `ai-context/tasks/task-details/01-278.md`: append the R23 source checkpoint and guard limits.
- This file is append-only, not a revision of a historical changelog.

## Guarantees

- URL space encoding remains unchanged: the original leading/trailing spaces are
  percent-encoded, not removed or silently mapped to a plain-ID sibling.
- Guarded sandbox uses exact HEAD/tree/parent, fixed blob pins, 3 tracked and 2 new
  paths, Maven wrapper with bot-core reactor scope, 3 targeted tests and diff checks.
- No working checkout mutation on validate. Apply requires a separate explicit token,
  a fresh second preflight and parity with the tested sandbox. No stage, commit, push.
- No source modifications to bot-local UI outbox, Panel backend, migrations, DB, queues,
  environment, containers, flags or private S2B artifacts.
- Approval status remains PROPOSED_NOT_APPROVED. Controlled write, backfill and
  production deployment are not authorized by this checkpoint.
