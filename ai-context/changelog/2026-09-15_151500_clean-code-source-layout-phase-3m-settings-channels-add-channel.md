# 2026-09-15 - clean-code source layout, phase 3m: settings channels add-channel

## Scope

- baseline: `23bed25eba6c90eafd8baf501a181105b6470c5b`;
- extract `addChannelModal` from `templates/settings/index.html`;
- keep Telegram, VK and MAX onboarding fields together as one add-channel responsibility;
- compose the modal through one wrapperless Thymeleaf fragment entrypoint;
- preserve the existing `channelsModal -> addChannelModal` page-shell parent/child contract and channels catalog DOM hooks;
- keep channels management, integration network, templates, automation and channel editor outside this fragment;
- normalize only pre-existing whitespace-only lines inside the extracted block so staged `git diff --check` remains clean;
- require exact structural round-trip validation after blank-line normalization and `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `addChannelWorkspace`: channel onboarding form for Telegram, VK and MAX.

## Not changed

Runtime JS, controllers/services/API, DB/data, generated CSS, Docker and production services.
