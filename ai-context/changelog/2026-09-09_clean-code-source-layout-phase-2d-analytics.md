## 2026-09-09 — clean-code source layout, phase 2d: analytics SCSS

### Scope

- baseline: `0f67d30eaff667fcbd6f8ed4ac989e041b2cc643`;
- `app/_analytics.scss` becomes a small Sass composition layer;
- rules are split into 4 responsibility partials under `app/analytics/`;
- source/cascade order is preserved exactly through ordered `@use` modules;
- compiler equivalence is required for app.css, settings.css, sidebar.css and style.css.

### Responsibility slices

- workspace: main analytics shell, controls, semantic badges, filters and shared section/table primitives;
- telemetry: telemetry/monitoring cards, runtime alert semantics and responsive treatment for the main analytics page;
- monitoring: child monitoring pages, overview controls, progress and health-state cells;
- monitoring-diagnostics: diagnostics/runtime indicators, iiko-specific fields and child-page table/mobile treatment.

### Reviewed and intentionally unchanged

- `settings/_workspace.scss` was reviewed before this phase and kept intact: its base workspace primitive and modal-specific adaptations remain cohesive around one settings-workspace pattern and are below the architecture review threshold.

### Not changed

Templates, browser JS, Java runtime behavior, API, DB/schema/data, Docker, NetBox, generated CSS and production services.
