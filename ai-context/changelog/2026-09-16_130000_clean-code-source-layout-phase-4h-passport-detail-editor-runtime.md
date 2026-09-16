# 2026-09-16 - clean-code source layout, phase 4h: passport detail editor runtime

## Scope

- baseline: `ca59a214f3f137b4ca7a2e3a323bf55df1d92e4c`;
- extract the complete passport detail editor and mutation lifecycle into `static/js/passport-detail-editor-runtime.js` as one cohesive feature runtime;
- move scalar-field rendering, schedule/equipment drafts, editor photo rows, open/close/save, passport PUT, photo POST/PATCH/DELETE and editor-specific event binding in one guarded step;
- keep `editEquipmentDraft` and `editScheduleDraft` private inside the editor runtime and expose only `openEditor`, `closeEditor` and `bindEvents`;
- inject page state accessors plus narrow `refreshWorkspace` and `refreshMedia` callbacks instead of coupling the editor runtime directly to overview/network/equipment/activity/media runtimes;
- keep cross-feature media-viewer keyboard/click binding, counts, tabs, activity loading and page bootstrap inline;
- update the passport detail source-contract so equipment archive/restore lifecycle assertions follow their new editor-runtime ownership;
- derive source/dependency/source-contract size guards from pinned Git blobs instead of copied byte counts;
- require generated-runtime `node --check`, exact multi-block structural round-trip, detail/edit WebMvc smoke tests and the focused passport-detail source-contract test.

## Not changed

Controller/service/API contracts, persistence/data, fragment markup ownership, read-only feature runtimes, activity API loading, tab/count behavior, Thymeleaf bootstrap values, SCSS/generated CSS, deployment and production services.
