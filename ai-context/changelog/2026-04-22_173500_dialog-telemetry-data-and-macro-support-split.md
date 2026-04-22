# 2026-04-22 17:35:00 — dialog telemetry data and macro support split

## Что сделано

- добавлен `DialogWorkspaceTelemetryDataService` и вынесены raw
  `workspace_telemetry_audit` read-model/JDBC/aggregation сценарии из
  `DialogService`;
- добавлен `DialogMacroGovernanceSupportService` и вынесены helper-сценарии
  macro governance/usage/variables из `DialogService`;
- `DialogService` переключён на новые telemetry/macro support layers, а старые
  raw helper/data blocks удалены из giant service;
- добавлены targeted tests:
  `DialogWorkspaceTelemetryDataServiceTest` и
  `DialogMacroGovernanceSupportServiceTest`.

## Проверка

- `.\mvnw.cmd -q "-Dtest=DialogWorkspaceTelemetryDataServiceTest,DialogMacroGovernanceSupportServiceTest,DialogWorkspaceTelemetrySummaryServiceTest,DialogMacroGovernanceAuditServiceTest,DialogWorkspaceTelemetryControllerWebMvcTest,DialogServiceTest" test`
- `.\mvnw.cmd -q -DskipTests compile`

## Эффект

- boundary-слой вокруг telemetry/notifier перестал быть только фасадом и
  получил реальные data/support dependencies;
- residual risk по giant `DialogService` смещён с raw telemetry/macro helper
  блока на remaining orchestration и compatibility delegates.
