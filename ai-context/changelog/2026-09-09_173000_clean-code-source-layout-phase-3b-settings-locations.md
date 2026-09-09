# 2026-09-09 - clean-code source layout, phase 3b: settings locations template

## Scope

- baseline: `a470a8c93a3eb278166e5c2e80ab7f5a80be7f22`;
- extract the locations workspace from `templates/settings/index.html`;
- keep the locations editor and its child wizard together as one stable responsibility;
- compose the extracted block with a wrapperless Thymeleaf `th:block` fragment;
- preserve the original modal ids, source order and rendered DOM contract;
- require exact source round-trip validation and a real `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `locationsModal`;
- `locationWizardModal`.

## Not changed

Settings runtime JavaScript, controllers, services, API, database/schema/data, generated CSS, Docker, NetBox and production services.
