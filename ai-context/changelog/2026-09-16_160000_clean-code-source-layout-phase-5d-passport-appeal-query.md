# 2026-09-16 - clean-code source layout, phase 5d: passport appeal query

## Scope

- baseline: `7ba85dc9276cf9ca03d7da42d61a6dc9d56e50c2`;
- extract the appeal/cases read-model SQL and matching rules from `ObjectPassportService` into feature-owned package-private `ObjectPassportAppealQuery`;
- keep `ObjectPassportService` as the public compatibility facade for list/cases API behavior; no controller or endpoint signature changes;
- preserve the existing `ObjectPassportService` constructor signature: its `JdbcTemplate` parameter now initializes the dedicated query owner;
- delegate passport-list `appeals_count` resolution and cases loading to the extracted query owner;
- leave passport CRUD, runtime datasource selection, equipment discovery and photo/media behavior in their current owners;
- extend the Java source-layout contract so appeal SQL cannot drift back into the core service.

## Verification

- pinned service/source-contract/regression-test blobs and clean-worktree guards;
- exact guarded extraction of the appeal query method block with only three visibility changes required for facade delegation;
- `ObjectPassportAppealMatchingTest`, `ObjectPassportServiceRuntimeDataSourceTest`, `ObjectPassportApiControllerWebMvcTest`, `ObjectPassportJavaSourceLayoutContractTest`, `ObjectPassportsListUiSourceContractTest` and `SettingsItEquipmentServiceTest`;
- restore only known Maven/Sass generated CSS artifacts inside the sandbox before final diff gate;
- `git diff --check`; no stage/commit/push/deploy.
