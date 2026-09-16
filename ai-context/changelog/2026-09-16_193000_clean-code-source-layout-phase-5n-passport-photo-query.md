# 01-261 clean-code/source-layout - Java phase 5n

## Scope
- Extract passport lookup-by-photo-id JDBC/read logic from ObjectPassportService into package-private ObjectPassportPhotoQuery.
- Keep upload/update/delete/download orchestration, transaction boundaries, runtime datasource selection, Connection lifecycle, and ObjectPassportPhotoStorageService I/O in ObjectPassportService.
- Preserve the public ObjectPassportService constructor signature and controller/service contracts.
- Preserve photo lookup semantics: trim photo id, normalize stored photos, return the owning StoredPassportRecord, and raise NOT_FOUND for blank or missing ids.
- Add direct H2 regression coverage and a source-layout ownership contract.
- No deployment.

## Why
- After P5m, ObjectPassportService still contained a coherent JDBC query whose only responsibility was resolving a passport from a photo id.
- Moving that query behind an already-open Connection reduces JDBC/read ownership without moving transaction or storage side effects.

## Checks
- ObjectPassportPhotoQuery is package-private and receives an already-open Connection.
- The query owns SELECT id FROM object_passports, persistence loading, photo normalization, and NOT_FOUND lookup behavior.
- ObjectPassportService delegates both updatePhoto and deletePhoto lookups to the query owner.
- ObjectPassportService still owns openConnection/runtimeObjectsDataSource, setAutoCommit/commit/rollback, store/deleteQuietly/download, and public photo mutation methods.
- Narrow regression selector includes ObjectPassportPhotoQueryTest, photo/payload/persistence/list regressions, runtime datasource, API controller, and Java source-layout contract tests.
- Generated Sass CSS noise is restored after Maven.
- git diff --check passes.

## Non-goals
- No photo mutation workflow extraction.
- No storage mutation ownership changes.
- No datasource or transaction changes.
- No endpoint, payload, schema, NetBox, scheduler, or deployment changes.
