# 2026-09-15 - clean-code source layout, phase 3q: settings storage inventory

## Scope

- baseline: `8623f851ea0e6f7e3de1b2f585f67ab8d6ad85e5`;
- extract the balanced `storageInventoryModal` from `templates/settings/index.html`;
- keep storage-root, SQLite, attachment-reference, risk and raw-report UI together as one inventory-runner responsibility;
- compose the modal through one wrapperless Thymeleaf fragment entrypoint;
- preserve the `canRunStorageInventory` visibility guard, endpoint and admin-shell runtime DOM hooks;
- keep reporting, manager bindings and channels workspaces outside this fragment;
- extract by exact div balance so adjacent task modals cannot leak into the fragment;
- normalize only pre-existing whitespace-only lines inside the extracted block so staged `git diff --check` remains clean;
- require exact structural round-trip validation after blank-line normalization and `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `storageInventoryWorkspace`: privileged storage inventory runner, summary, risks and raw-report presentation.

## Not changed

Runtime JS, controllers/services/API, storage scan behavior/data, generated CSS, Docker and production services.
