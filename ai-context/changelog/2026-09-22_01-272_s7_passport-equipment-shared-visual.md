# 2026-09-22 — task 01-272 — passport equipment shared visual S7

## Scope

- continue from checkpoint `a438b76c6205afb95891faaeae1e393b87fd2313`;
- unify equipment media parsing/cover selection between object passport and Settings equipment catalogue;
- align passport equipment visual area with the existing catalogue image grammar;
- keep backend/API/DB/schema unchanged;
- validate with targeted Maven and `panel-web`-only preview before commit/push.

## Implementation

- add reusable `equipment-media-runtime.js` for structured/legacy `photo_url` parsing and title/first cover selection;
- make `passport-detail-core-runtime.js` and `settings-it-equipment-runtime.js` consume the shared resolver;
- remove passport-local cover-selection duplication from `passport-detail-equipment-runtime.js`;
- use neutral image placeholder, type overlay and `object-fit: contain` in passport equipment cards;
- add source-contract coverage for shared runtime loading and visual markers.

## Corrective R5 - passport equipment editor

- Fix detail-editor CSRF header for CookieCsrfTokenRepository: X-XSRF-TOKEN instead of X-CSRF-TOKEN.
- Reuse the same exact type/vendor/model catalog identity contract for legacy instances that lack catalog_id; persist only unique inferred matches.
- Compact the equipment edit list into summary-first cards with quick instance facts, catalog state, icon archive/restore action and collapsed detailed fields.
- No DB/schema/queue mutation.

## Corrective R8 - catalogue thumbnails in equipment editor

- Added catalogue cover thumbnail to the compact equipment-instance summary card in passport edit mode.
- Reused the shared equipment media resolver via `equipmentCover`; no independent photo selection logic was introduced.
- Added neutral placeholder/fallback and responsive layout rules.
- Bumped detail editor/app asset versions and extended source-contract coverage.
- No backend, API, DB, queue or environment changes.

## Manual acceptance - S7 GREEN

- User accepted the R8 preview visually and functionally.
- Passport equipment save no longer fails on CSRF, legacy exact matches can be persisted as catalog links, and catalogue photos render in compact editor cards.
- S7 is ready for checkpoint commit/push; overall task 01-272 remains in progress.
