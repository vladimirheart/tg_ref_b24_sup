# Public ingress monitoring

Date: 2026-08-25
Task: 01-199

User prompts:
- "как считаешь, что осталось по проекту чтобы смело его запустить в прод, включая настройки развёртки, безопасности, миграции, общение с внешними сервисами, например по API как приём так и отправку;
да, средние значения по нагрузкам: ежедневно около 3000 обращений и порядка 30 одновременных операторов.

если брать развитие проекта, то какие твои рекомендации?

в целом чего ещё не хватает для мониторинга состояния систем, с которыми классически работает саппорт?"
- "делай. и сразу создай необходимые задачи по не закрытому контуру задач"
- "забирай следующий"

Changes:
- added a dedicated public ingress monitoring contour for DNS resolution, HTTP reachability and TLS certificate health of callback/public endpoints;
- introduced persistence, scheduler and aggregate availability logic for `public_ingress_monitors`, including timeline history in `monitoring_check_history`;
- added a new analytics page with CRUD, manual refresh and history modal, plus linked it from the analytics index;
- covered the new contour with service tests, analytics WebMvc route checks and SQLite bootstrap schema assertions, then marked task `01-199` as completed by AI.

Files and areas:
- `spring-panel/src/main/java/com/example/panel/entity/PublicIngressMonitor.java`
- `spring-panel/src/main/java/com/example/panel/repository/PublicIngressMonitorRepository.java`
- `spring-panel/src/main/java/com/example/panel/service/PublicIngressMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/service/PublicIngressMonitoringScheduler.java`
- `spring-panel/src/main/java/com/example/panel/controller/PublicIngressMonitoringApiController.java`
- `spring-panel/src/main/java/com/example/panel/controller/AnalyticsController.java`
- `spring-panel/src/main/java/com/example/panel/service/MonitoringDatabaseBootstrapService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V36__public_ingress_monitors.sql`
- `spring-panel/src/main/resources/templates/analytics/public-ingress.html`
- `spring-panel/src/main/resources/templates/analytics/index.html`
- `spring-panel/src/main/resources/static/js/public-ingress-monitoring.js`
- `spring-panel/src/test/java/com/example/panel/service/PublicIngressMonitoringServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/MonitoringDatabaseBootstrapServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/AnalyticsControllerWebMvcTest.java`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-199.md`

Verification:
- `.\mvnw.cmd -q "-Dtest=PublicIngressMonitoringServiceTest,MonitoringDatabaseBootstrapServiceTest,AnalyticsControllerWebMvcTest" test`
- `git diff --check` (only CRLF warnings in existing working copy files, no patch-format errors)
