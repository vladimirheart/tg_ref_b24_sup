# 01-262 clean-code/source-layout - Java phase 5o

## Scope
- Extract NetBox site-id passport lookup/read projection from ObjectPassportService into package-private ObjectPassportNetBoxQuery.
- Keep the public findPassportByNetBoxSiteId/upsertPassportByNetBoxSiteId facade, blank-site-id short-circuit, runtime datasource selection, and Connection lifecycle in ObjectPassportService.
- Preserve the public ObjectPassportService constructor signature and NetBox sync consumer contract.
- Preserve blank site-id semantics: return null before opening a database connection.
- Add direct H2 regression coverage and a source-layout ownership contract.
- No deployment.

## Why
- After P5n, ObjectPassportService still owned a coherent read-side scan that matched stored payloads by netbox_site_id and normalized the matching passport.
- Moving the scan/projection behind an already-open Connection reduces read ownership without moving datasource, transaction, sync, or mutation orchestration.

## Checks
- ObjectPassportNetBoxQuery is package-private and receives an already-open Connection.
- The query owns site-id normalization, netbox_site_id matching, persistence scanning, and normalized passport projection.
- ObjectPassportService preserves the blank-id pre-check before openConnection and delegates the actual lookup to the query owner.
- upsertPassportByNetBoxSiteId remains in ObjectPassportService and continues deciding create versus external-source update.
- Runtime datasource and Connection ownership stay in ObjectPassportService.
- NetBoxObjectPassportSyncServiceTest remains in the targeted selector.
- Generated Sass CSS noise is restored after Maven.
- git diff --check passes.

## Non-goals
- No NetBox sync orchestration changes.
- No passport mutation or transaction extraction.
- No photo workflow/storage ownership changes.
- No endpoint, schema, scheduler, datasource, or deployment changes.
