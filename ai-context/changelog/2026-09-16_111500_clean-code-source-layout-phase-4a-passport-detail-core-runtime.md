# 2026-09-16 - clean-code source layout, phase 4a: passport detail core runtime facade

## Scope

- baseline: `cfe63cca8ead0e57318a194f361ed1ca156f69a9`;
- start browser-JS phase P4 with a narrow compatibility facade instead of moving the whole detail runtime into one large external file;
- extract the contiguous detail core helper block (`DAY_LABELS`, text/HTML normalization, property rendering, media parsing and equipment-catalog lookup) into `static/js/passport-detail-core-runtime.js`;
- expose the helpers through the guarded global `PassportDetailCoreRuntime` mount API, following the existing project runtime pattern;
- keep Thymeleaf-provided page data, mutable page state, feature renderers, fetch/save flows and event orchestration in `passports/detail.html` for later P4 feature steps;
- load the core runtime before the existing inline Thymeleaf bootstrap and bridge its API back to the same local helper names, preserving current call sites;
- update the passport detail source-contract test so catalog lookup ownership moves from the template source to the core runtime source;
- derive source and source-contract size guards from pinned Git blobs instead of manually copied byte counts;
- require generated-runtime `node --check`, exact runtime extraction round-trip, detail/edit WebMvc smoke tests and the focused passport-detail source-contract test.

## Compatibility boundary

- `PassportDetailCoreRuntime`: stable page-level facade for core helpers and equipment-catalog lookup.
- `passports/detail.html`: retains server-injected config/state and feature orchestration; it mounts the facade and consumes the same helper names.

## Not changed

Controller/service/API behavior, persistence/data, fragment markup ownership, SCSS/generated CSS, deployment and production services.
