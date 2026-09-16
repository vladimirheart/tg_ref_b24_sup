# 2026-09-16 - clean-code source layout, phase 3x: passport editor equipment workspace

## Scope

- baseline: `68880f112f85d3145eced3cfd5f8848ee762842f`;
- extract the balanced `equipmentSection` from `templates/passports/new.html` into a dedicated passport-editor fragment;
- keep equipment add/manage/render controls together as one cohesive inventory responsibility;
- preserve all existing DOM ids and the current inline equipment runtime contract;
- preserve the parent-owned closing `div` between `equipmentSection` and `</main>` in the page shell;
- compose the fragment through a single Thymeleaf entrypoint `equipmentSection`;
- leave the small `scheduleCard` in the page shell and defer browser-runtime decomposition to phase P4;
- treat this as the final large static extraction from `passports/new.html` in phase P3;
- require exact structural round-trip validation and the existing `/object-passports/new` WebMvc smoke tests.

## Responsibility boundary

- `editor-equipment.html`: equipment inventory host, add control, disabled state, rendered container and empty state.
- `passports/new.html`: editor page shell, compact schedule card, fragment composition points and the existing inline runtime pending phase P4.

## Not changed

Browser runtime behavior, controllers/services/API, persistence/data, SCSS, generated CSS, deployment and production services.
