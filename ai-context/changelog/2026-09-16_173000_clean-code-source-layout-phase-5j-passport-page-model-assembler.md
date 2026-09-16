# 2026-09-16 - clean-code source layout, phase 5j: passport page model assembler

## Scope

- baseline: `c633e651d74a9a2ab5e0cbf8786863d256da6e28`;
- extract object-passport editor model assembly from `ObjectPassportPageController` into `ObjectPassportPageModelAssembler` in the feature API package;
- keep the controller responsible for HTTP routes, navigation enrichment, list orchestration and template selection only;
- preserve editor model attributes, effective-location fallback, equipment catalogue projection, status defaults and parameter normalization rules;
- keep endpoint paths, template names, passport CRUD, NetBox infrastructure, photo ownership, database and deployment behavior unchanged.

## Verification

- pin the controller plus all modified WebMvc/source-contract blobs;
- deterministic extraction keeps the original model-assembly method block and default-status constant, changing only the assembler entry visibility;
- direct `ObjectPassportPageModelAssemblerTest` covers effective locations, status defaults and existing model attributes;
- run ManagementControllerWebMvcTest plus passport list/workspace/source-layout contracts;
- restore only known Maven/Sass generated CSS artifacts before the final diff gate;
- `git diff --check`; no stage/commit/push/deploy.
