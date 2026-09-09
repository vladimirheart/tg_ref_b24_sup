# 2026-09-09 - clean-code source layout, phase 3d: settings partner data template

## Scope

- baseline: `1c92d9c9c2850112d263e3b66e54bd37264f40db`;
- extract partner parameters, legal entities and partner contact editors from `templates/settings/index.html`;
- keep partner parameters, parameter values, legal entities and partner contact editing together as one partner-data responsibility;
- compose the extracted block with a wrapperless Thymeleaf `th:block` fragment;
- preserve the original modal ids, source order and rendered DOM contract;
- require exact source round-trip validation and a real `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `parametersModal`;
- `parameterItemsModal`;
- `legalEntitiesModal`;
- `partnerContactEditorModal`;
- three partner parameter tabs remain in the same fragment and source order.

## Not changed

Settings runtime JavaScript, controllers, services, API, database/schema/data, generated CSS, Docker, NetBox and production services.
