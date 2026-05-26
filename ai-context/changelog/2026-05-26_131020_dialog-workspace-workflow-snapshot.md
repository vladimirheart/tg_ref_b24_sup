# 2026-05-26 13:10:20 — dialog workspace workflow snapshot

## Что сделано
- добавлен `DialogWorkspaceWorkflowSnapshotService`, который собирает
  operator-facing workflow snapshot для `/api/dialogs/{ticketId}/workspace`
  из responsibility/participants/operator-directory/triage-preferences
  bounded слоёв;
- в `workspace` payload добавлен новый top-level блок `workflow` с:
  `responsible`, `participants`, `reassign_candidates`,
  `participant_candidates`, `triage_preferences` и `collaboration`;
- `DialogWorkspaceParityService` расширен explicit
  `operator_workflow_projection` check, чтобы workflow snapshot был частью
  runtime parity contract, а не скрытым UI convenience helper;
- `DialogWorkspaceIntegrationTest` расширен live
  `SpringBootTest + SQLite` сценарием на collaboration/triage workflow
  contract, включая schema-aware users fixture;
- обновлены `DialogWorkspacePayloadAssemblerServiceTest`,
  `DialogWorkspaceParityServiceTest` и добавлен
  `DialogWorkspaceWorkflowSnapshotServiceTest`.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=DialogWorkspaceWorkflowSnapshotServiceTest,DialogWorkspacePayloadAssemblerServiceTest,DialogWorkspaceParityServiceTest,DialogWorkspaceIntegrationTest" test`
- `spring-panel\.\mvnw.cmd "-Dtest=DialogWorkspaceControllerWebMvcTest,DialogWorkspaceWorkflowSnapshotServiceTest,DialogWorkspacePayloadAssemblerServiceTest,DialogWorkspaceParityServiceTest,DialogWorkspaceIntegrationTest" test`

## Дальше
- добрать `quick-action/runtime continuity` поверх нового workflow snapshot,
  чтобы `/workspace` и `DialogQuickActionService` были синхронны не только по
  read-model projection, но и по реальным participant/reassign side-effects.
