# 2026-09-16 - clean-code source layout, phase 5g: NetBox passport sync infrastructure

## Scope

- baseline: `7fbd8c19d5abea74b4077e30c06d7b9702924a64`;
- move the complete `NetBoxObjectPassportSyncService` from generic `com.example.panel.service` into feature-owned `com.example.panel.passports.infrastructure`;
- preserve the complete sync class body; only package ownership and required imports change;
- rewire the four production consumers: settings page data, settings NetBox API controller, scheduler and backend-ops dispatcher;
- move package-private sync/merge regression tests with the class instead of widening production visibility;
- update backend-ops/runtime source contracts and the passport Java source-layout contract to the new path;
- synchronize the pre-existing manual-executor inventory contract with baseline reality: dispatcher, backend-ops heartbeat context and bot auto-start already own executors before P5g;
- leave NetBox API/settings services, passport CRUD, photo storage, durable command semantics, scheduler workload semantics and endpoint contracts unchanged.

## Verification

- pinned moved source/test blobs plus all rewired consumer/source-contract blobs and the pre-existing executor owners used by the inventory contract;
- exact baseline executor inventory scan proves seven existing `Executors.new*` owners before the move and the identical owner set after the move except for the NetBox source path;
- repository-wide Java scan rejects the retired sync-service import and retired source path;
- package-private sync/merge tests remain package-local after the move;
- `NetBoxObjectPassportSyncServiceTest`, `NetBoxObjectPassportSyncGuardTest`, `SettingsNetBoxSyncControllerWebMvcTest`, `ObjectPassportJavaSourceLayoutContractTest`, `BackendOpsCommandBoundarySourceContractTest` and `RuntimeLifecycleBoundarySourceContractTest`;
- restore only known Maven/Sass generated CSS artifacts inside the sandbox before final diff gate;
- `git diff --check`; no stage/commit/push/deploy.
