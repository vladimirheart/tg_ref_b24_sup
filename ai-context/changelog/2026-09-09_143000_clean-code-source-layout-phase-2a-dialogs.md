# 2026-09-09 — clean-code source layout, phase 2a: dialogs SCSS

## Scope

- baseline: `f67393518f508191d5a672bfa835e40314bb08d0`;
- legacy `app/_dialogs.scss` becomes a compatibility aggregator;
- rules are split into 14 contiguous responsibility partials under `app/dialogs/`;
- original cascade order is preserved;
- compiler equivalence is required for app.css, settings.css, sidebar.css and style.css.

## Responsibility slices

- `history-media`;
- `composer`;
- `list-base`;
- `list-layout`;
- `workspace`;
- `client-profile`;
- `template-layout`;
- `personalization`;
- `composer-ux`;
- `workspace-composer`;
- `media`;
- `message-actions`;
- `details-controls`;
- `header-density`;

## Not changed

Templates, browser JS, Java runtime behavior, API, DB/schema/data, Docker, NetBox and production services.
