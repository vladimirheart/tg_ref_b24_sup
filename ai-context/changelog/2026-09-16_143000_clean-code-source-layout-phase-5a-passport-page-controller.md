# 2026-09-16 - clean-code source layout, phase 5a: passport page controller

## Scope

- baseline: `395ad687d78251915f0e15451738aad2d8c6c046`;
- move all object-passport MVC page routes from `ManagementController` into feature-owned `com.example.panel.passports.ObjectPassportPageController`;
- move the complete passport editor model assembly, effective-location parameter normalization, equipment catalogue projection and passport-only helper methods with those routes;
- keep tasks, channels, users and settings page ownership in `ManagementController`;
- remove passport-only dependencies from `ManagementController`;
- keep the existing WebMvc route coverage by loading both controllers in the current MVC slice;
- update the passport source contract to point at the feature controller and assert that passport routes no longer live in `ManagementController`;
- no endpoint, template, service, persistence, API, SCSS/generated CSS or deployment behavior changes.

## Verification

- exact pinned source blobs and clean-worktree guards;
- moved passport route/model blocks preserved byte-for-byte after LF normalization;
- full `ManagementControllerWebMvcTest` and `ObjectPassportWorkspaceUiSourceContractTest`;
- restore only known Maven/Sass generated CSS artifacts inside the sandbox before the final diff gate;
- `git diff --check`; no stage/commit/push/deploy.
