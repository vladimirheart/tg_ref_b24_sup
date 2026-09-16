# 2026-09-16 - clean-code source layout, phase 3v: passport editor activity workspace

## Scope

- baseline: `047f12430087b58279b5237e0184d0ae923275b1`;
- extract the adjacent balanced `casesCard` and `tasksCard` blocks from `templates/passports/new.html` into one passport-editor activity fragment;
- keep location cases and location tasks together as one operational activity responsibility instead of creating one fragment per card;
- preserve all existing DOM ids and the current inline runtime contract;
- require whitespace-only sibling boundaries between cases/tasks and between the activity workspace and `photosSection`;
- compose both cards through a single Thymeleaf entrypoint `activityWorkspace`;
- leave the small `scheduleCard` in the page shell and defer browser-runtime decomposition to phase P4;
- require exact structural round-trip validation and the existing `/object-passports/new` WebMvc smoke tests.

## Responsibility boundary

- `activityWorkspace`: Bitrix cases and tasks associated with the current location, including refresh controls, tables and empty states.
- `passports/new.html`: editor page shell, schedule/media/equipment sibling workspaces and the existing inline runtime pending phase P4.

## Not changed

Browser runtime behavior, controllers/services/API, persistence/data, SCSS, generated CSS, deployment and production services.
