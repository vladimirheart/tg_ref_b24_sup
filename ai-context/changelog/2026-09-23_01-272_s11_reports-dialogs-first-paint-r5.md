# 01-272 S11 R5 - reports and dialogs first-paint parity

## Manual finding

R4 removed the Settings subtitle flash, but Reports still showed static help in the first frame. Dialogs also visibly rendered the selection checkboxes before its saved/default column state was applied.

## Root cause

- Reports relied only on runtime/CSS suppression. A semantic hidden attribute is stronger and independent of stylesheet timing.
- Dialogs already defines the select column as hidden by default in runtime state, but both SSR and client row markup rendered it visible until applyColumnState ran.

## Change

- Server-render the Reports page subtitle as hidden + data-disclosure-pending; the existing shared disclosure runtime reveals it only inside the info panel.
- Render the Dialogs select TH/TD hidden by default in SSR and client templates. Runtime personalization remains authoritative and may reveal the column when explicitly enabled.
- Extend final hygiene source-contract coverage.

## Boundaries

No backend/API/DB/queue/business semantics change. No generated CSS changes are required.

## Deferred residual checkpoint

R5 source was already applied locally when the R4-only checkpoint was attempted. The exact nine-file R5 state is retained and revalidated before commit. The operator explicitly chose to stop iterating on the remaining first-paint/personalization behavior and move on. This checkpoint records the current source as the continuation baseline without claiming that the residual UX is fully eliminated.
