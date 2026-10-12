# 01-278 / S2A / R41 — preserve exact ticket identity in attachment metadata

Date: 2026-10-12. Source-only baseline: `40595d0a75efe5ac09b5511d266d5810e905d38d`.

User prompt: R40 read-only audit report with both result sentinels GREEN,
11 pinned source blobs and 16 identity-normalization markers; continue task 01-278.

- Change ONLY INSERT argument for `chat_attachment_metadata.ticket_id` in panel
  and bot metadata services: exact original nonblank opaque ticket ID or NULL.
- Preserve existing text trimming for names/MIME/ref and existing storage-key behavior.
  Channel-owned bot paths must not be rewritten as ticket paths by this step.
- New 3-case panel Mockito test and 3 added bot Mockito cases cover padded/plain,
  literal null/non-null and missing/blank identity; include legacy regressions.
- Guarded detached Maven validation precedes explicit source apply.
- No runtime/working DB/storage objects or historical metadata changed.
  This is NOT an attachment-key writer/reader fix and does not enable scope.
- S2B policy and R12 task provenance remain unapproved; no backfill or deployment.
