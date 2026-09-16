# 2026-09-16 - clean-code source layout, phase 3y: passport detail edit workspace

## Scope

- baseline: `bb9b9f337d88b8c3941a118f5361feb28e452c76`;
- extract the balanced `passportEditLayer` from `templates/passports/detail.html` into a dedicated passport-detail edit fragment;
- keep editor tabs, generated field hosts, schedule/equipment/photo controls and save/status footer together as one edit-workspace responsibility;
- preserve all existing DOM ids and the current inline browser runtime contract;
- preserve the whitespace-only boundary between `passportEditLayer` and `passportWorkspaceError` in the page shell;
- compose the fragment through a single Thymeleaf entrypoint `passportEditLayer`;
- update the source-contract test so the page owns the composition point and the fragment owns the edit-layer markup;
- leave read-only overview/network/equipment/activity/photo panels and the photo viewer in `detail.html` for separate architectural review;
- require exact structural round-trip validation plus the detail/edit WebMvc smoke tests and the focused source-contract test.

## Responsibility boundary

- `detail-edit.html`: passport detail edit drawer, tab hosts, equipment/photo controls and save/status actions.
- `passports/detail.html`: read-only workspace shell, photo viewer, error host, fragment composition point and existing inline runtime pending phase P4.

## Not changed

Browser runtime behavior, controllers/services/API, persistence/data, SCSS, generated CSS, deployment and production services.
