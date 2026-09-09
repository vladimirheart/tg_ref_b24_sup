# 2026-09-09 - clean-code source layout, phase 3a: settings users/access template

## Scope

- baseline: `72da58b25046238b32877d20ae4e9210c9c853b1`;
- extract the users/access workspace from `templates/settings/index.html`;
- keep the main users modal and its child dialogs together as one stable responsibility;
- compose the extracted block with a wrapperless Thymeleaf `th:block` fragment;
- preserve the original modal ids, source order and rendered DOM contract;
- require exact source round-trip validation and a real `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `usersModal`;
- `authUserDetailsModal`;
- `authUserPasswordModal`;
- `authUserPhotoPreviewModal`;
- `orgMembersModal`.

## Not changed

Settings runtime JavaScript, controllers, services, API, database/schema/data, generated CSS, Docker, NetBox and production services.
