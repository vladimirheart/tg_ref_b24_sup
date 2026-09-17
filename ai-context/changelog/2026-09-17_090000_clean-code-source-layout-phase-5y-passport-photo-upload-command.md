# Clean-code/source-layout phase P5y — object passport photo upload command

- Baseline: `3bdcf49ce295e8af926bd8ff35d76d74549316db`.
- Extends package-private `ObjectPassportPhotoCommand` with DB-side upload mutation ownership.
- Service still stores the file before opening the DB transaction and still owns rollback cleanup of the newly stored file.
- Command receives a neutral `UploadMetadata` record; it does not depend on `MultipartFile`, `StoredPhoto` or `ObjectPassportPhotoStorageService`.
- Upload payload normalization, photo-map creation, UUID allocation, category/caption normalization, single-title enforcement and passport-row persistence move from service to the command.
- Runtime DataSource, Connection lifecycle, transaction boundaries, public API and photo storage I/O remain in `ObjectPassportService`.
- Existing update/delete command ownership and post-commit delete cleanup remain unchanged.
- Adds direct regression coverage for upload persistence, metadata projection, single-title behavior and caller-owned transactions.
- Rewires source-layout guards for the completed photo command boundary.
- No schema, endpoint, deployment, task-domain or unrelated mojibake changes.

## Verification

- Guard exact baseline and pinned source blobs.
- Apply first in detached worktree.
- Run targeted Maven tests through `mvnw.cmd`.
- Restore known generated CSS noise.
- Run exact changed-file gate and `git diff --check` before real checkout mutation.
- No stage/commit/push/deploy.
