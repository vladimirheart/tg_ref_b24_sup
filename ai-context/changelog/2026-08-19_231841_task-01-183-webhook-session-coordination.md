# Changelog

## Summary

- added shared `BotSessionStoreService` for Redis-backed bot webhook/session snapshots with direct-mode fallback
- extended bot coordination settings with job lease and bot session TTL controls
- moved `VK` and `MAX` question-flow sessions off process-local maps to shared session storage
- replaced webhook single-owner `409` gating with shared session-state handling for `VK`/`MAX`
- switched bot-side session expiry/unblock background flows to dedicated distributed job leases
- updated task status and production contour documentation

## Files

- `java-bot/bot-core/src/main/java/com/example/supportbot/config/BotIngressCoordinationProperties.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/BotIngressCoordinationService.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/BotSessionStoreService.java`
- `java-bot/bot-core/src/main/resources/application.yml`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/BotSessionStoreServiceTest.java`
- `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkCallbackController.java`
- `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java`
- `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java`
- `docs/runbooks/postgresql-production-contour.md`
- `docs/POSTGRESQL_FULL_PRODUCTION_GAP_AUDIT.md`
- `docs/environment_variables.md`
- `docs/configuration.md`
- `ai-context/tasks/task-details/01-183.md`

## Prompt

- `хорошо, давай дальше`
