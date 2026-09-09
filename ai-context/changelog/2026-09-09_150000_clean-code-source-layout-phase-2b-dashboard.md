# 2026-09-09 — clean-code source layout, phase 2b: dashboard SCSS

## Scope

- baseline: `22727bb6600868fd889c42c86b6438f296fd4531`;
- legacy `app/_dashboard.scss` becomes a small composition layer;
- rules are split into 9 responsibility partials under `app/dashboard/`;
- each responsibility partial is a standalone Sass module exporting one `styles` mixin;
- eight mixins are included inside the exact original dashboard page selector;
- the neutral-kpi mixin is included at top level after that selector, preserving the original order;
- module composition uses `@use` + namespaced `@include`, adding no new Sass `@import` debt;
- compiler equivalence is required for app.css, settings.css, sidebar.css and style.css.

## Responsibility slices

- `shell-filters`;
- `metrics`;
- `panel-layout`;
- `heatmap`;
- `team`;
- `notifications-responsive`;
- `visual-layer`;
- `subworkspaces`;
- `neutral-kpi`;

## Not changed

Templates, browser JS, Java runtime behavior, API, DB/schema/data, Docker, NetBox and production services.
