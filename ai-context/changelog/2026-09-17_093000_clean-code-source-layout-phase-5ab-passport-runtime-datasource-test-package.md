# Clean-code/source-layout phase P5ab — object passport runtime DataSource test package

- Baseline: `c80b84d31c30f58009ce4d4bb91672d972889c6e`.
- Moves `ObjectPassportServiceRuntimeDataSourceTest` from the generic `com.example.panel.service` test package into the feature-owned `com.example.panel.passports` test package.
- Removes the redundant import of `ObjectPassportService` after the test joins the service package.
- Extends the existing source-layout contract so the old test path must stay absent and the feature-owned path/package must stay present.
- Test behavior and runtime DataSource assertions are unchanged.
- No production Java source, schema, endpoint, transaction, storage, task-domain or deployment changes.

## Verification

- Guard exact baseline and pinned test/source-layout blobs.
- Apply first in detached worktree.
- Run the moved runtime DataSource test and source-layout contract through repo `mvnw.cmd`.
- Restore known generated CSS noise.
- Run exact changed-file gate and `git diff --check` before real checkout mutation.
- No stage/commit/push/deploy.
