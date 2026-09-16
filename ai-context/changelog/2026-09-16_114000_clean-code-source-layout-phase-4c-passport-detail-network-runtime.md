# 2026-09-16 - clean-code source layout, phase 4c: passport detail network runtime

## Scope

- baseline: `dc8e1d400ea81afea40d446be94689950b5247ce`;
- extract the read-only network renderer from the passport detail inline browser runtime into `static/js/passport-detail-network-runtime.js`;
- move only `renderNetwork`; header/cover, equipment, activity, media, editor and page bootstrap remain inline for later P4 steps;
- expose a narrow guarded global `PassportDetailNetworkRuntime` with only `renderNetwork` as its public feature API;
- inject the current passport through `getPassport()` so the runtime observes later page-state replacement without copying mutable state;
- depend on the pinned `PassportDetailCoreRuntime` helper facade and keep the already extracted overview runtime independent;
- keep the existing passport detail source-contract test unchanged and run it as a regression check;
- derive guarded sizes from pinned Git blobs instead of copied byte counts;
- require generated-runtime `node --check`, exact extraction round-trip, detail/edit WebMvc smoke tests and the focused passport-detail source-contract test.

## Not changed

Controller/service/API behavior, persistence/data, Thymeleaf bootstrap values, fragment markup ownership, SCSS/generated CSS, deployment and production services.
