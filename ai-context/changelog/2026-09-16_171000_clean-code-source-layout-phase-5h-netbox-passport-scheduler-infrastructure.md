# 2026-09-16 - clean-code source layout, phase 5h: NetBox passport scheduler infrastructure

## Scope

- baseline: `7fae71b434a3a289eeae613e36517266d39511d9`;
- move `NetBoxObjectPassportSyncScheduler` from generic `com.example.panel.service` into feature-owned `com.example.panel.passports.infrastructure`;
- keep `NetBoxObjectPassportSyncService` and its scheduler in the same feature infrastructure package;
- preserve scheduler body, schedule properties, worker role, leased replica policy and lease key; only package ownership and the now-required `RuntimeCoordinationService` import change;
- update the passport Java source-layout contract and the runtime deployment workload inventory path;
- leave NetBox sync behavior, durable backend commands, passport CRUD, photo model/storage and API endpoints unchanged.

## Verification

- pin scheduler, sync service, runtime coordination, source-layout/doc and regression-test blobs;
- detached sandbox transform before real source mutation;
- compile and run passport sync/source-layout plus runtime boundary regression tests;
- restore only known Maven/Sass generated CSS artifacts before the final diff gate;
- repository scan rejects the retired scheduler import and the runtime inventory rejects the retired scheduler source path;
- `git diff --check`; no stage/commit/push/deploy.
