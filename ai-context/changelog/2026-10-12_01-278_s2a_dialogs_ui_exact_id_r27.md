# 01-278 S2A: exact legacy ticket IDs in dialogs UI (R27)

Date: 2026-10-12. Source-only; baseline `8aba39db1d30a33bc038ca38ccbce3b33db73354`.

Промт пользователя: The latest user message supplied `TASK_01_278_S2A_R26_COMMIT_PUSH_RESULT=GREEN`,
`R26_FINAL_COMMIT_PUSH_RESULT=GREEN` and `NEW_HEAD=8aba39db1d30a33bc038ca38ccbce3b33db73354`.

- Five existing UI JavaScript files preserve exact nonblank ID across workspace contract,
  row lookup, draft storage, macro catalog/workflow, AI requests, pinned list persistence,
  own-dialog list, and realtime events. All replaced anchors have immutable counts.
- New Node source contract test verifies exact vs padded vs blank ID, workspace contract
  fail-closed mismatch, row/channel selection and separate draft storage keys.
- Task detail receives an additive R27 section; prior changelogs are not edited.
- Guarded operator: exact HEAD/tree/parent/origin, seven pinned Git blobs, clean root,
  two collision-checked new files, detached worktree, syntax + runtime contract test,
  changed-file/diff/content checks, separate source-only APPLY authorization.
- No staging, commit, push, production runtime/DB/queue, Flyway migration, backfill
  or rollout. S2B and R12 remain PROPOSED_NOT_APPROVED / PENDING.
