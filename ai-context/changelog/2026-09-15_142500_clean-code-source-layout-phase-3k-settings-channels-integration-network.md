# 2026-09-15 - clean-code source layout, phase 3k: settings channels integration network template

## Scope

- baseline: `da1ab638a3efa9074bc4b272be9a8167761e5a73`;
- extract the channels integration-network workspace from `templates/settings/index.html`;
- keep reusable proxy/VPN profiles and project/bot network routes together as one integration-network responsibility;
- move the related `integrationNetworkProfileEditorModal` into the same fragment through a second wrapperless Thymeleaf entrypoint;
- preserve the existing `channelsModal -> integrationNetworkProfileEditorModal` page-shell parent/child contract and integration-network DOM hooks;
- keep the channels quick cards/table, channel templates, automation, add-channel UI and channel editor outside this fragment;
- normalize only pre-existing whitespace-only lines inside the extracted ranges so staged `git diff --check` remains clean;
- require exact two-point structural round-trip validation after blank-line normalization and `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `integrationNetworkWorkspace`: reusable network profiles plus project/bot route configuration inside channels management.
- `integrationNetworkProfileModal`: editor for one reusable integration-network profile.

## Not changed

Runtime JS, controllers/services/API, DB/data, generated CSS, Docker and production services.
