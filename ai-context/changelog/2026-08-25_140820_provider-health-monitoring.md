# Provider health monitoring

Date: 2026-08-25
Task: 01-201

User prompts:
- "как считаешь, что осталось по проекту чтобы смело его запустить в прод, включая настройки развёртки, безопасности, миграции, общение с внешними сервисами, например по API как приём так и отправку;
да, средние значения по нагрузкам: ежедневно около 3000 обращений и порядка 30 одновременных операторов.

если брать развитие проекта, то какие твои рекомендации?

в целом чего ещё не хватает для мониторинга состояния систем, с которыми классически работает саппорт?"
- "делай. и сразу создай необходимые задачи по не закрытому контуру задач"
- "забирай следующий"
- "давай дальше"

Changes:
- added a unified provider health backend for `telegram`, `vk` and `max` channels without introducing a new monitor registry, using `channels` as the source of truth;
- implemented provider-level probes for `Telegram getMe`, `VK groups.getById` and `MAX /updates`, combined with live bot runtime status from `BotProcessService`;
- aggregated real inbound and outbound activity from `chat_history`, including `last_inbound_at`, `last_outbound_at`, `inbound_24h` and `outbound_24h`;
- persisted refresh snapshots into `monitoring_check_history` under `provider_health`, added manual refresh endpoints, history loading and a scheduled refresh contour with runtime lease coordination;
- added a new analytics page for provider health, linked it from the analytics index and covered the new contour with service and WebMvc tests;
- created follow-up task `01-203` for the still-missing persisted provider delivery ledger and direct outbound error classification.

Files and areas:
- `spring-panel/src/main/java/com/example/panel/service/ProviderHealthMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/service/ProviderHealthMonitoringScheduler.java`
- `spring-panel/src/main/java/com/example/panel/controller/ProviderHealthMonitoringApiController.java`
- `spring-panel/src/main/java/com/example/panel/controller/AnalyticsController.java`
- `spring-panel/src/main/resources/templates/analytics/provider-health.html`
- `spring-panel/src/main/resources/templates/analytics/index.html`
- `spring-panel/src/main/resources/static/js/provider-health-monitoring.js`
- `spring-panel/src/test/java/com/example/panel/service/ProviderHealthMonitoringServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/AnalyticsControllerWebMvcTest.java`
- `ai-context/tasks/task-details/01-201.md`
- `ai-context/tasks/task-list.md`

Verification:
- `.\mvnw.cmd -q "-Dtest=ProviderHealthMonitoringServiceTest,AnalyticsControllerWebMvcTest" test`
- `git diff --check`
