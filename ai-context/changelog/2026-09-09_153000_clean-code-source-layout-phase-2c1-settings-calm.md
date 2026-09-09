# 2026-09-09 — clean-code source layout, phase 2c1: settings calm SCSS

## Scope

- baseline: `27f3b574b24dc9b49da6f43de3d0f9cd34b6dc47`;
- legacy `settings/_calm.scss` becomes a small Sass composition layer;
- rules are split into 9 contiguous responsibility partials under `settings/calm/`;
- source/cascade order is preserved exactly by module load order;
- compiler equivalence is required for `app.css`, `settings.css`, `sidebar.css` and `style.css`;
- generated CSS remains unstaged and is not part of the source refactor.

## Responsibility slices

- modal and design workspace;
- overview surfaces;
- modal hierarchy and tab content;
- shared modal rhythm;
- channels and channel editor;
- dialog templates and partner parameter editors;
- users and access;
- IT / locations infrastructure settings;
- production readiness.

## Not changed

Templates, browser JS, Java runtime behavior, API, DB/schema/data, Docker, NetBox and production services.
