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
