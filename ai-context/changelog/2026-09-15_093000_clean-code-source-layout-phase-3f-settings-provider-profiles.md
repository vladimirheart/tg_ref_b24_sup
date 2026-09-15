# 2026-09-15 - clean-code source layout, phase 3f: settings provider profiles template

## Scope

- baseline: `551efcaacbd6b4eb8d189273b15e0f30e92eb083`;
- extract provider profiles and their editor from `templates/settings/index.html`;
- compose them through two Thymeleaf fragment entrypoints;
- leave NetBox, IT connections, remote access, integration routing and equipment/media unchanged;
- require exact round-trip validation and `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `providerProfilesSection`: `networkProfilesSection`;
- `providerProfileModal`: `networkProfileEditorModal`.

## Not changed

Runtime JS, API, DB/data, generated CSS, Docker, NetBox behavior and production services.
