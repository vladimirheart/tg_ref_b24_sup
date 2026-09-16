# 2026-09-16 - clean-code source layout, phase 3z: passport detail activity workspace

## Scope

- baseline: `30daefaac666949b1dd75e26afba85f738f3e9f5`;
- extract the adjacent balanced `cases` and `tasks` read-only panels from `templates/passports/detail.html` into one passport-detail activity fragment;
- keep cases/incidents and tasks together as one operational activity responsibility instead of splitting one panel per file;
- preserve all existing DOM ids, panel data attributes and the current inline browser runtime contract;
- require whitespace-only sibling boundaries between cases/tasks and between the activity workspace and the photos panel;
- compose both panels through a single Thymeleaf entrypoint `activityPanels`;
- leave overview/network/equipment/media markup and the existing inline runtime in `detail.html` for separate architectural review / phase P4;
- require exact structural round-trip validation plus the detail/edit WebMvc smoke tests and focused passport-detail source-contract test.

## Responsibility boundary

- `detail-activity.html`: read-only cases/incidents and tasks panels associated with the current passport.
- `passports/detail.html`: detail workspace shell, sibling read-only panels, media viewer, edit-fragment composition and existing inline runtime pending phase P4.

## Not changed

Browser runtime behavior, controllers/services/API, persistence/data, SCSS, generated CSS, deployment and production services.
