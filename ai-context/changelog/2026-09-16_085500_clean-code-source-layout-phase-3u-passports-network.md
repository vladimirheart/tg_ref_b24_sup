# 2026-09-16 - clean-code source layout, phase 3u: passport editor network workspace

## Scope

- baseline: `f708eb2020a0baf89789b15bfe7feee6e829d62f`;
- extract the balanced `networkCard` from `templates/passports/new.html` into a dedicated passport-editor fragment;
- keep internal network, provider details, connection parameters and network-file controls together as one cohesive editor responsibility;
- preserve all existing DOM ids and the current inline runtime contract;
- preserve the whitespace-only sibling boundary between `networkCard` and `scheduleCard` in the page shell;
- compose the fragment through a single Thymeleaf entrypoint `networkCard`;
- require exact structural round-trip validation and the existing `/object-passports/new` WebMvc smoke tests.

## Responsibility boundary

- `networkCard`: internal network, tunnel/equipment summary, provider contract details, internet parameters and network files.
- `passports/new.html`: editor page shell, sibling workspaces and the existing inline runtime pending phase P4.

## Not changed

Browser runtime behavior, controllers/services/API, persistence/data, SCSS, generated CSS, deployment and production services.
