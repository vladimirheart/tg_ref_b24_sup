# 2026-09-16 - clean-code source layout, phase 4k: passport editor page runtime

## Scope

- baseline: `a1b3d74ecacb05c65a3f13eae6636c31210d8c65`;
- extract the complete remaining inline runtime from `passports/new.html` into `static/js/passport-editor-page-runtime.js`;
- keep only Thymeleaf bootstrap values and a narrow `PassportEditorPageRuntime.mount(...).boot()` bridge inline;
- retain `PassportEditorEquipmentRuntime` as an independent equipment feature dependency;
- move location parameters, searchable selects, network profiles, schedule, dirty tracking, passport save/load, cases/tasks/status history, passport media, network files and page event wiring together as the page lifecycle owner;
- add a source contract for the creation-editor runtime boundary;
- require exact whole-inline-script round-trip, generated runtime `node --check`, full `ManagementControllerWebMvcTest` plus full `ObjectPassportWorkspaceUiSourceContractTest`, generated CSS restore after Maven and `git diff --check`.

## Not changed

Controller/service/API contracts, persistence/data, endpoint behavior, fragment markup, SCSS/generated CSS, deployment and production services.
