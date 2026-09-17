# Clean-code/source-layout phase P5x — object passport photo command

- Baseline: `afdb22acfa553fbc2e29df3f2a67e30336f7a837`.
- Extracts DB-side update/delete photo choreography into package-private `ObjectPassportPhotoCommand`.
- Keeps runtime DataSource, Connection lifecycle, transaction boundaries and public API in `ObjectPassportService`.
- Keeps photo storage I/O in the service; delete cleanup still runs only after successful DB commit.
- Keeps upload-photo storage/rollback compensation unchanged for a later narrow seam.
- Reuses `ObjectPassportPhotoQuery`, `ObjectPassportPhotoModel`, `ObjectPassportPayloadModel` and `ObjectPassportPersistence` without moving their ownership.
- Adds a direct command regression test covering update persistence, single-title behavior, delete stored-name handoff, no implicit title promotion, and caller-owned rollback.
- Rewires source-layout guards for the new consumer owner.
- No schema, endpoint, deployment, task-domain or unrelated mojibake changes.

## Verification

- Guard exact baseline and pinned source blobs.
- Apply first in detached worktree.
- Run targeted Maven tests through `mvnw.cmd`.
- Restore known generated CSS noise.
- Run exact changed-file gate and `git diff --check` before real checkout mutation.
- No stage/commit/push/deploy.
