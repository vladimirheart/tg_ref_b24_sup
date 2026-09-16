# 2026-09-16 - clean-code source layout, phase 5i: passport page API package

## Scope

- baseline: `30de335f15f79a81ac8e2d277d09292264496506`;
- move `ObjectPassportPageController` from the feature root into `com.example.panel.passports.api`;
- preserve the complete MVC controller body, routes, templates and model assembly; only package ownership and the required `ObjectPassportService` import change;
- rewire the management WebMvc test and passport UI/source-layout contracts to the new source path;
- leave `ObjectPassportApiController` in the generic controller package because its shared request-payload helper boundary is a separate concern;
- leave passport CRUD, NetBox infrastructure, photo model/storage, database and deployment behavior unchanged.

## Verification

- pin the page controller, service, management controller and all rewired test/source-contract blobs;
- detached sandbox transform before real source mutation;
- run ManagementControllerWebMvcTest plus passport list/workspace/source-layout contracts;
- repository Java scan rejects the retired page-controller import;
- restore only known Maven/Sass generated CSS artifacts before the final diff gate;
- `git diff --check`; no stage/commit/push/deploy.
