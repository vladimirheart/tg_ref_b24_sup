# SMTP notification monitoring

Date: 2026-08-25
Task: 01-200

User prompts:
- "как считаешь, что осталось по проекту чтобы смело его запустить в прод, включая настройки развёртки, безопасности, миграции, общение с внешними сервисами, например по API как приём так и отправку;
да, средние значения по нагрузкам: ежедневно около 3000 обращений и порядка 30 одновременных операторов.

если брать развитие проекта, то какие твои рекомендации?

в целом чего ещё не хватает для мониторинга состояния систем, с которыми классически работает саппорт?"
- "делай. и сразу создай необходимые задачи по не закрытому контуру задач"
- "забирай следующий"
- "давай дальше"

Changes:
- added a dedicated SMTP relay monitoring contour with persisted monitor configuration, scheduler-based refresh and timeline history in `monitoring_check_history`;
- implemented protocol-aware relay probes for `plain`, `starttls` and `tls` modes, including SMTP banner capture and TLS metadata snapshotting;
- added live incident notification delivery health over `incident_route_delivery_outbox`, with backlog/failure aggregates, route-type breakdown and recent failed delivery visibility;
- introduced a new analytics page for SMTP/notification health, linked it from the analytics index and covered the contour with service tests, analytics route checks and SQLite bootstrap assertions;
- marked task `01-200` as completed by AI.

Files and areas:
- `spring-panel/src/main/java/com/example/panel/entity/SmtpNotificationMonitor.java`
- `spring-panel/src/main/java/com/example/panel/repository/SmtpNotificationMonitorRepository.java`
- `spring-panel/src/main/java/com/example/panel/service/SmtpNotificationMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/service/SmtpNotificationMonitoringScheduler.java`
- `spring-panel/src/main/java/com/example/panel/service/IncidentNotificationRouteHealthService.java`
- `spring-panel/src/main/java/com/example/panel/controller/SmtpNotificationMonitoringApiController.java`
- `spring-panel/src/main/java/com/example/panel/controller/AnalyticsController.java`
- `spring-panel/src/main/java/com/example/panel/service/MonitoringDatabaseBootstrapService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V37__smtp_notification_monitors.sql`
- `spring-panel/src/main/resources/templates/analytics/smtp-notifications.html`
- `spring-panel/src/main/resources/templates/analytics/index.html`
- `spring-panel/src/main/resources/static/js/smtp-notification-monitoring.js`
- `spring-panel/src/test/java/com/example/panel/service/SmtpNotificationMonitoringServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/MonitoringDatabaseBootstrapServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/AnalyticsControllerWebMvcTest.java`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-200.md`

Verification:
- `.\mvnw.cmd -q "-Dtest=SmtpNotificationMonitoringServiceTest,MonitoringDatabaseBootstrapServiceTest,AnalyticsControllerWebMvcTest" test`
