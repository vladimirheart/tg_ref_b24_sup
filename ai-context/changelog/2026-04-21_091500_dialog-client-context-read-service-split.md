# 2026-04-21 09:15 MSK — dialog client context read service split

## Что сделано

- добавлен `DialogClientContextReadService`, который забрал из giant
  `DialogService` read-сценарии клиентского контекста:
  `loadClientDialogHistory`, `loadClientProfileEnrichment`,
  `loadDialogProfileMatchCandidates`, `loadRelatedEvents`;
- `DialogService` оставлен совместимым для legacy вызовов через thin delegates,
  чтобы не ломать старые controller/integration tests и внешний контракт;
- `DialogWorkspaceService` переведён на новый слой для истории клиента,
  related events, profile enrichment и profile match candidates;
- `DialogMacroService` переведён на новый слой для macro-variable enrichment по
  клиентскому профилю;
- из `DialogService` удалены локальные helper'ы, которые уже не нужны после
  выноса client-context read-layer;
- добавлен targeted integration-style test `DialogClientContextReadServiceTest`
  на sqlite-диалекте;
- обновлены roadmap, architecture audit и task-detail `01-024`.

## Проверка

- `spring-panel\\mvnw.cmd -q "-Dtest=DialogClientContextReadServiceTest,DialogWorkspaceControllerWebMvcTest,DialogMacroControllerWebMvcTest,DialogSlaRuntimeServiceTest,DialogListReadServiceTest" test`
- `spring-panel\\mvnw.cmd -q -DskipTests compile`

## Эффект

- giant `DialogService` начал реальный service-level split не только на уровне
  controllers, но и по внутреннему read-домену клиентского контекста;
- `workspace` и `macro` перестали тянуть этот контекст через монолитный service;
- следующий пакет по `dialogs` теперь можно брать уже поверх выделенного
  `DialogClientContextReadService`, а не через ещё одно разрастание `DialogService`.
