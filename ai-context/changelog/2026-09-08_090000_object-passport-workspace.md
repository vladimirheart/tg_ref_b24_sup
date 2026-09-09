# 2026-09-08 — object passport workspace and equipment cards (01-259)

## Request

Снизить визуальный шум страницы паспорта объекта, уплотнить размещение данных и связать оборудование паспорта с мастер-каталогом оборудования из Настроек. Визуальный ориентир для оборудования — компактные asset cards с изображением, статусом и ключевыми атрибутами вместо постоянно раскрытой формы.

## Changes

- существующий паспорт получает отдельный view-first route/template, а полный editor переносится на `/object-passports/{id}/edit`;
- detail workspace получает компактный header, KPI strip и tabs для Overview / IT и сети / оборудования / обращений / задач / фото;
- Overview показывает только заполненные поля и вычисляемую заполненность паспорта;
- equipment instances обогащаются master-каталогом сначала по `catalog_id`, затем fallback по `type + vendor + model`;
- master-каталог передаёт в UI собственный `id` и используется для фото/ссылок/базовой комплектации;
- каталог оборудования в Settings переводится с inline edit table на searchable asset-card grid, а существующая modal используется и для создания, и для редактирования;
- schema/data migration намеренно не выполняется.


## R4 visual acceptance follow-up

- per-user/per-page font scale uses existing server-backed UI preferences;
- photo KPI opens the photo tab and object photos open in a gallery viewer;
- modern workspace becomes the primary edit surface, with legacy editor retained only as fallback;
- appeal matching is location-first and deduplicates ticket IDs;
- settings equipment catalogue surfaces read-only discovered models from existing passport JSON and lets the user explicitly promote them;
- NetBox catalogue discovery no longer requires a non-empty vendor;
- no schema/data migration or automatic catalogue backfill is performed.

## R4.1 production visual-regression hotfix

- restored the 01-258 catalogue/legacy-editor SCSS that was accidentally dropped when r4 replaced `_passports.scss` with workspace-only rules;
- retained the r4 workspace/photo-viewer/edit-drawer refinements on top of the restored stylesheet;
- protected equipment edit mode from the shared Bootstrap modal `show` lifecycle reset, so «Изменить» keeps the persisted card and pre-fills its fields;
- bumped the passports-list CSS and Settings equipment-runtime asset versions to avoid stale browser cache;
- added source contracts that explicitly pin 42×32 thumbnails, 320×220 hover preview and the equipment edit lifecycle guard;
- no backend/data/schema/NetBox mutation is part of this hotfix.

## R4.2 UI runtime stabilization

- changed per-page font scaling from an absolute html percentage to a multiplier of the stylesheet-defined root baseline, so displayed 100% preserves the project's original typography/geometry;
- deferred baseline resolution until DOMContentLoaded and cache-busted ui-preferences.js;
- protected SRI-sensitive vendored static assets from EOL conversion with .gitattributes and added Docker build-time SHA-384 checks for Bootstrap CSS/JS;
- added source/runtime integrity tests for Bootstrap bytes, relative font scaling and the compact sidebar account footer;
- removed the noisy «Аккаунт» kicker, suppressed duplicate username text, and reduced account/action/font-control dimensions without removing functionality;
- retained the r4.1 passport thumbnail and equipment edit-mode corrections; no backend/data/schema/NetBox mutation is included.

## R4.3 equipment catalogue visual polish

- added catalogue filters by equipment type and vendor in addition to free-text search;
- catalogue cards now use the persisted model photo when present and a neutral placeholder otherwise, with the equipment type centered as the visual badge;
- edit/delete actions became compact icon controls in the card header and card/meta spacing was tightened while increasing readability;
- replaced the misleading links counter with «Используется у объектов» based on a unique object/passport count, while retaining instance usage_count internally;
- persisted catalogue rows now receive usage/object counts from passport-discovered aggregates;
- no schema/data migration, automatic backfill, NetBox mutation or ownership-contract change is included.

## R4.4 equipment photos, card alignment and archive history

- moved the equipment type badge from the left media rail to the center of the whole catalogue card, placed catalog id in the bottom-right corner and stacked edit/delete icons vertically;
- added persisted equipment photo upload with mandatory category/comment and compact thumbnail management in the equipment modal;
- stores versioned equipment media metadata in the existing photo_url CLOB, retaining legacy documentation links and avoiding schema migration;
- title/general photo semantics now drive the card cover instead of treating arbitrary links as images;
- catalog deletion is blocked server-side while the model is actively used by object passports, resolving catalog_id before the legacy type/vendor/model fallback;
- removing passport equipment now archives the instance with timestamp/history styling instead of physically dropping it; archived instances are excluded from active catalogue usage/discovery and can be restored;
- no NetBox write/sync behavior, ownership contract, schema or automatic backfill changes are included.

## R4.5 equipment photo workspace

- moved equipment media management into a dedicated tab with 25x25 thumbnail rows, visible descriptions and edit/delete actions;
- replaced external-window photo viewing with an in-app Bootstrap viewer modal with previous/next navigation and metadata;
- added PATCH metadata editing for photo category/comment and explicit replace_title confirmation on both upload and edit;
- title replacement and photo deletion now use managed UI confirmation modals instead of browser confirm dialogs;
- deleting or demoting the current title photo promotes the first remaining photo to title, keeping card cover semantics consistent;
- corrected the r4.4 thumbnail CSS scope from #itEquipmentSection to #itEquipmentAddModal, which is the actual modal DOM location;
- no database schema migration, data backfill, NetBox mutation or ownership-contract change is included.

## R4.6 equipment photo integrity and modal stack

- enlarged equipment-photo previews and moved photo creation into an icon-triggered compact modal;
- made equipment media mutations row-locked and transactional so uploads append instead of losing sibling photos, while main-card link saves preserve the latest media envelope;
- added full photo-object editing with optional binary replacement, category/comment editing, and commit-safe storage cleanup;
- enforced a single title photo in serialized media and retained explicit replace_title confirmation for upload/edit/replace paths;
- retained title fallback promotion after delete/demotion so card cover and photo type stay consistent;
- added a settings-wide topmost Escape guard for nested Bootstrap modals so Esc closes only the highest visible modal;
- cache-busted settings CSS, equipment runtime, and settings-page-shell for the r4.6 browser contract;
- no database schema migration, data backfill, NetBox mutation or ownership-contract change is included.

## R4.7 production acceptance fixes

- narrowed the main equipment save transaction boundary so best-effort notification/discovery reads cannot poison the media/catalog write transaction; photo media mutations remain row-locked and transactional;
- omitted no-op photo_url updates from ordinary card saves when links are unchanged;
- moved persistent photo workflow notes into an info popover;
- replaced native file controls with icon actions and added clipboard image paste for add/replace flows;
- retained all r4.6 media integrity and nested-modal Escape behavior;
- no database schema migration, backfill, NetBox mutation, or ownership-contract change is included.
