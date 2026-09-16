# 2026-09-16 - clean-code source layout, phase 3w: passport editor media workspace

## Scope

- baseline: `10e3ffede31b14f374f224fd0417204c036dfdf7`;
- extract the balanced `photosSection` and `photoPreviewModal` blocks from `templates/passports/new.html` into one passport-editor media fragment file;
- keep upload/gallery controls and photo preview modal together as one cohesive media responsibility while preserving their two original composition points;
- preserve all existing DOM ids and the current inline runtime contract;
- keep `equipmentSection` in the page shell and preserve whitespace-only boundaries from photos to equipment and from preview modal to Bootstrap script;
- compose the fragment through two Thymeleaf entrypoints: `photosSection` and `photoPreviewModal`;
- leave the small `scheduleCard` in the page shell and defer browser-runtime decomposition to phase P4;
- require exact structural round-trip validation and the existing `/object-passports/new` WebMvc smoke tests.

## Responsibility boundary

- `editor-media.html`: object photo upload/gallery UI plus the photo preview carousel modal.
- `passports/new.html`: editor page shell, schedule/equipment sibling workspaces and the existing inline runtime pending phase P4.

## Not changed

Browser runtime behavior, controllers/services/API, persistence/data, SCSS, generated CSS, deployment and production services.
