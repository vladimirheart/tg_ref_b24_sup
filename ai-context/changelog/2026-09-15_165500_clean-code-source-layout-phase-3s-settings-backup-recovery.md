# 2026-09-15 - clean-code source layout, phase 3s: settings backup recovery

## Scope

- baseline: `3bda681353d0bbc2cd0f71cbd56c907007d80522`;
- extract the balanced `backupSettingsModal` from `templates/settings/index.html` into a dedicated backup/recovery fragment;
- keep storage, retention, manual backup, schedule, restore rehearsal and derived-path UI together as one cohesive DR responsibility;
- preserve the overview tile in `settings/index.html` and compose the modal through one wrapperless Thymeleaf entrypoint;
- move backup UI ownership in `ProductionBackupContourSourceContractTest` from the giant settings template to the new fragment without weakening assertions;
- make the source-contract `read()` helper normalize CRLF to LF so existing newline-based contracts are deterministic on Windows and Unix;
- keep the overview-target assertion on `settings/index.html`, where that tile still belongs;
- preserve all backup runtime ids, data hooks, tar.gz contract and panel-lifecycle runner marker;
- extract by exact div balance so the following input-formatting workspace cannot leak into the fragment;
- require exact structural round-trip validation, `/settings` Thymeleaf render smoke-test and the full production backup source-contract test.

## Responsibility boundary

- `backupRecoveryWorkspace`: backup policy, manual runner controls, scheduling, restore rehearsal and actual storage paths.
- `settings/index.html`: backup overview tile only.

## Not changed

Backup runtime behavior, controllers/services/API, backup/restore scripts, DB/data, generated CSS, Docker topology and production services. Test expectations are preserved; only source ownership and EOL normalization change.
