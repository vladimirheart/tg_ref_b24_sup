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
