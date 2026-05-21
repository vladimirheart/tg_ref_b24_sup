# 2026-05-21 09:22:04 — operator notification watcher orchestration hardening

## Что сделано
- Добавлен `OperatorNotificationWatcherTest` с dedicated orchestration coverage для:
  - incoming client message через `AlertQueueService`;
  - fallback на `notifyAllOperators`, если alert route не сработал;
  - initial `public_form_submit` ветки;
  - first-response-overdue fallback на operator audience.
- Исправлен `OperatorNotificationWatcher`:
  - `watchChatHistoryMessages` и `watchFeedbacks` переведены на явный `ResultSetExtractor`, чтобы убрать wrong `JdbcTemplate.query(...)` overload, который мог пропускать строки;
  - `watchFirstResponseOverdue` теперь умеет fallback-ить на operator audience, если `AlertQueueService.notifyFirstResponseOverdue(...)` вернул `false`.
- Audit detail для overdue fallback теперь явно помечает `route=fallback_all_operators`.

## Зачем
- Закрыть следующий runtime focus уже не на helper/service уровне, а на orchestration слое, где встречаются самые дорогие silent regressions.
- Зафиксировать, что watcher не теряет incoming rows из-за callback overload bug.
- Не допускать silent drop overdue alerts, если route-level alert delivery не сработал.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=OperatorNotificationWatcherTest" test`
- `spring-panel\.\mvnw.cmd "-Dtest=OperatorNotificationWatcherTest,AlertQueueServiceTest,NotificationRoutingServiceTest,SupportPanelIntegrationTests#notificationServiceMergesDialogRecipientsFromResponsibleAndActiveSources+notificationServiceFallsBackToOperatorsWhenDialogRecipientsMissing+notificationServiceFindAllOperatorRecipientsFiltersDisabledAndBlockedAcrossDatabases+operatorNotificationWatcherCreatesBellNotificationForFollowUpClientMessage+dialogServiceAggregatesStatsAndHistory+dialogListIncludesTicketsWithoutMessages+publicFormServiceCreatesSessionsAndHistory,NotificationApiIntegrationTest,NotificationApiControllerWebMvcTest,PublicFormFlowSmokeIntegrationTest,PublicFormApiContractServiceTest,PublicFormApiResponseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest" test`

## Дальше
- Следующий logical focus: проверить дублирование alerts между `PublicFormSubmissionPersistenceService`, `AlertQueueService` и `OperatorNotificationWatcher`, а также общий escalation/runtime contract между dialog/public-form/notification слоями.
