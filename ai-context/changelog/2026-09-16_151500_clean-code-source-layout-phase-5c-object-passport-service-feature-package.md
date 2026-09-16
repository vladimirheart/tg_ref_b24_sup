# 2026-09-16 - clean-code source layout, phase 5c: object passport service feature package

## Scope

- baseline: `52aafe1735d79928239b732f100cf92a01aadf3e`;
- move the complete `ObjectPassportService` owner from the generic `com.example.panel.service` package into feature-owned `com.example.panel.passports`;
- preserve the complete service body exactly after LF normalization; only the package declaration changes;
- update all direct Java consumers and old-package test consumers to the feature-owned service package; the page controller uses same-package ownership without a redundant import;
- keep `ObjectPassportApiController` in the shared controller package for this phase because it still depends on package-private shared `RequestPayloadUtils`;
- keep `ObjectPassportPhotoStorageService` in infrastructure-owned `storage`; no persistence/media behavior is split or duplicated;
- keep package-private manual-override coverage without widening production visibility by moving that single guard into the passports test package while leaving the NetBox merge guard in service;
- add a Java source-layout contract that requires the new service path and forbids the old path/import;
- repair the existing passports-list source contract so it reads the feature-owned service/page controller and the actual SCSS partial owners instead of retired aggregate owners;
- no endpoint, template, database, storage, API payload or deployment behavior changes.

## Verification

- pinned service/controller/test blobs and clean-worktree guards;
- exact service-body preservation across package move;
- repository-wide Java source/test scan rejects live imports of the retired service FQCN;
- `ObjectPassportApiControllerWebMvcTest`, `ManagementControllerWebMvcTest`, both sync/manual guard tests, all old-package ObjectPassportService consumer tests, the new Java source-layout contract and the existing passports-list source contract;
- restore only known Maven/Sass generated CSS artifacts inside the sandbox before final diff gate;
- `git diff --check`; no stage/commit/push/deploy.
