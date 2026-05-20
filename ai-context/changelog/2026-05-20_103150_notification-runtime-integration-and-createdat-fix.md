# 2026-05-20 10:31:50 - Notification runtime integration and createdAt fix

## Промт пользователя

- `хорошо, давай дальше более широким пакетом`

## Что сделано

- Добавлен live
  `spring-panel/src/test/java/com/example/panel/controller/NotificationApiIntegrationTest.java`
  на `SpringBootTest + SQLite`.
- В integration-пакете закреплены реальные `NotificationApiController`
  ветки для `list`, `unread_count`, `markAsRead`, identity scope между
  пользователями, anonymous `all` fallback и runtime bridge от
  `NotificationService.notifyUsersExcluding`.
- В `spring-panel/src/main/java/com/example/panel/entity/Notification.java`
  `createdAt` переведён на `LenientOffsetDateTimeConverter`, чтобы
  notification rows, созданные через JPA на SQLite, стабильно читались
  обратно в `/api/notifications` вместо `created_at` parse-failure.
- В `spring-panel/src/main/java/com/example/panel/service/NotificationService.java`
  сняты ambiguous `JdbcTemplate.query(...)` ветки через явный
  `RowCallbackHandler`, что убрало runtime/compile-path сбой в
  `findDialogRecipients` и `findAllOperatorRecipients`.
- В
  `spring-panel/src/test/java/com/example/panel/controller/PublicFormFlowSmokeIntegrationTest.java`
  обновлён participant-notification lifecycle сценарий: peer participant
  теперь явно восстанавливается перед ручной проверкой `resolve/reopen`
  routing, чтобы smoke контракт оставался согласован с текущим runtime.
- Актуализированы `docs/ARCHITECTURE_AUDIT_2026-04-08.md` и
  `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`.

## Проверка

- `spring-panel\.\mvnw.cmd "-Dtest=NotificationApiIntegrationTest" test`
- `spring-panel\.\mvnw.cmd "-Dtest=NotificationApiIntegrationTest,NotificationApiControllerWebMvcTest,PublicFormFlowSmokeIntegrationTest,PublicFormApiContractServiceTest,PublicFormApiResponseServiceTest,PublicFormApiControllerWebMvcTest,PublicFormControllerWebMvcTest" test`

## Что дальше

- Следующим notification/runtime пакетом можно уже добирать `findDialogRecipients`
  live continuity через `DialogQuickActionService`/`NotificationRoutingService`
  без ручного SQL восстановления participants, либо идти в adjacent operator
  bell metrics/read-model contract поверх уже выровненного persistence слоя.
