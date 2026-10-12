# 01-278 S2A: preserve exact legacy IDs in dialog routes and actions (R29)

Date: 2026-10-12. Source-only baseline `6846809ed0f804fb855fd46d164985003ae7d178`.

Prompt of operator: R28 user execution log supplied `TASK_01_278_S2A_R28_COMMIT_PUSH_RESULT=GREEN`,
`R28_FINAL_COMMIT_PUSH_RESULT=GREEN`, and `NEW_HEAD=6846809ed0f804fb855fd46d164985003ae7d178`.

- Preserve exact nonblank `ticketId` throughout five remaining dialogs UI boundary modules:
  task-create handoff, modal navigation, quick operator actions, media/history action URLs,
  and participant service calls. Whitespace is checked only for blankness, not stripped.
- New Node source-contract regression verifies padded/plain distinctness in helper,
  task-create routing, modal popstate, action resolver, history delete route and participants.
- Existing R27 regression also runs in a detached worktree. JS sources and emitted test
  pass `node --check`. Mutations remain bounded to five JS files, one test, task details,
  and this append-only changelog.
- Exact HEAD/tree/parent/origin, eight pinned Git blobs, clean Git checkout, new-file collision,
  no staged changes, exact modified file set, `git diff --check` and parity guards.
- R29 only validates by default; APPLY is explicit and separate. No staging, commit, push,
  runtime, database, queue, backfill, feature-flag, migration or rollout actions.
- R12 task provenance and S2B owner/product/aliases approvals remain PROPOSED_NOT_APPROVED.
