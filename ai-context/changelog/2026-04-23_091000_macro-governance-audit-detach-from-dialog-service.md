# 2026-04-23 09:10:00 — macro governance audit detach from DialogService

## Что сделано

- `DialogMacroGovernanceAuditService` перестал быть delegating-wrapper над
  `DialogService` и получил собственную реализацию macro governance audit;
- giant `DialogService` больше не является обязательной consumer-boundary
  точкой для macro governance audit слоя;
- `DialogMacroGovernanceAuditServiceTest` синхронизирован с новым
  constructor/ownership contract;
- актуализированы `architecture audit`, `roadmap` и `01-024` под состояние,
  где среди прямых consumer-зависимостей от `DialogService` остался только
  `DialogWorkspaceTelemetrySummaryService`.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=DialogMacroGovernanceAuditServiceTest,DialogWorkspaceTelemetryControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Эффект

- direct dependency surface giant `DialogService` ещё сузилась;
- следующий логичный сервисный хвост по аудиту теперь один и явно локализован:
  `DialogWorkspaceTelemetrySummaryService`.
