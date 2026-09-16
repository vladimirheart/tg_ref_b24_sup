# 2026-09-16 - clean-code source layout, phase 4i: passport detail page runtime

## Scope

- baseline: `2c25e48e1719b19242fcdb4258d5426e184f228f`;
- complete passport detail browser-runtime decomposition by extracting the remaining page lifecycle/orchestration into `static/js/passport-detail-page-runtime.js`;
- move page state, feature-runtime composition, refresh callbacks, counts, tabs, media-viewer cross-feature events, activity/API loading and loading/error lifecycle as one cohesive page runtime;
- expose only `boot` publicly; keep `passport`, `cases`, `tasks` and `incidents` private to the page runtime;
- keep only Thymeleaf-provided bootstrap values plus a narrow `PassportDetailPageRuntime.mount(...).boot()` bridge inline in `detail.html`;
- preserve the already extracted core/header/overview/network/equipment/activity/media/editor feature runtimes as independent dependencies instead of merging feature logic back into the page module;
- update the passport detail source-contract so activity API URLs and runtime-facade composition assertions follow their new page-runtime ownership;
- derive source/dependency/source-contract size guards from pinned Git blobs instead of copied byte counts;
- require generated-runtime `node --check`, exact template extraction round-trip, detail/edit WebMvc smoke tests and the focused passport-detail source-contract test.

## Not changed

Controller/service/API contracts, persistence/data, fragment markup ownership, feature runtime behavior, editor mutation behavior, Thymeleaf data values, SCSS/generated CSS, deployment and production services. `passports/new.html` remains for later P4 work.
