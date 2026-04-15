# 2026-04-15 19:27:52 - dialog macro, telemetry, ai and auth split

## Что сделано

- добавлен `DialogAuthorizationService` как общий слой для dialog permission
  checks и audit logging новых controller-срезов;
- вынесен macro API диалогов в `DialogMacroController` и `DialogMacroService`;
- вынесен workspace telemetry API и summary orchestration в
  `DialogWorkspaceTelemetryController` и
  `DialogWorkspaceTelemetryService`;
- вынесен AI ops API диалогов в `DialogAiOpsController` и
  `DialogAiOpsService`;
- из `DialogApiController` удалены дублирующие mappings для macro,
  workspace telemetry и AI ops, чтобы controller перестал быть точкой
  концентрации для этих доменных срезов.

## Проверка

- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Эффект

- `DialogApiController` стал тоньше и перестал обслуживать ещё три крупных
  группировки endpoints;
- новые dialog controllers используют общий permission/audit слой вместо
  копирования проверок;
- roadmap и task-detail обновлены с фиксацией следующего пакета выполненных
  этапов рефакторинга.
