# 01-260 clean-code/source-layout - Java phase 5m

## Scope
- Extract object-passport list SQL/read-model projection from ObjectPassportService into package-private ObjectPassportListQuery.
- Keep appeal-count context loading in ObjectPassportService before opening the runtime objects connection.
- Keep runtime datasource selection, connection ownership, transaction boundaries, public APIs, and list field semantics unchanged.
- Add an H2 regression test for list projection/fallbacks, deleted status, title photo, appeals count, and normalized photos.
- Synchronize source-layout ownership contracts with the new list-query owner.
- No deployment.

## Why
- ObjectPassportService still owned a coherent SQL/read-model responsibility after P5l.
- Moving that responsibility behind an already-open Connection keeps runtime datasource compatibility in the facade while reducing mixed I/O/projection ownership.

## Checks
- ObjectPassportListQuery is package-private and receives an already-open Connection.
- ObjectPassportService still loads appeal-count context and opens the runtime objects connection.
- List SQL, payload read, projection/fallbacks, deleted flag, title photo URL, appeals count, and photos field moved together.
- ObjectPassportJavaSourceLayoutContractTest rewired stale ownership assertions.
- Narrow regression selector: ObjectPassportListQueryTest,ObjectPassportPersistenceTest,ObjectPassportPayloadModelTest,ObjectPassportPhotoModelTest,ObjectPassportManualOverrideGuardTest,ObjectPassportServiceRuntimeDataSourceTest,ObjectPassportAppealMatchingTest,ObjectPassportJavaSourceLayoutContractTest.
- Generated Sass CSS noise is restored after Maven.
- git diff --check passes.

## Non-goals
- No datasource selection changes.
- No transaction-boundary changes.
- No photo storage/orchestration changes.
- No endpoint, payload, schema, NetBox, scheduler, or deployment changes.
