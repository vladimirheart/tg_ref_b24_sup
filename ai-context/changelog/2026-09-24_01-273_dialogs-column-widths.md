# 01-273 - Dialogs independent per-user column widths

## Change

- Resume from the R7 five-file partial preference/service write after the CRLF-sensitive dialogs.js marker block.
- Use line-ending-neutral source replacement for Windows CRLF compatibility.
- Activate a visible resizer on every Dialogs table header with data-column-key.
- Freeze current visible widths at drag start so changing one column does not rescale neighboring columns.
- Persist widths in server-backed dialogsColumnWidths UI preferences per username.
- Clear stale local fallback when the current authenticated server bootstrap has no width preference.
- Add immediate preference flush with keepalive for durable reload/re-login behavior.
- Keep existing Dialogs column visibility/order behavior and responsive horizontal scrolling.

## Safety

- No DB migration.
- No queue/environment mutation.
- No commit/push before manual acceptance.
## Manual acceptance / closure - 2026-09-24

<!-- 01-273_MANUAL_ACCEPTANCE_GREEN_2026-09-24 -->

- `MANUAL_ACCEPTANCE=GREEN` - explicit manual acceptance was received after the final R8 Dialogs UI review.
- Final scope: independent per-user server-backed Dialogs column widths, persistence across reload/login, and keyboard divider accessibility.
- R8 apply, isolated validation, panel-web-only preview, and runtime asset checks were GREEN before manual acceptance.
- Checkpoint closes `01-273` as GREEN without runtime, DB, queue, or environment mutation.
