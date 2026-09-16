# 2026-09-16 - clean-code source layout, phase 4g: passport detail header runtime

## Scope

- baseline: `edbdc32349f74ec7c38fac69ee407501ac80dac8`;
- extract passport detail header and cover rendering from the inline browser runtime into `static/js/passport-detail-header-runtime.js`;
- move `renderCover` and `renderHeader` as one read-only page-header responsibility; keep editor lifecycle, photo mutations, counts, tabs, loading and page bootstrap inline;
- expose a guarded global `PassportDetailHeaderRuntime` with public APIs `renderCover` and `renderHeader`;
- inject the current passport and passport id through getters so later passport replacement stays observable without moving page-owned state;
- depend only on the pinned `PassportDetailCoreRuntime` helper facade while preserving overview/network/equipment/activity/media runtimes as independent dependencies;
- keep the passport detail source-contract byte-for-byte unchanged because its current assertions do not own header renderer implementation;
- derive source/dependency/source-contract size guards from pinned Git blobs instead of copied byte counts;
- require generated-runtime `node --check`, exact structural round-trip, detail/edit WebMvc smoke tests and the focused passport-detail source-contract test.

## Not changed

Controller/service/API behavior, persistence/data, photo upload/update/delete behavior, editor state/lifecycle, activity loading, tab/count orchestration, Thymeleaf bootstrap values, fragment markup ownership, SCSS/generated CSS, deployment and production services.
