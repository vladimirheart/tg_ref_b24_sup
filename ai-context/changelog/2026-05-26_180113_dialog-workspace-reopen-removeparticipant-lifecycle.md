# 2026-05-26 18:01:13 — dialog workspace reopen remove-participant lifecycle

## Что сделано
- `DialogWorkspaceIntegrationTest` расширен live
  `SpringBootTest + SQLite` сценарием, который проходит полный lifecycle
  `reassign -> resolve -> reopen -> removeParticipant` и затем повторно читает
  `/api/dialogs/{ticketId}/workspace`;
- зафиксировано реальное runtime поведение после `reopen`: dialog возвращается
  в `waiting_operator`, а не в абстрактный `open`;
- в том же сценарии закреплены refreshed operator-facing guards и projections:
  `resolve` снова `enabled`, `reopen` уходит в `disabled_reason=not_closed`,
  `participants_add` снова доступен, `participants_remove` возвращает
  `disabled_reason=no_participants`, а candidate lists снова включают ранее
  удалённого оператора;
- `DialogWorkspaceWorkflowSnapshotServiceTest` добран отдельной unit-веткой на
  `no_participants` guard после participant removal.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=DialogWorkspaceControllerWebMvcTest,DialogWorkspaceWorkflowSnapshotServiceTest,DialogWorkspacePayloadAssemblerServiceTest,DialogWorkspaceParityServiceTest,DialogWorkspaceIntegrationTest" test"`

## Дальше
- добрать более глубокий operator action UX/runtime contract вокруг workspace:
  optimistic refresh and notification continuity после `reopen/remove
  participant`, badge/state drift у quick-action consumers и соседние read-side
  projection edges.
