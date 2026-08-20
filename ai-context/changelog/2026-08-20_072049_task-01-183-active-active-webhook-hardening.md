# Changelog

## Summary

- added shared webhook delivery guard with inflight/processed dedup state for `VK` and `MAX`
- extended bot session storage with optimistic compare-and-set save semantics
- hardened `VK` and `MAX` webhook ingress for strict active-active ownership, dedup and bounded session-conflict retry
- restored real local `/webhooks/max` endpoint for relay path with coordinated processing
- added targeted webhook/session coordination tests and synced `01-183` production contour docs

## Files

- `java-bot/bot-core/src/main/java/com/example/supportbot/config/BotIngressCoordinationProperties.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/BotSessionStoreService.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/BotWebhookDeliveryGuardService.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/SessionStateConflictException.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/BotSessionStoreServiceTest.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/BotWebhookDeliveryGuardServiceTest.java`
- `java-bot/bot-vk/pom.xml`
- `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkCallbackController.java`
- `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java`
- `java-bot/bot-vk/src/test/java/com/example/supportbot/vk/VkCallbackControllerTest.java`
- `java-bot/bot-vk/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
- `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java`
- `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxWebhookControllerTest.java`
- `java-bot/bot-max/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`
- `docs/runbooks/postgresql-production-contour.md`
- `ai-context/tasks/task-details/01-183.md`

## Prompt

- `продолжи по задаче 01-183:
Остались strict active-active webhook/session-sharing edge cases для одного канала, deeper worker-specific replay/forensics beyond текущего panel-side history/debug, и более широкий внешний observability/alerting closeout. Следующий логичный пакет можно брать именно в эту сторону.`
- `давай следующий большой шаг. что ещё осталось по задаче?`
- `делай`
