# 2026-09-09 - clean-code source layout, phase 3c: settings dialogs template

## Scope

- baseline: `86c8afc7e8c4f21331e1d5bdbde5b3142ebb5f85`;
- extract the dialog settings workspace from `templates/settings/index.html`;
- keep categories, questions, completion actions, macros, SLA/workspace governance, metrics and badges together as one dialog-settings responsibility;
- compose the extracted block with a wrapperless Thymeleaf `th:block` fragment;
- preserve the original modal ids, source order and rendered DOM contract;
- require exact source round-trip validation and a real `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `categoriesModal`;
- six dialog settings tabs remain in the same fragment and source order.

## Not changed

Settings runtime JavaScript, controllers, services, API, database/schema/data, generated CSS, Docker, NetBox and production services.
