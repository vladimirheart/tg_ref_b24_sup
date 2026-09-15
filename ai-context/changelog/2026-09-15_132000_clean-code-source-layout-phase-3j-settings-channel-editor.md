# 2026-09-15 - clean-code source layout, phase 3j: settings channel editor template

## Scope

- baseline: `d6fe7893d79de8fcf560eb02dc146dc8662b4958`;
- extract the channel editor workspace from `templates/settings/index.html`;
- keep the main channel editor and its VK webhook child together as one bounded channel-editor responsibility;
- compose both modals through one wrapperless Thymeleaf fragment entrypoint;
- preserve the existing `channelsModal -> channelEditorModal -> vkWebhookModal` page-shell parent/child contract and channel editor DOM hooks;
- keep `channelsModal`, `addChannelModal`, bot template editors and integration-network profile editor outside this fragment;
- normalize only pre-existing whitespace-only lines inside the extracted block so staged `git diff --check` remains clean;
- require exact structural round-trip validation after that blank-line normalization and `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `channelEditorWorkspace`: channel configuration/editor UI plus the VK webhook child editor.

## Not changed

Runtime JS, controllers/services/API, DB/data, generated CSS, Docker and production services.
