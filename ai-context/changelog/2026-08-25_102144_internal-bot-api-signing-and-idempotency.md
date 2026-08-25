# Internal bot API signing and idempotency

Date: 2026-08-25
Task: 01-195

User prompt:
- "как считаешь, что осталось по проекту чтобы смело его запустить в прод, включая настройки развёртки, безопасности, миграции, общение с внешними сервисами, например по API как приём так и отправку;
да, средние значения по нагрузкам: ежедневно около 3000 обращений и порядка 30 одновременных операторов.

если брать развитие проекта, то какие твои рекомендации?

в целом чего ещё не хватает для мониторинга состояния систем, с которыми классически работает саппорт?"
- "хорошо. создай соответствующие задачи и приступай к их выполнению"
- "делай"
- "бери"

Changes:
- hardened the `spring-panel` internal bot API with centralized token verification, optional HMAC request signing, timestamp skew checks and replay-safe idempotency for write mutations;
- updated bot-side panel clients to sign requests, reuse idempotency keys across retries and expose explicit timeout/retry/signing environment contract through the runtime handoff;
- added integration hardening documentation and linked it from the main project entrypoints, then marked task `01-195` as completed by AI and ready for manual review.

Files and areas:
- `spring-panel/src/main/java/com/example/panel/security/`
- `spring-panel/src/main/java/com/example/panel/controller/`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeContractService.java`
- `spring-panel/src/main/resources/application.yml`
- `spring-panel/src/test/java/com/example/panel/security/InternalBotApiRequestGuardServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/IntegrationPanelApiProperties.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/`
- `java-bot/bot-core/src/main/resources/application-shared.yml`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/PanelApiRequestHeadersFactoryTest.java`
- `docs/integration-contract-hardening.md`
- `docs/environment_variables.md`
- `docs/CURRENT_PROJECT_DOCUMENTATION.md`
- `README.md`
- `ai-context/tasks/task-list.md`

Verification:
- `spring-panel\mvnw.cmd "-Dtest=InternalBotApiRequestGuardServiceTest,BotRuntimeReadApiControllerWebMvcTest,BotRuntimeWriteApiControllerWebMvcTest" test`
- `java-bot\mvnw.cmd -pl bot-core "-Dtest=PanelApiRequestHeadersFactoryTest,BotWebhookDeliveryGuardServiceTest" test`
- `git diff --check`
