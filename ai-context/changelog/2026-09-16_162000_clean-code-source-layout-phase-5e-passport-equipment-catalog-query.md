# 2026-09-16 - clean-code source layout, phase 5e: passport equipment catalogue query

## Scope

- baseline: `68ba0f9fd69d76d5f5606857f722bb231b0ba602`;
- extract passport equipment catalogue discovery/projection rules from `ObjectPassportService` into feature-owned package-private `ObjectPassportEquipmentCatalogQuery`;
- keep `ObjectPassportService.listEquipmentCatalogCandidates()` as the public compatibility facade used by settings equipment;
- keep runtime datasource selection and stored-passport loading in `ObjectPassportService`; the extracted query is pure over passport payloads and owns no JDBC connection;
- preserve the existing `ObjectPassportService` constructor signature and all controller/service consumers;
- leave passport CRUD, appeal/cases query ownership, photo/media behavior and NetBox sync behavior unchanged;
- extend the Java source-layout contract so equipment discovery rules cannot drift back into the core service.

## Verification

- pinned service/query/source-contract/regression-test blobs and clean-worktree guards;
- guarded extraction keeps the catalogue aggregation body equivalent while replacing stored-record iteration with payload iteration;
- `ObjectPassportAppealMatchingTest`, `ObjectPassportServiceRuntimeDataSourceTest`, `ObjectPassportApiControllerWebMvcTest`, `ObjectPassportJavaSourceLayoutContractTest` and `SettingsItEquipmentServiceTest`;
- restore only known Maven/Sass generated CSS artifacts inside the sandbox before final diff gate;
- `git diff --check`; no stage/commit/push/deploy.
