# 2026-05-21 08:43:43 — alert queue and notification routing hardening

## Что сделано
- Исправлен `AlertQueueService`:
  - incoming client alert text больше не приходит в mojibake-виде;
  - online recipient filtering теперь понимает локальные SQLite timestamps (`yyyy-MM-dd HH:mm:ss`), а не только `OffsetDateTime`;
  - schema inspection для `users` приведён к lower-case column matching.
- Исправлен `NotificationRoutingService`:
  - `mergeUsers(...)` переведён на явный `RowCallbackHandler`, чтобы убрать `JdbcTemplate.query(...)` ambiguous compile/runtime blocker;
  - `PRAGMA table_info(users)` теперь тоже нормализуется к lower-case column names.
- Добавлены dedicated service tests:
  - `AlertQueueServiceTest` на `incomingClientMessage`, `firstResponseOverdue`, legacy `alertQueue` и `online_only_fallback_all`;
  - `NotificationRoutingServiceTest` на `employees_only`, `all_operators`, cross-database recipient merge и local timestamp online filtering.

## Зачем
- Закрыть следующий post-notification continuity focus не только через controller/API или support smoke, но и через отдельный alert/routing service layer.
- Зафиксировать живой contract для delivery-mode и audience-selection веток, где чаще всего возникают тихие regressions.
- Снять второй ambiguous `JdbcTemplate.query(...)` хвост в notification-adjacent runtime до того, как он снова всплывёт в integration-наборах.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=AlertQueueServiceTest,NotificationRoutingServiceTest" test`
- `spring-panel\.\mvnw.cmd "-Dtest=AlertQueueServiceTest,NotificationRoutingServiceTest,SupportPanelIntegrationTests#notificationServiceMergesDialogRecipientsFromResponsibleAndActiveSources+notificationServiceFallsBackToOperatorsWhenDialogRecipientsMissing+notificationServiceFindAllOperatorRecipientsFiltersDisabledAndBlockedAcrossDatabases+operatorNotificationWatcherCreatesBellNotificationForFollowUpClientMessage+dialogServiceAggregatesStatsAndHistory+dialogListIncludesTicketsWithoutMessages+publicFormServiceCreatesSessionsAndHistory,NotificationApiIntegrationTest,NotificationApiControllerWebMvcTest,PublicFormFlowSmokeIntegrationTest,PublicFormApiContractServiceTest,PublicFormApiResponseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest" test`

## Дальше
- Следующий logical focus: `OperatorNotificationWatcher` / escalation audience continuity, а также более широкий runtime orchestration contract между `AlertQueueService`, `NotificationRoutingService` и dialog/public-form lifecycle.
