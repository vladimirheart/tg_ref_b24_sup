# Clean-code/source-layout phase P5z — object passport tasks query

- Baseline: `628c5497d38384018f9234f570a2422a65b4d4ff`.
- Extracts the current empty tasks projection and passport existence check into package-private `ObjectPassportTasksQuery`.
- `ObjectPassportService` keeps runtime DataSource and Connection lifecycle; the query receives the caller-owned `Connection`.
- Preserves the existing tasks response shape: `success=true`, empty `items`, zero `total_minutes` and `0 мин` display.
- Preserves missing-passport behavior through `ObjectPassportPersistence.loadStoredPassport(...)`.
- Removes the service-only `ensurePassportExists(...)` helper and the last direct runtime persistence read from the service.
- Adds direct query regression coverage for response projection, 404 propagation and caller-owned connection state.
- Rewires source-layout and persistence ownership guards for the new query owner.
- No schema, endpoint, deployment, photo-storage, transaction-boundary or unrelated mojibake changes.

## Verification

- Guard exact baseline and pinned source blobs.
- Apply first in detached worktree.
- Run targeted Maven tests through `mvnw.cmd`.
- Restore known generated CSS noise.
- Run exact changed-file gate and `git diff --check` before real checkout mutation.
- No stage/commit/push/deploy.
