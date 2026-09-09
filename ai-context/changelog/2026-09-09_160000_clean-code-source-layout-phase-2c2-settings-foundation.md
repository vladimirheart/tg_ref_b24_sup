# 2026-09-09 - clean-code source layout, phase 2c2: settings foundation SCSS

## Scope

- baseline: `4ee3ca9e8b4bfdcf03118fca65c66faebcbdda58`;
- legacy `settings/_foundation.scss` becomes a small Sass composition layer;
- rules are split into 2 contiguous responsibility partials under `settings/foundation/`;
- source/cascade order is preserved exactly by module load order;
- compiler equivalence is required for `app.css`, `settings.css`, `sidebar.css` and `style.css`;
- generated CSS remains unstaged and is not part of the source refactor.

## Responsibility slices

- equipment catalogue cards, discovery, lifecycle and visual polish;
- equipment photo workspace, viewer, add/edit flow and acceptance polish.

## Not changed

Templates, browser JS, Java runtime behavior, API, DB/schema/data, Docker, NetBox and production services.
