# 2026-09-23 — 01-272 S11 project-wide header hygiene

## Scope

- shared page-header title-row normalization;
- preserve static help behind the existing info disclosure;
- keep dynamic identity/context subtitles visible;
- no backend/API/DB/schema changes.

## Audit findings

- `content-disclosure.js` is global through the navbar and currently processes every `.page-header-card .page-subtitle` unless explicitly skipped;
- most static subtitles therefore already belong behind the shared info affordance;
- `clients/profile.html` uses page-subtitle for the current client ID and `passports/new.html` uses it for the current object title, so those values must opt out;
- page kickers and titles are structurally inconsistent even though the shared runtime already owns header disclosure composition.

## Implementation

- add one shared `ensurePageHeaderTitleRow(...)` helper and run it for page headers before disclosure processing;
- move an existing kicker into the same title row instead of adding per-page wrappers;
- keep title-row margins compact in shared core SCSS;
- mark the two dynamic subtitle contexts with `data-no-disclosure`;
- add a source contract for the shared behavior.

## Validation

- fresh Maven compile and focused source-contract tests;
- generated app.css retained, unrelated generated CSS restored;
- panel-web-only preview with non-panel identity guard;
- manual review required before checkpoint.

## R2 — pre-paint header parity

- Manual R1 preview found a visible first-paint layout shift: kicker initially rendered above the page title and moved inline only after content-disclosure boot.
- Added a CSS-first pre-paint parity rule for the standard direct kicker/title structure; the selector naturally stops matching after runtime creates the shared title row.
- Static help remains second-line before disclosure boot, client ID keeps its inline identity treatment, and no backend/runtime action semantics change.
- Added a source-contract assertion for the no-shift pre-paint rule.

## R3 - first-paint disclosure and dialogs preference hydration

- Prevent static page-header subtitle help from flashing before shared info disclosure initializes.
- Add a synchronous dialogs prepaint runtime that reads the existing list-only storage key before body paint.
- Keep the root prepaint marker synchronized with DialogsShellRuntime so later user toggles work normally.
- Extend the S11 source contract; no backend/API/DB behavior changes.

## Manual acceptance - GREEN

- S11 accepted after R3 panel-web preview.
- Hard reload no longer exposes static page-header help before the shared info disclosure is ready.
- Kicker/title first-paint geometry matches the final hydrated header without a visible jump.
- Client ID and passport object title remain permanently visible as dynamic context.
- Dialogs list-only preference is applied before first body paint and remains synchronized with the existing runtime toggle.
- Checkpoint closes S11 only; overall `01-272` remains `YELLOW` pending the final hygiene tail audit.
