# 2026-09-16 - clean-code source layout, phase 3ac: passport detail network workspace

## Scope

- baseline: `db0a64a1157462ec0de2976ef5165bd39aadc92a`;
- extract the balanced read-only `network` panel from `templates/passports/detail.html` into a dedicated passport-detail network fragment;
- keep internal network, provider, technical connection parameters and network attachments together as one read-only network responsibility;
- preserve all existing DOM ids, panel data attributes and the current inline browser runtime contract;
- require a whitespace-only boundary between the network panel and the equipment panel;
- compose the panel through the Thymeleaf entrypoint `networkPanel`;
- leave the page header/KPIs/tabs plus overview/equipment and existing activity/media/edit fragment composition in `detail.html`;
- leave the existing inline browser runtime in `detail.html` for phase P4;
- derive the source byte-size guard directly from the pinned Git blob instead of a manually copied size constant;
- require exact structural round-trip validation plus the detail/edit WebMvc smoke tests and focused passport-detail source-contract test.

## Responsibility boundary

- `detail-network.html`: read-only network panel for internal network, provider, technical connection parameters and network attachments.
- `passports/detail.html`: detail workspace shell, sibling read-only panels, fragment composition and existing inline runtime pending phase P4.

## Not changed

Browser runtime behavior, controllers/services/API, persistence/data, SCSS, generated CSS, deployment and production services.
