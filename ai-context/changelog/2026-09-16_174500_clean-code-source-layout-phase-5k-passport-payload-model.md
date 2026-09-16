# 2026-09-16 - clean-code source layout, phase 5k: passport payload model

## Scope

- baseline: `00e49253e31cb1351cf6e9d1436341c67042ea5f`;
- extract object-passport payload merge/default-list normalization, department validation, object/passport naming and deleted-status normalization from `ObjectPassportService` into package-private `ObjectPassportPayloadModel`;
- keep the public `ObjectPassportService` constructor and facade methods unchanged; instantiate the payload model internally beside the existing photo model;
- keep persistence SQL, transactions, runtime datasource resolution, photo storage/orchestration, appeal query and equipment catalogue ownership in their current owners;
- preserve photo normalization by delegating payload photo normalization to the existing `ObjectPassportPhotoModel`.

## Verification

- pin service, photo model, source-layout contract and service regression-test blobs;
- detached sandbox transform before real source mutation;
- direct `ObjectPassportPayloadModelTest` covers merge/list defaults, photo normalization, required department, naming and deleted-status rules;
- run photo model, runtime datasource, appeal matching and Java source-layout regression tests;
- restore only known Maven/Sass generated CSS artifacts before the final diff gate;
- `git diff --check`; no stage/commit/push/deploy.
