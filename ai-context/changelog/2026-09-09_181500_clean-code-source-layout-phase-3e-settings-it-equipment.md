# 2026-09-09 - clean-code source layout, phase 3e: settings IT equipment template

## Scope

- baseline: `3988056f2491009802fa7c814578a7cb7de09668`;
- extract the IT equipment catalogue accordion section and its editor/photo modal suite from `templates/settings/index.html`;
- keep catalogue and equipment/media child modals together as one responsibility with two Thymeleaf entrypoints;
- leave NetBox sync, connections, provider profiles, remote access and bot configuration in place;
- preserve ids, source order, modal parent/suspend contracts and rendered DOM;
- require exact source round-trip validation and a real `/settings` Thymeleaf render smoke-test.

## Responsibility boundary

- `equipmentSection` composes the `itEquipmentSection` accordion item;
- `equipmentModals` composes the equipment editor plus add/view/edit/confirm photo modals;
- both entrypoints live in `settings/fragments/it-equipment.html` as one equipment/media responsibility.

## Not changed

Settings runtime JavaScript, controllers, services, API, database/schema/data, generated CSS, Docker, NetBox synchronization behavior and production services.
