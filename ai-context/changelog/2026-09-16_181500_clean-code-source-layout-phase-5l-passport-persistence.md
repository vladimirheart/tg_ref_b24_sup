# 2026-09-16 - clean-code source layout, phase 5l: passport persistence gateway

## Scope

- baseline: `4a3006ce2bbc9227e7b510597d40e5bb955196b4`;
- extract object-passport row insert/update/load helpers and JSON persistence mapping from `ObjectPassportService` into package-private `ObjectPassportPersistence`;
- keep transaction boundaries, runtime datasource selection and connection ownership in `ObjectPassportService`; the persistence owner receives an already-open `Connection`;
- keep list projection, replace-all orchestration, photo orchestration, payload/photo models, appeal query and equipment catalogue behavior unchanged;
- keep the public `ObjectPassportService` constructor signature unchanged and construct the persistence collaborator internally.

## Verification

- pin service, payload/photo models, source-layout contract and existing service regression-test blobs;
- detached sandbox transform before real source mutation;
- direct `ObjectPassportPersistenceTest` covers generated-key inserts, row loading, object/passport updates and invalid JSON fallback;
- synchronize the existing P5k source-layout contract so object/passport naming calls are asserted in the persistence owner rather than the facade;
- run payload/photo/manual-override, runtime datasource, appeal matching and Java source-layout regression tests;
- restore only known Maven/Sass generated CSS artifacts before the final diff gate;
- `git diff --check`; no stage/commit/push/deploy.
