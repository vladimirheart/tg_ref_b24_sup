# 01-272 S12 — ellipsis full-value access

## Scope

Final hygiene tail after S11 checkpoint.

- Adds one shared hover/focus tooltip runtime for explicitly marked truncated values.
- Covers high-signal dense values that previously had ellipsis without reliable full-value access:
  dialogs client/problem/responsible values, reports staff names, Settings channel/runtime values, and passport workspace/activity values.
- Tooltip text is read from the live DOM at interaction time, so later runtime updates keep the revealed value current.
- Existing Knowledge Notion metadata and location/iiko source rows already expose full values and are not duplicated.
- The icon-control audit found no safe project-wide migration: shared `ui-icon-control` is intentionally 2.45rem while dense local channel/knowledge/passport controls are 1.65–2rem. Replacing them globally would increase geometry/noise, so S12 leaves those scoped controls intact.

## Non-goals

- No backend/API/DB/queue/permission changes.
- No change to table column state, filters, report calculations, runtime actions, or passport data.
- No attempt to reopen the deferred S11 first-paint residuals.

## Verification

- `node --check` for the shared runtime and touched JS sources.
- `UiFinalHygieneSourceContractTest` extends the source contract.
- Relevant existing Settings/Reports/Passport source-contract tests remain green.
- Maven validation runs in an isolated workspace to avoid host `spring-panel/target` locks.
- Panel preview may recreate only `panel-web`; non-panel container identities must remain unchanged.

### S12 manual acceptance - GREEN

- User manually accepted S12 after the validated panel-web preview.
- Truncated dense operational values expose their full live text on hover and keyboard focus without changing row or workspace geometry.
- Manual checks covered Dialogs, Reports staff names, Settings channel/runtime values, and Passport values; compact icon-control geometry remained unchanged.
- S12 validation and preview were GREEN; preview recreated panel-web only and preserved non-panel container identities.
- Overall task 01-272 remains YELLOW pending the final project-wide read-only review and final acceptance.
