# 2026-06-30 17:57:09 — dialogs regression pass

## Prompt

`сделай тогда: Следующий рациональный шаг уже не очередной вынос, а финальный regression pass по ключевым сценариям страницы диалогов`

## Что сделано

- Зафиксирован итоговый regression pass по ключевым controller/integration
  сценариям страницы диалогов в `ai-context/tasks/task-details/01-130.md`.
- Обновлён roadmap `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`, чтобы
  следующий проход шёл уже не по новым helper-split, а по трём конкретным
  live regressions continuity.
- Отдельно отмечено, что `DialogsControllerWebMvcTest` остаётся красным из-за
  server-side navbar Thymeleaf/model contract, а не из-за client-side split
  `dialogs.js`.

## Regression pass summary

- Green:
  `DialogListControllerWebMvcTest`,
  `DialogTriagePreferencesControllerWebMvcTest`,
  `DialogReadControllerWebMvcTest`,
  `DialogWorkspaceControllerWebMvcTest`,
  `DialogQuickActionsControllerWebMvcTest`,
  `DialogAiOpsControllerWebMvcTest`,
  `DialogListIntegrationTest`,
  `DialogReadIntegrationTest`.
- Red:
  `DialogDetailsIntegrationTest.notificationReadAllRereadStillAllowsNextDetailsFollowUpToRearmUnreadAndBell`
  (`$.unread = 3` вместо `1`);
  `DialogWorkspaceIntegrationTest.workspaceApiPreservesTakeWorkflowAndUnreadRearmAcrossReplyAckAndNextFollowUp`
  (`conversation.statusKey = auto_processing` вместо `waiting_operator`);
  `DialogQuickActionsIntegrationTest.quickActionsApiLifecycleActionsNotifyPeerParticipantsThroughNotificationApi`
  (`5` уведомлений вместо `4`);
  `DialogQuickActionsIntegrationTest.quickActionsApiSpamNotifiesPeerParticipantsThroughNotificationApi`
  (`2` уведомления вместо `1`).
