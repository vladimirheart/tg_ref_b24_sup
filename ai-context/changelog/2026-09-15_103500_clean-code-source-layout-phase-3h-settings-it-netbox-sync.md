# 2026-09-15 - clean-code source layout, phase 3h: settings IT NetBox sync template

## Scope

- baseline: `c75026353f185b87d1ecda68777dd213f5dc8627`;
- extract the NetBox synchronization accordion section from `templates/settings/index.html`;
- compose it through one wrapperless Thymeleaf fragment entrypoint inside the existing IT settings accordion;
- preserve all connection, site-selection, progress and status DOM ids used by `settings-netbox-sync-runtime.js`;
- keep the IT parent shell/navigation, equipment, connection catalog, provider profiles, remote access and integration routing unchanged;
- require exact round-trip validation and `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `netBoxSyncSection`: NetBox connection settings, site selection and synchronization status UI.

## Not changed

Runtime JS, controllers/services/API, DB/data, generated CSS, Docker, NetBox behavior and production services.
