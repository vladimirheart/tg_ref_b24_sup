# Storage cutover: reviewed known-unrecoverable historical attachments

Date: 2026-08-28

## Live evidence

Production storage repair completed with `metadata_rows_checked=72`, `canonical_already_present=52`, `still_missing=20`, `execution_errors=0`.

A read-only local source audit then reported `missing_metadata_rows_considered=20`, `rows_with_unique_local_candidate=0`, `rows_with_ambiguous_local_candidates=0`, `rows_with_no_local_candidate=20`.

A final read-only search by exact missing filenames across the Windows user profile reported `missing_rows=20`, `rows_found_elsewhere=0`, `rows_still_unrecoverable=20`.

The missing bytes are therefore not present in canonical MinIO, legacy unprefixed MinIO, current attachment roots, the old absolute `Documents\\tg_bot\\...` attachment root, or other searched user-profile directories. Local fallback cannot serve these records because the local bytes are absent.

## Cutover policy

The 20 reviewed historical losses are recorded in `ai-context/storage-known-unrecoverable-dialog-attachments.json` using exact `metadata_id` plus exact UTF-8 `storage_key`.

`scripts/docker-production-storage-cutover-gate.ps1` is the authoritative storage gate for final cutover. It:

- keeps the existing raw storage audit for metadata-row and panel-avatar checks;
- independently reads attachment `storage_key` values from PostgreSQL as UTF-8 HEX to avoid Windows console mojibake;
- independently executes canonical MinIO `mc stat` through the LF-only `scripts/internal/storage-cutover-object-stat.sh` helper;
- requires every manifest entry to remain the same metadata row/key with `availability_status=missing` and a physically absent canonical object;
- fails on any unexpected missing object outside the manifest;
- fails if a manifest entry becomes stale because its object is recovered, metadata changes, or the row disappears;
- performs no DB updates, MinIO copies/deletes, or local file changes.

No empty or synthetic replacement objects are created for the lost historical payloads.

## Required live gate before fallback disable

Run `docker-production-storage-cutover-gate.ps1 -ValidateOnly`, then the live gate. A valid result requires:

- `known_unrecoverable_dialog_objects=20`
- `unexpected_missing_s3_dialog_objects=0`
- `stale_known_unrecoverable_entries=0`
- `missing_metadata_rows=0`
- `missing_s3_panel_avatars=0`
- `invalid_panel_avatar_refs=0`

The client-avatar audit remains a separate required gate. Legacy fallback stays enabled until both gates and manual UI/media validation are green.
