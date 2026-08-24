# 2026-08-24 10:05 — NetBox site resilience + selection closeout

## User prompt

`делай`

## Scope

Закрыть связанный блок задач `01-179` + `01-180` после production-contour closeout: проверить фактическую реализацию NetBox partial-degradation/site selection, добавить недостающие regression tests и перевести задачи в статус ручной приёмки.

## Changes

- runtime NetBox-код не дублировался: audit подтвердил, что site-level fallback, safe image listing, selected-site settings/API/UI/filter уже присутствуют;
- `NetBoxObjectPassportSyncServiceTest` дополнен сценариями image-attachments HTTP 500, локального падения одного site и selected-site filtering;
- `SettingsNetBoxSyncControllerWebMvcTest` теперь фиксирует `status` как часть site catalog API contract;
- `01-179` и `01-180` переведены из `🟡` в `🟣` и получили verification notes.

## Files

- `spring-panel/src/test/java/com/example/panel/service/NetBoxObjectPassportSyncServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/SettingsNetBoxSyncControllerWebMvcTest.java`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-179.md`
- `ai-context/tasks/task-details/01-180.md`
- `ai-context/changelog/2026-08-24_100500_netbox-site-resilience-selection-closeout.md`