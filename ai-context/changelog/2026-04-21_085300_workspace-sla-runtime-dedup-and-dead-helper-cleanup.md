# 2026-04-21 08:53 MSK — workspace sla runtime dedup and dead helper cleanup

## Что сделано

- добавлен общий `DialogSlaRuntimeService` для SLA lifecycle/runtime логики:
  target/warning parsing, deadline, minutes-left, lifecycle state;
- `DialogWorkspaceService` переведён на `DialogSlaRuntimeService` и дополнительно
  очищен от мёртвых helper-блоков по SLA/source-coverage/export formatting;
- `DialogListReadService` тоже переведён на `DialogSlaRuntimeService`, чтобы не
  держать дублирующий SLA-runtime код внутри списка диалогов;
- добавлены targeted tests:
  - `DialogSlaRuntimeServiceTest`
  - `DialogListReadServiceTest`
- обновлены roadmap, architecture audit и task-detail `01-024`.

## Проверка

- `spring-panel\\mvnw.cmd -q "-Dtest=DialogSlaRuntimeServiceTest,DialogListReadServiceTest,DialogWorkspaceContextContractServiceTest,DialogWorkspaceControllerWebMvcTest,DialogListControllerWebMvcTest" test`
- `spring-panel\\mvnw.cmd -q -DskipTests compile`

## Эффект

- `DialogWorkspaceService` стал ещё тоньше и опустился примерно до ~615 строк;
- SLA-runtime перестал жить в двух местах с одинаковой логикой;
- дальнейший service-level split `dialogs` теперь лучше упирается в `DialogService`,
  а не в локальные дубликаты вокруг workspace/list SLA logic.
