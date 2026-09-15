# 2026-09-15 - clean-code source layout, phase 3g: settings IT connection catalog template

## Scope

- baseline: `d2aa97389bf6e0a84d472d3cbdd7b479b79f07f7`;
- extract the IT connection category catalog and its add modal from `templates/settings/index.html`;
- compose them through two Thymeleaf fragment entrypoints;
- keep NetBox sync, remote access, provider profiles, integration routing and equipment/media unchanged;
- require exact round-trip validation and `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `connectionCatalogSection`: `itConnectionsSection`;
- `connectionCatalogModal`: `itConnectionAddModal`.

## Not changed

Runtime JS, controllers/services/API, DB/data, generated CSS, Docker, NetBox behavior and production services.
