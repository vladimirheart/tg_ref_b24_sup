# 2026-09-15 - clean-code source layout, phase 3t: passport editor basic info

## Scope

- baseline: `a3d4633743533580dea6f30890e91fc15b43bce6`;
- extract the balanced `basicInfoCard` from `templates/passports/new.html` into a dedicated passport-editor fragment;
- keep identity/location parameters, lifecycle status/history and the compact IT block together as one cohesive editor responsibility;
- preserve all existing DOM ids and the current inline runtime contract;
- leave the parent-owned closing div between `basicInfoCard` and `networkCard` in the page shell;
- compose the fragment through a single Thymeleaf entrypoint `basicInfoCard`;
- require exact structural round-trip validation and the existing `/object-passports/new` WebMvc smoke tests.

## Responsibility boundary

- `basicInfoCard`: object identity, location fields, lifecycle/status fields and the compact IT summary block.
- `passports/new.html`: editor page shell, sibling workspaces and the existing inline runtime pending phase P4.

## Not changed

Browser runtime behavior, controllers/services/API, persistence/data, SCSS, generated CSS, deployment and production services.
