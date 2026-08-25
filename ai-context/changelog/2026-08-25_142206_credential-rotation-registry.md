# Credential rotation registry

Date: 2026-08-25
Task: 01-202

User prompts:
- "как считаешь, что осталось по проекту чтобы смело его запустить в прод, включая настройки развёртки, безопасности, миграции, общение с внешними сервисами, например по API как приём так и отправку;
да, средние значения по нагрузкам: ежедневно около 3000 обращений и порядка 30 одновременных операторов.

если брать развитие проекта, то какие твои рекомендации?

в целом чего ещё не хватает для мониторинга состояния систем, с которыми классически работает саппорт?"
- "делай. и сразу создай необходимые задачи по не закрытому контуру задач"
- "давай дальше"
- "продолжай"

Changes:
- added a dedicated credential rotation registry contour in monitoring storage with discovery-backed metadata for expiry and rotation windows;
- implemented runtime/config discovery for shared bot credentials, legacy channel tokens, VK/MAX webhook secrets, NetBox and Notion tokens, dialog external auth tokens, iiko/iikoServer secrets, Bitrix24 webhooks and internal panel API/security secrets;
- introduced status calculation for `healthy`, `tracking_missing`, `expires_soon`, `expired`, `rotation_due_soon`, `rotation_overdue`, `missing_secret` and `source_removed` with `30 / 14 / 7` day warning horizons;
- added analytics API, scheduled refresh, history snapshots and a new analytics page for manual metadata management without exposing raw secret values;
- extended PostgreSQL and SQLite monitoring schema bootstrap for the new registry and covered the contour with service and WebMvc tests;
- created follow-up tasks `01-204` and `01-205` for proactive alerting/escalation and for expanding the registry to network-route secrets plus external secret-backend metadata import.

Files and areas:
- `spring-panel/src/main/java/com/example/panel/service/CredentialRotationRegistryService.java`
- `spring-panel/src/main/java/com/example/panel/service/CredentialRotationRegistryScheduler.java`
- `spring-panel/src/main/java/com/example/panel/controller/CredentialRotationRegistryApiController.java`
- `spring-panel/src/main/java/com/example/panel/entity/CredentialRotationRegistryEntry.java`
- `spring-panel/src/main/java/com/example/panel/repository/CredentialRotationRegistryRepository.java`
- `spring-panel/src/main/java/com/example/panel/controller/AnalyticsController.java`
- `spring-panel/src/main/java/com/example/panel/service/MonitoringDatabaseBootstrapService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V38__credential_rotation_registry.sql`
- `spring-panel/src/main/resources/templates/analytics/credential-rotation.html`
- `spring-panel/src/main/resources/static/js/credential-rotation-monitoring.js`
- `spring-panel/src/main/resources/templates/analytics/index.html`
- `spring-panel/src/test/java/com/example/panel/service/CredentialRotationRegistryServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/AnalyticsControllerWebMvcTest.java`
- `ai-context/tasks/task-details/01-202.md`
- `ai-context/tasks/task-list.md`

Verification:
- `.\mvnw.cmd -q "-Dtest=CredentialRotationRegistryServiceTest,AnalyticsControllerWebMvcTest" test`
- `git diff --check`
