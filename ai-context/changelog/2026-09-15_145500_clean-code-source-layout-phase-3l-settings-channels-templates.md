# 2026-09-15 - clean-code source layout, phase 3l: settings channels templates

## Scope

- baseline: `3ffac6b9a59b979dcd9b42af55cbc51ab7310127`;
- extract the `channels-templates` tab from `templates/settings/index.html`;
- keep question templates, rating templates and unblock cooldown together as one channels-templates workspace;
- move `botTemplateEditorModal` and `botRatingTemplateModal` into the same fragment through a second wrapperless Thymeleaf entrypoint;
- preserve the existing bot-settings DOM hooks, modal ids and runtime behavior;
- keep channels management/integration network, automation, add-channel UI and channel editor outside this fragment;
- normalize only pre-existing whitespace-only lines inside the extracted ranges so staged `git diff --check` remains clean;
- require exact two-point structural round-trip validation after blank-line normalization and `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `templatesWorkspace`: channels template catalog, active template selection, rating templates and unblock cooldown.
- `templateEditorModals`: question-template and rating-template editors used by the templates workspace.

## Not changed

Runtime JS, controllers/services/API, DB/data, generated CSS, Docker and production services.
