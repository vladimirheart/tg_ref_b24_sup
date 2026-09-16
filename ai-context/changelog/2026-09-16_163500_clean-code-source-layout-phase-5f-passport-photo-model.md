# 2026-09-16 - clean-code source layout, phase 5f: passport photo model

## Scope

- baseline: `6d54849da9027ea28bc305d8fcfa2c6ebaba23ca`;
- extract passport photo normalization, legacy-field projection and single-title-photo rules from `ObjectPassportService` into feature-owned package-private `ObjectPassportPhotoModel`;
- keep upload/update/delete/download orchestration, transactions and `ObjectPassportPhotoStorageService` I/O ownership in `ObjectPassportService`;
- inject only the existing photo URL builder into the model so the extracted rules own no JDBC connection or storage mutation;
- preserve the public `ObjectPassportService` constructor signature and all controller/service consumer contracts;
- leave passport CRUD, appeal/cases query, equipment catalogue query, runtime datasource and NetBox sync ownership unchanged;
- add direct photo-model regression coverage and extend the Java source-layout contract.

## Verification

- pinned service/query/source-contract/regression-test blobs and clean-worktree guards;
- direct `ObjectPassportPhotoModelTest` for legacy field normalization, one-title invariant, title URL resolution and mutable-copy isolation;
- `ObjectPassportAppealMatchingTest`, `ObjectPassportServiceRuntimeDataSourceTest`, `ObjectPassportApiControllerWebMvcTest`, `ObjectPassportJavaSourceLayoutContractTest`, `ObjectPassportsListUiSourceContractTest` and `SettingsItEquipmentServiceTest`;
- restore only known Maven/Sass generated CSS artifacts inside the sandbox before final diff gate;
- `git diff --check`; no stage/commit/push/deploy.
