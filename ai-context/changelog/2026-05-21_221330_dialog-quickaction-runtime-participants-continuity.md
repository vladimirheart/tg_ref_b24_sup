# 2026-05-21 22:13:30 — dialog quickaction runtime participants continuity

## Что сделано
- Расширен `SupportPanelIntegrationTests` двумя live SQLite сценариями:
  - `dialogQuickActionServiceManagesParticipantsAndReassignsResponsibleWithRuntimeContinuity`
  - `dialogQuickActionServiceRemovesParticipantAndNotifiesRemainingAudience`
- В них зафиксированы реальные side-effects для quick-action веток
  `addParticipant`, `removeParticipant` и `reassignTicket`:
  - изменение `ticket_participants`;
  - обновление `ticket_responsibles`;
  - projection через `DialogReadService.loadParticipants(...)`;
  - continuity уведомлений через `NotificationService`.
- `clean()` в integration-наборе синхронизирован с
  `ticket_participants`, `ticket_active` и `ticket_responsibles`, чтобы
  repeated прогон не оставлял runtime residue между сценариями.

## Зачем
- Перевести quick-action participant/reassign слой из unit/WebMvc-only
  покрытия в живой runtime contract на реальном SQLite orchestration.
- Зафиксировать audience continuity для bell notifications после add/remove
  participant и reassign, где участвуют сразу responsibility, participants и
  dialog recipients.
- Убрать ещё один класс regressions, которые сложно поймать только через
  mocked service/controller tests.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=SupportPanelIntegrationTests#dialogQuickActionServiceManagesParticipantsAndReassignsResponsibleWithRuntimeContinuity+dialogQuickActionServiceRemovesParticipantAndNotifiesRemainingAudience" test`
- `spring-panel\.\mvnw.cmd "-Dtest=SupportPanelIntegrationTests#dialogQuickActionServiceManagesParticipantsAndReassignsResponsibleWithRuntimeContinuity+dialogQuickActionServiceRemovesParticipantAndNotifiesRemainingAudience,DialogQuickActionServiceTest,DialogQuickActionsControllerWebMvcTest" test`

## Дальше
- Следующий logical focus: live runtime continuity для remaining quick-action
  side-effects вокруг dialog history mutation и adjacent notification/read
  bridges, а не дальнейшее расширение unit/controller coverage.
