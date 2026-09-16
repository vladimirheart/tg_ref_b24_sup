# 2026-09-16 - clean-code source layout, phase 4f: passport detail media runtime

## Scope

- baseline: `ca812a07a65d5e7659e00b7c52251440abe9b1b3`;
- extract the read-only passport photo grid and photo-viewer lifecycle from the detail inline browser runtime into `static/js/passport-detail-media-runtime.js`;
- move `renderPhotos`, `viewablePhotos`, `renderPhotoViewer`, `openPhotoViewer`, `closePhotoViewer` and `movePhotoViewer`; keep upload/PATCH/DELETE, editor photo rows, cover rendering and page bootstrap inline;
- expose a guarded global `PassportDetailMediaRuntime` with public APIs `renderPhotos`, `openPhotoViewer`, `closePhotoViewer` and `movePhotoViewer`; viewer list/render helpers stay private;
- move `photoViewerPosition` into the media runtime as private state and inject the current passport through `getPassport()`;
- depend on the pinned `PassportDetailCoreRuntime` helper facade while preserving overview/network/equipment/activity runtimes as independent dependencies;
- keep the passport detail source-contract byte-for-byte unchanged because media fragment ownership and editor/API behavior do not change;
- derive source/dependency/source-contract size guards from pinned Git blobs instead of copied byte counts;
- require generated-runtime `node --check`, exact multi-block structural round-trip, detail/edit WebMvc smoke tests and the focused passport-detail source-contract test.

## Not changed

Controller/service/API behavior, persistence/data, photo upload/update/delete endpoints, editor photo behavior, cover renderer, Thymeleaf bootstrap values, fragment markup ownership, SCSS/generated CSS, deployment and production services.
