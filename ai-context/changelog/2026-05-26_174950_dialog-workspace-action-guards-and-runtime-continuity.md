# 2026-05-26 17:49:50 — dialog workspace action guards and runtime continuity

## Что сделано
- `DialogWorkspaceWorkflowSnapshotService` расширен action guard-contract:
  `workflow.actions` теперь явно проецирует availability и `disabled_reason`
  для `reply`, `reply_media`, `take`, `resolve`, `reopen`, `reassign`,
  `participants_add` и `participants_remove`;
- `DialogWorkspaceParityService` получил новый
  `operator_action_guards` parity check, чтобы workspace фиксировал не только
  workflow snapshot, но и согласованность quick-action guard semantics;
- `DialogWorkspaceWorkflowSnapshotServiceTest` добран ветками на open/closed
  dialog semantics и permission-denied cases для reassign/participant flows;
- `DialogWorkspaceIntegrationTest` расширен live
  `SpringBootTest + SQLite` continuity сценарием после
  `DialogQuickActionService.reassignTicket(...)` и
  `DialogQuickActionService.resolveTicket(...)`, который проверяет updated
  responsible, closed lifecycle и новые action disabled reasons в реальном
  `/api/dialogs/{ticketId}/workspace` payload;
- `DialogWorkspacePayloadAssemblerServiceTest` и
  `DialogWorkspaceParityServiceTest` синхронизированы с новым `workflow.actions`
  contract.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=DialogWorkspaceControllerWebMvcTest,DialogWorkspaceWorkflowSnapshotServiceTest,DialogWorkspacePayloadAssemblerServiceTest,DialogWorkspaceParityServiceTest,DialogWorkspaceIntegrationTest" test"`

## Дальше
- добрать более глубокий operator action UX parity вокруг `workspace`:
  optimistic refresh/notification edges после `reopen/remove participant`,
  adjacent badge/state projection drift и соседние quick-action consumer
  contracts.
