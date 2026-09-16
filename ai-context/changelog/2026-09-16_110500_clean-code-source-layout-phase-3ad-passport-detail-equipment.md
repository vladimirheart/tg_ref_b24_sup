# 2026-09-16 - clean-code source layout, phase 3ad: passport detail equipment workspace

## Scope

- baseline: `0a31340b18d69c0c06fb3da556441473f6b50671`;
- extract the balanced read-only `equipment` panel from `templates/passports/detail.html` into a dedicated passport-detail equipment fragment;
- keep equipment search, edit entrypoint, asset grid and empty state together as one read-only equipment responsibility;
- preserve all existing DOM ids, data hooks and the current inline browser runtime contract;
- compose the panel through the Thymeleaf entrypoint `equipmentPanel`;
- update the source-contract test so `detail.html` owns the equipment composition point while `detail-equipment.html` owns `passport-equipment-grid` markup;
- keep the `passport-asset-card` assertion on `detail.html` because asset-card rendering remains in the inline runtime until phase P4;
- leave the page header/KPIs/tabs, existing overview/network/activity/media/edit fragment composition and inline browser runtime in `detail.html`;
- derive both source and source-contract size guards from their pinned Git blobs instead of manually copied size constants;
- require exact structural round-trip validation plus the detail/edit WebMvc smoke tests and focused passport-detail source-contract test.

## Responsibility boundary

- `detail-equipment.html`: read-only equipment panel shell, search control, edit entrypoint, asset-grid host and empty state.
- `passports/detail.html`: detail page shell, fragment composition and existing inline equipment rendering/runtime pending phase P4.

## Not changed

Browser runtime behavior, controllers/services/API, persistence/data, SCSS, generated CSS, deployment and production services.
