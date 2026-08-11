# 2026-08-11 17:28:00 - users schema metadata cleanup

## Request
давай следующий шаг

## Summary
- заменил active schema introspection для `users` и других table-column checks в панели с SQLite `PRAGMA table_info(...)` на JDBC metadata;
- добавил общий helper `JdbcSchemaInspector`, чтобы panel-сервисы и контроллеры одинаково читали колонки как в SQLite, так и в PostgreSQL;
- перевёл на новый helper `AuthManagementApiController`, `AlertQueueService`, `DialogParticipantService`, `NotificationService`, `NotificationRoutingService` и `DialogLookupReadService`.

## Files Changed
- `spring-panel/src/main/java/com/example/panel/support/JdbcSchemaInspector.java`
- `spring-panel/src/main/java/com/example/panel/controller/AuthManagementApiController.java`
- `spring-panel/src/main/java/com/example/panel/service/AlertQueueService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogParticipantService.java`
- `spring-panel/src/main/java/com/example/panel/service/NotificationService.java`
- `spring-panel/src/main/java/com/example/panel/service/NotificationRoutingService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogLookupReadService.java`
