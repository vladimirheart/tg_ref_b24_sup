# 2026-09-16 - clean-code source layout, phase 4d: passport detail equipment runtime

## Scope

- baseline: `26a88064c40de6dbb311af494e408a3c18e0e9aa`;
- extract the read-only equipment view/renderer from the passport detail inline browser runtime into `static/js/passport-detail-equipment-runtime.js`;
- move `equipmentView` and `renderEquipment`; keep equipment editor/archive/restore lifecycle, activity, media, page shell and bootstrap inline for later P4 steps;
- expose a narrow guarded global `PassportDetailEquipmentRuntime` with only `renderEquipment` as its public feature API; `equipmentView` stays private;
- inject the current passport through `getPassport()` and resolve the equipment search control inside the feature runtime;
- depend on the pinned `PassportDetailCoreRuntime` helper facade while preserving the already extracted overview/network runtimes as independent dependencies;
- update the passport detail source-contract so `passport-asset-card` ownership moves from the template source to the equipment runtime source; editor archive/restore assertions remain template-owned;
- derive source/dependency/source-contract size guards from pinned Git blobs instead of copied byte counts;
- require generated-runtime `node --check`, exact extraction round-trip, detail/edit WebMvc smoke tests and the focused passport-detail source-contract test.

## Not changed

Controller/service/API behavior, persistence/data, Thymeleaf bootstrap values, fragment markup ownership, editor equipment/archive behavior, SCSS/generated CSS, deployment and production services.
