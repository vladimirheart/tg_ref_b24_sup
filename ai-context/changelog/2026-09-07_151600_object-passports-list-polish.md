# 2026-09-07 — object passports list polish (01-258)

## Request

Продолжить UI passports: компактная шапка, титульное фото с hover preview, читаемый счётчик обращений и reversible soft-delete старых паспортов ID 1–9.

## Changes

- список паспортов получает compact one-row desktop header и доступные icon-only actions;
- service отдаёт `title_photo_url` и `deleted`;
- department cell показывает title thumbnail/placeholder и единый floating preview с 500 ms delay; после visual review добавлен hover-handoff с миниатюры на увеличенный preview;
- deleted passports скрываются по умолчанию, доступны через switch или явный status filter, counters работают по visibility scope;
- status defaults расширены значением `Удалён`;
- appeals badge усилен, использует жирные tabular digits и увеличен примерно на 1 px после visual review;
- добавлен source-contract test;
- production data mutation ID 1–9 намеренно отложена до rollout UI.
