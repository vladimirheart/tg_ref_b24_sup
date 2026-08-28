# Storage cutover manual verification and purge runbook

Date: 2026-08-28
Task: 01-218

User prompt:
- "UI/media после отключения fallback проверены"
- "панель пока не останавливал"

Changes:
- recorded the successful live storage fallback cutover with `APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=false` persisted in `.env` and active in healthy `ops-worker` / `panel-web` containers;
- recorded GREEN post-cutover storage and client-avatar audits, including `unexpected_missing_s3_dialog_objects=0`, `stale_known_unrecoverable_entries=0`, `missing_metadata_rows=0`, panel-avatar gaps `0`, and client-avatar gaps `0`;
- recorded the exact cutover rollback backup path `C:\Users\SinicinVV\git_h\tg_ref_b24_sup\.env.storage-cutover-20260828-162458.bak`;
- recorded the user's manual post-cutover UI/media verification and the fact that the full production contour was not stopped;
- added a dedicated manual purge/rollback runbook that forbids broad deletion, uses explicit candidate manifests and quarantine first, excludes canonical and legacy MinIO deletion, and requires a separate rollback-window closure before physical deletion;
- marked task `01-218` GREEN because the user manually verified the production result; the task was not archived.

Files and areas:
- `docs/runbooks/storage-legacy-purge-and-rollback.md`
- `ai-context/tasks/task-details/01-218.md`
- `ai-context/tasks/task-list.md`
- `ai-context/changelog/2026-08-28_162600_storage-cutover-manual-verification-and-purge-runbook.md`

Safety:
- no runtime container was stopped or recreated by these documentation/task updates;
- no local legacy file was moved or deleted;
- no PostgreSQL row was changed;
- no MinIO object was changed or deleted;
- canonical `iguana/...` objects and legacy unprefixed MinIO objects remain untouched;
- physical purge remains a separate future operator action.

Verification basis:
- user-provided live outputs from the successful cutover helper and explicit `.env` / runtime `fallback=false` checks;
- user-provided manual confirmation that UI/media work after fallback was disabled.
