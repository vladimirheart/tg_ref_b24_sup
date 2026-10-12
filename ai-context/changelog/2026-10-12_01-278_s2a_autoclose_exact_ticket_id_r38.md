# 01-278 S2A R38 - exact ticket ID in auto-close follow-up

Date: 2026-10-12. Source-only baseline `0198a1b21cda31be3ada30628f77ba8d01d3a4ab`, tree `24eadde1bbfd1aac89425c775f000a1156edf2c8`.

- Continue after R37, preserving all R31-R37 commits without reapplying them.
- Keep the exact nonblank `ticketId` across responsibility lookup, message,
  participant JDBC lookup and TaskService task-link payload. Do not borrow
  another ticket with the same trimmed ID.
- Encode the ticket ID as a URL path segment for the task-body `/dialogs/` link;
  preserve escaped literal ID in the link text. Plain ASCII legacy URLs remain
  the same. Keep employee/description/content normalization unchanged.
- Add four Mockito regressions plus the existing three auto-close tests and
  one R37 task-link regression; run in detached Maven reactor before APPLY.
- Verify exact four-file scope, pinned baseline/tree/parent/blobs, clean root,
  script parse, test reports, diff checks and content parity on both EOL modes.
- S2B owner/product approvals and R12 provenance are PROPOSED_NOT_APPROVED.
  No production DB, migration/backfill, queue, flags, deployment, commit/push
  or unrelated source changes are authorized by this source-only step.
