# 2026-05-21 09:42:24 — public-form initial alert dedup

## Что сделано
- Исправлен `PublicFormSubmissionPersistenceService`:
  - `notifyQueueForNewPublicAppeal(...)` теперь возвращает `boolean`, чтобы submit path знал, была ли реальная queue-delivery;
  - после initial submit пишется отдельный audit action `public_form_new_appeal_notification` со статусом `success` или `skipped` и detail вида `route=alert_queue|no_recipients`.
- Исправлен `OperatorNotificationWatcher`:
  - initial `public_form_submit` ветка теперь сначала проверяет successful `public_form_new_appeal_notification`;
  - если alert уже был доставлен на submit path, watcher не шлёт второй `notifyAllOperators(...)`;
  - если queue route не сработал, legacy fallback остаётся активным, и watcher всё ещё может поднять initial notification сам.
- Добавлены и расширены проверки:
  - `PublicFormSubmissionPersistenceServiceTest` фиксирует второй audit write для queue-delivery;
  - `OperatorNotificationWatcherTest` проверяет both branches: fallback path и duplicate-suppression path;
  - `SupportPanelIntegrationTests` закрепляет live SQLite сценарий без дублей initial `"Новое обращение ..."` после прохода watcher.

## Зачем
- Убрать двойные operator notifications на первом `public-form` сообщении, когда и submit path, и watcher считали себя владельцами одного и того же alert.
- Сохранить старую страховку для случаев, где route-level audience пустой и queue notification фактически не была отправлена.
- Сделать suppression decision наблюдаемым через `dialog_action_audit`, а не через неявное поведение.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=PublicFormSubmissionPersistenceServiceTest,OperatorNotificationWatcherTest,SupportPanelIntegrationTests#publicFormServiceCreatesSessionsAndHistory+publicFormInitialSubmitDoesNotDuplicateNewAppealNotificationsAfterWatcherPass+operatorNotificationWatcherCreatesBellNotificationForFollowUpClientMessage" test`
- `spring-panel\.\mvnw.cmd "-Dtest=OperatorNotificationWatcherTest,AlertQueueServiceTest,NotificationRoutingServiceTest,PublicFormSubmissionPersistenceServiceTest,SupportPanelIntegrationTests#notificationServiceMergesDialogRecipientsFromResponsibleAndActiveSources+notificationServiceFallsBackToOperatorsWhenDialogRecipientsMissing+notificationServiceFindAllOperatorRecipientsFiltersDisabledAndBlockedAcrossDatabases+operatorNotificationWatcherCreatesBellNotificationForFollowUpClientMessage+publicFormServiceCreatesSessionsAndHistory+publicFormInitialSubmitDoesNotDuplicateNewAppealNotificationsAfterWatcherPass,NotificationApiIntegrationTest,NotificationApiControllerWebMvcTest,PublicFormFlowSmokeIntegrationTest,PublicFormApiContractServiceTest,PublicFormApiResponseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest" test`

## Дальше
- Следующий logical focus: добрать более широкий escalation/runtime contract уже вокруг `DialogAiAssistantService` и operator-facing notification continuity, не возвращая hidden duplication между submit/orchestration слоями.
