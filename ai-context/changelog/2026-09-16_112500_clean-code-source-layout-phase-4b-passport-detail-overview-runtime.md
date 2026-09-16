# 2026-09-16 - clean-code source layout, phase 4b: passport detail overview runtime

## Scope

- baseline: `307c664dd264d16579b1ffec96150dfedd4275ca`;
- extract the read-only overview renderer from the passport detail inline browser runtime into `static/js/passport-detail-overview-runtime.js`;
- move only `renderOverview`, `renderSchedule`, `completionInfo` and `renderQuality`; header/cover, network, equipment, activity, media, editor and page bootstrap remain inline for later P4 steps;
- expose a narrow guarded global `PassportDetailOverviewRuntime` with only `renderOverview` as its public feature API;
- inject the current passport through `getPassport()` so the feature runtime observes later page-state replacement without copying mutable state;
- depend on the pinned `PassportDetailCoreRuntime` helper facade rather than duplicating text/HTML/property helpers;
- keep the existing passport detail source-contract test unchanged and run it as a regression check;
- derive all guarded sizes from pinned Git blobs instead of copied byte counts;
- require generated-runtime `node --check`, exact extraction round-trip, detail/edit WebMvc smoke tests and the focused passport-detail source-contract test.

## Not changed

Controller/service/API behavior, persistence/data, Thymeleaf bootstrap values, fragment markup ownership, SCSS/generated CSS, deployment and production services.
