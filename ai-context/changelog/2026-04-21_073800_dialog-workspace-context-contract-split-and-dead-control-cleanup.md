# 2026-04-21 07:38 MSK — dialog workspace context contract split and dead control cleanup

## Что сделано

- вынесён `workspace context contract` из giant `DialogWorkspaceService` в
  отдельный `DialogWorkspaceContextContractService`;
- `DialogWorkspaceService` переведён на делегирование нового contract-сервиса;
- удалён мёртвый дублирующий блок review-controls из `DialogWorkspaceService`,
  который уже жил в `DialogWorkspaceTelemetryService` и не использовался в
  workspace flow;
- добавлен targeted unit-test `DialogWorkspaceContextContractServiceTest`;
- синхронизированы roadmap, architecture audit и task-detail `01-024`.

## Проверка

- `spring-panel\\mvnw.cmd -q "-Dtest=DialogWorkspaceContextContractServiceTest,DialogWorkspaceControllerWebMvcTest" test`
- `spring-panel\\mvnw.cmd -q -DskipTests compile`

## Эффект

- giant `DialogWorkspaceService` стал ещё тоньше и ближе к orchestration-only
  роли;
- правила `context contract` теперь живут в отдельном сервисе с адресной
  safety net, а не внутри общего workspace-комбайна;
- из workspace-слоя убран лишний мёртвый код, который повышал шум при ревью и
  мешал следующему service-level split.
