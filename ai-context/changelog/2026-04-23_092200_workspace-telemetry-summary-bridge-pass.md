# 2026-04-23 09:22:00 — workspace telemetry summary bridge pass

## Что сделано

- добавлен `DialogWorkspaceTelemetrySummaryBridgeService` как явный
  compatibility bridge для workspace telemetry summary;
- `DialogWorkspaceTelemetrySummaryService` больше не зависит от
  `DialogService` напрямую и работает через bridge-слой;
- добавлен targeted test `DialogWorkspaceTelemetrySummaryBridgeServiceTest`,
  а `DialogWorkspaceTelemetrySummaryServiceTest` переведён на новый контракт;
- обновлены `ARCHITECTURE_AUDIT_2026-04-08.md`,
  `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md` и `01-024.md`.

## Проверка

- `mvnw.cmd -q "-Dtest=DialogWorkspaceTelemetrySummaryServiceTest,DialogWorkspaceTelemetrySummaryBridgeServiceTest,DialogWorkspaceTelemetryControllerWebMvcTest" test`
- `mvnw.cmd -q -DskipTests compile`

## Зачем

Это снимает последний прямой consumer-boundary хвост `DialogService` в
основном service/controller-слое и переводит remaining связность в явный
compatibility bridge, который можно убирать отдельным следующим этапом без
ломки runtime API.
