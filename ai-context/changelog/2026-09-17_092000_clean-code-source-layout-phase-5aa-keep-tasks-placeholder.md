# Clean-code/source-layout phase P5aa — keep object passport tasks placeholder in service

- Baseline: `2d1a49b72f7416bc5b6fd9d3aa9aa8496d82e522`.
- Corrects P5z after re-reading the handoff rule that explicitly says not to create a dedicated class only for the current empty tasks placeholder.
- Removes `ObjectPassportTasksQuery` and its direct unit test.
- Restores the existing service-local tasks placeholder and `ensurePassportExists(...)` helper without changing response shape or 404 behavior.
- Keeps all P5x/P5y photo-command ownership, persistence extraction, query owners and transaction/storage boundaries unchanged.
- Reverts only the P5z source-layout/persistence guard changes that depended on `ObjectPassportTasksQuery`.
- No schema, endpoint, deployment, photo-storage, transaction-boundary, task-domain implementation or unrelated mojibake changes.

## Verification

- Guard exact P5z baseline and pinned source blobs.
- Apply first in detached worktree.
- Run targeted Maven tests through `mvnw.cmd`.
- Restore known generated CSS noise.
- Run exact changed-file gate and `git diff --check` before real checkout mutation.
- No stage/commit/push/deploy.
