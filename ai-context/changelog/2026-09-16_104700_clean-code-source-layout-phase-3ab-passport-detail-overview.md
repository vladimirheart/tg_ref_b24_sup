# 2026-09-16 - clean-code source layout, phase 3ab: passport detail overview workspace

## Scope

- baseline: `75b9f7b706437452769bade5c8a81432bf47d60a`;
- extract the balanced read-only `overview` panel from `templates/passports/detail.html` into a dedicated passport-detail overview fragment;
- keep object identity properties, lifecycle/status history, contacts, schedule and quality together as one read-only overview responsibility;
- preserve all existing DOM ids, panel data attributes and the current inline browser runtime contract;
- require a whitespace-only boundary between the overview panel and the network panel;
- compose the panel through the Thymeleaf entrypoint `overviewPanel`;
- leave the page header/KPIs/tabs plus network/equipment and existing activity/media/edit fragment composition in `detail.html`;
- leave the existing inline browser runtime in `detail.html` for phase P4;
- derive the source byte-size guard directly from the pinned Git blob instead of a manually copied size constant;
- require exact structural round-trip validation plus the detail/edit WebMvc smoke tests and focused passport-detail source-contract test.

## Responsibility boundary

- `detail-overview.html`: read-only overview panel for core properties, lifecycle, contacts, schedule and data quality.
- `passports/detail.html`: detail workspace shell, sibling read-only panels, fragment composition and existing inline runtime pending phase P4.

## Not changed

Browser runtime behavior, controllers/services/API, persistence/data, SCSS, generated CSS, deployment and production services.
