# 2026-09-16 - clean-code source layout, phase 3aa: passport detail media workspace

## Scope

- baseline: `49c05f5166f98c9fdc61d8cee502799c85e3b237`;
- extract the read-only `photos` panel and sibling `passportPhotoViewer` from `templates/passports/detail.html` into one passport-detail media fragment file;
- keep gallery controls and the full-screen photo viewer together as one cohesive read-only media responsibility while preserving their two original composition points;
- preserve the parent `passportWorkspace` closing div between the photos panel and viewer in the page shell; do not move the viewer inside the workspace;
- preserve all existing DOM ids, data hooks and the current inline browser runtime contract;
- compose the fragment through two Thymeleaf entrypoints: `photosPanel` and `photoViewer`;
- update the source-contract test so `detail.html` owns both composition points and `detail-media.html` owns the media markup;
- leave overview/network/equipment/activity/edit composition and the existing inline runtime in `detail.html` for separate architectural review / phase P4;
- require exact structural round-trip validation plus the detail/edit WebMvc smoke tests and focused passport-detail source-contract test.

## Responsibility boundary

- `detail-media.html`: read-only object photo panel plus the photo viewer overlay.
- `passports/detail.html`: detail workspace shell, media composition points, edit-fragment composition, error host and existing inline runtime pending phase P4.

## Not changed

Browser runtime behavior, controllers/services/API, persistence/data, SCSS, generated CSS, deployment and production services.
