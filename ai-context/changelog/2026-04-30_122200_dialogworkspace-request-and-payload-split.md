# 2026-04-30 12:22:00 — DialogWorkspace request/payload split

## Что сделано

- из `DialogWorkspaceService` вынесены request-contract normalization и final
  payload assembly в отдельные
  `DialogWorkspaceRequestContractService` и
  `DialogWorkspacePayloadAssemblerService`;
- `DialogWorkspaceService` переведён на новые bounded dependencies и больше не
  держит локальные include/limit/cursor/config helper'ы и финальный
  payload-builder;
- под новый split добавлены targeted tests
  `DialogWorkspaceRequestContractServiceTest` и
  `DialogWorkspacePayloadAssemblerServiceTest`;
- `01-024`, roadmap и architecture audit синхронизированы под новый baseline:
  `DialogWorkspaceService` сжат примерно до `327` строк, а следующий фокус
  смещён в remaining reply/notifier/write-side bounded contexts вокруг
  workspace consumers.

## Проверка

- `.\mvnw.cmd -q "-Dtest=DialogWorkspaceRequestContractServiceTest,DialogWorkspacePayloadAssemblerServiceTest,DialogWorkspaceControllerWebMvcTest,DialogWorkspaceParityServiceTest,DialogServiceTest" test`
- `.\mvnw.cmd -q -DskipTests compile`

## Заметки

- `logs/spring-panel.log` обновился от локальных Maven-прогонов и вручную не
  редактировался.
