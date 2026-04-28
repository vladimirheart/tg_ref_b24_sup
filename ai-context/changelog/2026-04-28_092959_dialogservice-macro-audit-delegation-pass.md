# 2026-04-28 09:29:59 - dialogservice macro audit delegation pass

## Что сделано
- giant `DialogService` больше не держит дублирующий
  `macro governance audit` bounded context:
  `buildMacroGovernanceAudit(...)` переведён на прямой delegate в
  `DialogMacroGovernanceAuditService`
- из constructor dependency `DialogService` убран уже ненужный
  `DialogMacroGovernanceSupportService`
- `DialogServiceTest` расширен regression-сценарием на delegate-контракт
  `buildMacroGovernanceAudit(...)`
- синхронизированы `01-024`, roadmap и architecture audit под новый
  размер и текущий remaining scope giant service

## Проверка
- `spring-panel\mvnw.cmd -q "-Dtest=DialogServiceTest,DialogMacroGovernanceAuditServiceTest,DialogWorkspaceTelemetryControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Зачем
- снять из giant `DialogService` ещё один крупный дублирующий bounded context
- оставить giant service совместимым фасадом, а реальное ownership macro
  governance audit держать в отдельном domain service
- подготовить следующий `Phase 3` проход уже под workspace telemetry summary
  и remaining compatibility delegates
