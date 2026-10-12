# 01-278 S2A: bot-local UI outbox exact legacy ticket ID (R25)

Date: 2026-10-11. Source-only checkpoint. Baseline commit: `0ae40335eb6c717a706cc5eb7bb87400c0c15daf`.

Промт пользователя: The latest message supplied the completed R24 operator result:
`TASK_01_278_S2A_R24_COMMIT_PUSH_RESULT=GREEN`,
`R24_FINAL_COMMIT_PUSH_RESULT=GREEN`, and
`NEW_HEAD=0ae40335eb6c717a706cc5eb7bb87400c0c15daf`.
This R25 source review follows the previously agreed next step concerning
`java-bot/UiEventOutboxService`; the full operational log is not repeated.

## Exact changed-file scope

- `java-bot/bot-core/src/main/java/com/example/supportbot/service/UiEventOutboxService.java`: pass the original nonblank `ticketId` to JDBC, without trim.
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/UiEventOutboxExactLegacyIdTest.java`: four deterministic JUnit/JDBC-capture regression tests.
- `ai-context/tasks/task-details/01-278.md`: append the R25 contract and test scope.
- This new append-only changelog; no existing changelog rewritten.

## Validation and safety boundary

- Detached worktree from exact HEAD/tree/parent; seven pinned Git blobs;
  exactly two tracked and two new files. Strict Maven wrapper reactor test
  classes R25 (4 tests) and R23 (3 tests), then exact-file and content guards.
- `--validate` does not write to the normal checkout; `--apply` requires
  an explicit R25 authorization and a second fresh Git/source preflight.
- No staging, commit, push, deployment, production DB or runtime changes,
  queues, env, container mutation, schema/Flyway or protected private records.
- Data scope/authorization is not granted by a notification outbox row;
  S2B decisions and task provenance are PROPOSED_NOT_APPROVED / PENDING.
