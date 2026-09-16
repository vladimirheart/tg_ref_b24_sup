# 2026-09-16 - clean-code source layout, phase 5b: settings page controller

## Scope

- baseline: `471e129889a0a7197b825afaf215420a19a6ee10`;
- move the complete settings MVC page feature from `ManagementController` into feature-owned `com.example.panel.settings.SettingsPageController`;
- move the `/settings` route, page bootstrap/model assembly, macro publish permission helpers and legacy question-template audit helper as one cohesive block;
- keep tasks, channels and users page ownership in `ManagementController`;
- remove settings-only repositories, services and normalizers from `ManagementController`;
- keep the existing WebMvc route coverage by loading management, passport and settings page controllers in the current MVC slice;
- add a settings source-layout contract that asserts positive ownership in `SettingsPageController` and negative ownership in `ManagementController`;
- no endpoint, template, API, persistence, SCSS/generated CSS or deployment behavior changes.

## Verification

- exact pinned source blobs and clean-worktree guards;
- settings and remaining management route blocks preserved byte-for-byte after LF normalization;
- full `ManagementControllerWebMvcTest` plus `SettingsPageControllerSourceContractTest`;
- restore only known Maven/Sass generated CSS artifacts inside the sandbox before the final diff gate;
- `git diff --check`; no stage/commit/push/deploy.
