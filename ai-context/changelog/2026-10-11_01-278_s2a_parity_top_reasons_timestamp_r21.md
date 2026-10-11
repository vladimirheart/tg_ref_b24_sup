# 01-278: parity top_reasons last_seen_at timestamp (R21)

Date: 2026-10-11. Stage: S2A source-only checkpoint.
User request (verbatim): я обновил репо. проанализируй и давай дальше по задаче

## Actual source scope

- `spring-panel/src/main/java/com/example/panel/service/DialogWorkspaceRolloutParityService.java`: keep JDBC `created_at` as `Instant` for the shared
  workspace parity reason accumulator instead of serializing it prematurely.
- `spring-panel/src/test/java/com/example/panel/service/DialogWorkspaceRolloutParityServiceTest.java`: assert nested `top_reasons[].last_seen_at` on the
  exact-ID scenario and add a per-reason timestamp/experiment regression.
- `ai-context/tasks/task-details/01-278.md`: record the bounded R21 contract, limits and validation gate.
- This new append-only changelog records the exact file scope.

## Verification and safety boundary

- The operator first builds the complete patch in its own detached worktree,
  runs the repository Maven wrapper against parity and telemetry regression
  classes and enforces an exact semantic changed-file set plus
  `git diff --check`. The Sass plugin may regenerate tracked CSS,
  which is tolerated only when it differs from HEAD exclusively in LF/CRLF.
  Tests and any non-EOL CSS changes remain blocking. Test totals are
  authoritative only in the operator log.
- No source mutation in `--validate`. `--apply` needs a separate exact
  authorization argument and repeats the detached-worktree verification.
- No schema/Flyway changes, legacy ticket reassignment, task provenance
  changes, private approval-file access, production DB writes, deployment,
  feature flag change, stage, commit or push.
- S2B business/product approval remains PENDING. This checkpoint does not
  grant a rollout or backfill authorization.
