# 2026-09-16 - clean-code source layout, phase 4e: passport detail activity runtime

## Scope

- baseline: `8342274b83b763ae244b0e8104728fe0ec478bb8`;
- extract the read-only cases/tasks/incidents renderer from the passport detail inline browser runtime into `static/js/passport-detail-activity-runtime.js`;
- move `recordCard` and `renderActivity`; keep activity API loading/state assignment, media, editor, page shell and bootstrap inline for later P4 steps;
- expose a narrow guarded global `PassportDetailActivityRuntime` with only `renderActivity` as its public feature API; `recordCard` stays private;
- inject current `cases`, `tasks` and `incidents` through getters so the renderer observes page-state replacement without owning fetch lifecycle;
- depend on the pinned `PassportDetailCoreRuntime` helper facade while preserving overview/network/equipment runtimes as independent dependencies;
- keep the passport detail source-contract byte-for-byte unchanged because the `/cases`, `/tasks` and `/incidents` API ownership remains in the page bootstrap;
- derive source/dependency/source-contract size guards from pinned Git blobs instead of copied byte counts;
- require generated-runtime `node --check`, exact extraction round-trip, detail/edit WebMvc smoke tests and the focused passport-detail source-contract test.

## Not changed

Controller/service/API behavior, persistence/data, activity API URLs/loading, Thymeleaf bootstrap values, fragment markup ownership, media/editor behavior, SCSS/generated CSS, deployment and production services.
