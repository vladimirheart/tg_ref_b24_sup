# 2026-09-15 - clean-code source layout, phase 3k1: channels integration-network boundary repair

## Scope

- baseline: `504c90f2d3c523caa3e44302938c412e3d855c24`;
- repair the P3k Thymeleaf composition boundary without changing rendered settings behavior;
- keep `integrationNetworkWorkspace` responsible only for its own `channelsManageAdvancedAccordion` markup;
- move the two parent closing `div` tags for `channels-manage-shell` and `channels-manage` back to `templates/settings/index.html`;
- preserve the existing integration-network profile modal entrypoint unchanged;
- require exact expanded-composition equivalence and `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- the integration-network fragment must be structurally self-contained and div-balanced;
- the channels parent shell owns and closes its own wrappers.

## Not changed

Runtime JS, controllers/services/API, DB/data, generated CSS, Docker and production services.
