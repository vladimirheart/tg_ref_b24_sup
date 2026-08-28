# Storage pre-cutover gates GREEN — 2026-08-28

## Live operator evidence

Windows production host successfully completed the authoritative storage cutover gate and client-avatar audit before disabling legacy local fallback.

### Storage cutover gate

- `attachment_mappings_checked=72`
- `raw_missing_s3_dialog_objects=20`
- `known_unrecoverable_dialog_objects=20`
- `unexpected_missing_s3_dialog_objects=0`
- `stale_known_unrecoverable_entries=0`
- `missing_metadata_rows=0`
- `missing_s3_panel_avatars=0`
- `invalid_panel_avatar_refs=0`
- Result: `STORAGE CUTOVER GATE PASSED`

The 20 raw missing dialog objects exactly match the reviewed known-unrecoverable manifest. No unexpected attachment migration gaps remain.

### Client-avatar audit

- `client_avatar_history_users_checked=0`
- `missing_s3_client_avatars=0`
- Result: `CLIENT AVATAR CUTOVER AUDIT PASSED`

There are currently no `client_avatar_history` users to migrate, so the client-avatar gate is vacuously GREEN rather than skipped.

## Current cutover state

`APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED` must remain `true` until the required manual UI checks pass:

- `/login` loads and authentication succeeds;
- dialogs/cards load normally;
- representative canonical historical attachments can be previewed/downloaded;
- panel/operator avatars render normally where configured;
- reviewed known-unrecoverable attachments remain explicitly unavailable rather than being replaced with synthetic objects.

After manual UI GREEN, recreate the runtime containers with fallback disabled, then rerun both cutover gates and the UI/media checks before preparing any legacy-storage purge procedure.
