# 2026-09-15 - clean-code source layout, phase 3o: settings input formatting

## Scope

- baseline: `84a87f103bae9aa7775729319bcb7cc39d4b9c70`;
- extract `inputFormattingModal` from `templates/settings/index.html`;
- keep phone, e-mail, address and font-preview controls together as one input-formatting responsibility;
- compose the modal through one wrapperless Thymeleaf fragment entrypoint;
- preserve the existing input-settings runtime ids and data hooks;
- keep users/access, backup and IT settings outside this fragment;
- normalize only pre-existing whitespace-only lines inside the extracted block so staged `git diff --check` remains clean;
- require exact structural round-trip validation after blank-line normalization and `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `inputFormattingWorkspace`: phone formatting/types, e-mail validation, address parsing/order and local font previews.

## Not changed

Runtime JS, controllers/services/API, DB/data, generated CSS, Docker and production services.
