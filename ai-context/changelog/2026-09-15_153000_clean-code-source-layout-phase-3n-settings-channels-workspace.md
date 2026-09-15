# 2026-09-15 - clean-code source layout, phase 3n: settings channels workspace

## Scope

- baseline: `aafdc0501b01dc47a5f2fb4b65516d4d2692893a`;
- extract the remaining `channelsModal` parent workspace from `templates/settings/index.html`;
- keep channels navigation, manage-list shell and the small automation tab together as one parent channels responsibility;
- keep integration-network and templates composed through their existing child fragments inside the parent workspace;
- keep add-channel and channel-editor child modals as sibling fragment compositions outside the parent workspace;
- preserve channels shell ids, runtime DOM hooks and modal hierarchy;
- normalize only pre-existing whitespace-only lines inside the extracted block so staged `git diff --check` remains clean;
- require exact structural round-trip validation after blank-line normalization and `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `channelsWorkspace`: primary channels modal shell, bot management overview/list and auto-close automation tab.

## Not changed

Runtime JS, controllers/services/API, DB/data, generated CSS, Docker and production services.
