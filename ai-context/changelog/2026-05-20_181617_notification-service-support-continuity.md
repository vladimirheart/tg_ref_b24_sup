# 2026-05-20 18:16:17 — notification service support continuity

## Что сделано
- Добавлены новые integration-сценарии в `SupportPanelIntegrationTests` для `NotificationService`:
  - merge recipient pool из `ticket_responsibles` и `ticket_active`;
  - fallback на operator audience, если у dialog нет participant recipients;
  - filtering operator recipient pool по `enabled` и optional `is_blocked` across main/users SQLite.
- Тестовый helper `insertOperatorUser(...)` сделан schema-aware, чтобы integration-набор не зависел от жёсткого предположения о наличии `is_blocked` в каждом `users` schema.
- В `DialogLookupReadService` снят `JdbcTemplate.query(...)` ambiguous compile-blocker для responsible profile enrichment через явный `RowCallbackHandler`.

## Зачем
- Перевести notification layer из controller/API coverage в service/runtime continuity coverage.
- Зафиксировать живой contract для dialog participant routing и operator fallback в том же integration-контексте, где уже живут `public-form` и `dialogs`.
- Убрать соседний compile/runtime blocker, который мешал проходить dialog list/details integration сценариям после расширения coverage.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=SupportPanelIntegrationTests#notificationServiceMergesDialogRecipientsFromResponsibleAndActiveSources+notificationServiceFallsBackToOperatorsWhenDialogRecipientsMissing+notificationServiceFindAllOperatorRecipientsFiltersDisabledAndBlockedAcrossDatabases+operatorNotificationWatcherCreatesBellNotificationForFollowUpClientMessage+dialogServiceAggregatesStatsAndHistory+dialogListIncludesTicketsWithoutMessages+publicFormServiceCreatesSessionsAndHistory" test`
- `spring-panel\.\mvnw.cmd "-Dtest=SupportPanelIntegrationTests#notificationServiceMergesDialogRecipientsFromResponsibleAndActiveSources+notificationServiceFallsBackToOperatorsWhenDialogRecipientsMissing+notificationServiceFindAllOperatorRecipientsFiltersDisabledAndBlockedAcrossDatabases+operatorNotificationWatcherCreatesBellNotificationForFollowUpClientMessage+dialogServiceAggregatesStatsAndHistory+dialogListIncludesTicketsWithoutMessages+publicFormServiceCreatesSessionsAndHistory,NotificationApiIntegrationTest,NotificationApiControllerWebMvcTest,PublicFormFlowSmokeIntegrationTest,PublicFormApiContractServiceTest,PublicFormApiResponseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest" test`

## Дальше
- Следующий practical focus: `NotificationRoutingService` / `AlertQueueService` continuity, escalation audience selection и related runtime coverage.
