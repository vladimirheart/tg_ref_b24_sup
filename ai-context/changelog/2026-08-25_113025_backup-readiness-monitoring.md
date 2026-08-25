# Backup readiness monitoring

Date: 2026-08-25
Task: 01-198

User prompts:
- "как считаешь, что осталось по проекту чтобы смело его запустить в прод, включая настройки развёртки, безопасности, миграции, общение с внешними сервисами, например по API как приём так и отправку;
да, средние значения по нагрузкам: ежедневно около 3000 обращений и порядка 30 одновременных операторов.

если брать развитие проекта, то какие твои рекомендации?

в целом чего ещё не хватает для мониторинга состояния систем, с которыми классически работает саппорт?"
- "хорошо. создай соответствующие задачи и приступай к их выполнению"
- "делай"
- "дальше"
- "что дальше? докумнтацию-то создал, а работа по ней?"
- "делай. и сразу создай необходимые задачи по не закрытому контуру задач"

Changes:
- created a real backup readiness monitoring contour for Iguana analytics with CRUD/API, periodic refresh, backup artifact probing and manual restore evidence confirmation;
- added persistence and schema support for `backup_readiness_monitors` in both PostgreSQL Flyway migrations and SQLite monitoring bootstrap;
- added a dedicated analytics screen with overview cards, table actions, restore evidence modal and monitoring timeline for recent checks;
- covered the new contour with service tests, WebMvc route coverage and bootstrap schema assertions, then marked task `01-198` as completed by AI.

Files and areas:
- `spring-panel/src/main/java/com/example/panel/entity/BackupReadinessMonitor.java`
- `spring-panel/src/main/java/com/example/panel/repository/BackupReadinessMonitorRepository.java`
- `spring-panel/src/main/java/com/example/panel/service/BackupReadinessMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/service/BackupReadinessMonitoringScheduler.java`
- `spring-panel/src/main/java/com/example/panel/controller/BackupReadinessMonitoringApiController.java`
- `spring-panel/src/main/java/com/example/panel/controller/AnalyticsController.java`
- `spring-panel/src/main/java/com/example/panel/service/MonitoringDatabaseBootstrapService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V35__backup_readiness_monitors.sql`
- `spring-panel/src/main/resources/templates/analytics/backup-readiness.html`
- `spring-panel/src/main/resources/templates/analytics/index.html`
- `spring-panel/src/main/resources/static/js/backup-readiness-monitoring.js`
- `spring-panel/src/test/java/com/example/panel/service/BackupReadinessMonitoringServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/MonitoringDatabaseBootstrapServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/AnalyticsControllerWebMvcTest.java`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-198.md`

Verification:
- `.\mvnw.cmd -q "-Dtest=BackupReadinessMonitoringServiceTest,MonitoringDatabaseBootstrapServiceTest,AnalyticsControllerWebMvcTest" test`
- `git diff --check` (only CRLF warnings in existing working copy files, no patch-format errors)
