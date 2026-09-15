# 2026-09-15 - clean-code source layout, phase 3p: settings IT workspace

## Scope

- baseline: `e4aa7d76a49bc5c743919207b80694f7517088ba`;
- extract the balanced `itConnectionsModal` parent workspace from `templates/settings/index.html`;
- keep the IT navigation tiles, accordion shell and cohesive remote-access section together as one parent responsibility;
- keep NetBox sync, equipment, connection catalog and provider profiles composed through their existing child fragments;
- leave provider/editor child modals and channels-specific network profile UI outside the parent IT workspace;
- extract by exact div balance so the settings-surface parent closing tag cannot leak into the fragment;
- preserve the existing IT workspace ids, disclosure hooks and runtime DOM hooks;
- normalize only pre-existing whitespace-only lines inside the extracted block so staged `git diff --check` remains clean;
- require exact structural round-trip validation after blank-line normalization and `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `itConnectionsWorkspace`: primary IT settings shell, section navigation, child-fragment composition and remote-access catalogue.

## Not changed

Runtime JS, controllers/services/API, DB/data, NetBox operations, generated CSS, Docker and production services.
