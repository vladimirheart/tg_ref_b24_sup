# 2026-09-15 - clean-code source layout, phase 3i: settings panel design template

## Scope

- baseline: `4c3515b6bc2474c13d48ada697b747f060483bd8`;
- extract the panel appearance/settings modal from `templates/settings/index.html`;
- keep theme/palette, client-status styling and business-style controls together as one appearance responsibility;
- compose it through one wrapperless Thymeleaf fragment entrypoint;
- preserve the existing `appearance` page-shell contract, modal ids and design-workspace DOM hooks;
- normalize only pre-existing whitespace-only lines inside the extracted block so staged `git diff --check` remains clean;
- require exact structural round-trip validation after that blank-line normalization and `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `panelDesignSettings`: UI theme/palette, client statuses and business visual styles.

## Not changed

Runtime JS, controllers/services/API, DB/data, generated CSS, Docker and production services.
