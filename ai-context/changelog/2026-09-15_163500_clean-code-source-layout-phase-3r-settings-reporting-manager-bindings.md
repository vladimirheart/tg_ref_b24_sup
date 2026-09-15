# 2026-09-15 - clean-code source layout, phase 3r: settings reporting and manager bindings

## Scope

- baseline: `35536202be06b621051a3e239fbf377ac7c3fa99`;
- extract the balanced `reportingModal` and `managerBindingsModal` from `templates/settings/index.html`;
- keep both task modals in one fragment because they share `settings-reporting-manager-bindings.js` state, lifecycle and persistence runtime;
- expose two wrapperless Thymeleaf entrypoints instead of creating one file per modal;
- preserve reporting form hooks, manager-binding datalists/table hooks and existing page-shell lifecycle ids;
- leave overview summary hooks in the settings tiles and keep channels/storage/other settings workspaces outside this fragment;
- extract both modals by exact div balance so adjacent workspaces cannot leak into the fragment;
- normalize only pre-existing whitespace-only lines inside the extracted blocks so staged `git diff --check` remains clean;
- require exact structural round-trip validation after blank-line normalization and `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `reportingWorkspace`: auto-report configuration and delivery targets.
- `managerBindingsWorkspace`: location-to-manager/supervisor assignment periods.

## Not changed

Runtime JS, controllers/services/API, DB/data, reporting behavior, manager-binding behavior, generated CSS, Docker and production services.
