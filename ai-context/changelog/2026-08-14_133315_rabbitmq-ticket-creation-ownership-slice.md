# 2026-08-14 13:33:15 - rabbitmq ticket creation ownership slice

## Промпт пользователя
- `отлично. давай дальше`

## Что сделано
- в `java-bot/bot-core` добавлены transport properties и отдельный RabbitMQ publisher для события `ticket.created.initial_contact`;
- `TicketService` получил новый gateway `createConversationTicket(...)`: в `rabbitmq`-режиме он публикует backend-owned событие создания тикета, а в `jdbc`-режиме сохраняет текущий direct write fallback;
- финализация conversation flow переведена на новый gateway в `Telegram`, `VK` и `MAX`, включая перенос initial history entries, attachment paths и собранных answer/attribute данных;
- в `spring-panel` inbox-сервис обобщён под несколько inbound event kinds, после чего добавлены listener и ingestion service для `ticket.created.initial_contact`;
- новый panel-side ingestion path сам создаёт `Message`, `Ticket`, `TicketSpan`, `TicketActive`, `ticket_attributes` и стартовый `ChatHistory`, то есть initial business ownership уходит за backend boundary;
- обновлены targeted tests для обоих ownership slices: `TicketServiceInboundTransportTest`, `InboundClientMessageIngestionServiceTest`, `ConversationTicketCreationIngestionServiceTest`.

## Затронутые файлы
- `java-bot/bot-core/src/main/resources/application.yml`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/IntegrationRabbitProperties.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/TicketService.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/ConversationHistoryEntry.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/ConversationTicketCreationCommand.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/ConversationTicketCreatedEvent.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/ConversationTicketCreatedPublisher.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/TicketServiceInboundTransportTest.java`
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
- `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java`
- `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java`
- `spring-panel/src/main/resources/application.yml`
- `spring-panel/src/main/java/com/example/panel/config/IntegrationRabbitProperties.java`
- `spring-panel/src/main/java/com/example/panel/config/RabbitIntegrationTransportConfig.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/IntegrationInboundEventInboxService.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/InboundClientMessageIngestionService.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/ConversationTicketCreatedEvent.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/ConversationTicketCreatedListener.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/ConversationTicketCreationIngestionService.java`
- `spring-panel/src/test/java/com/example/panel/service/integration/InboundClientMessageIngestionServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/integration/ConversationTicketCreationIngestionServiceTest.java`
- `ai-context/tasks/task-details/01-181.md`

## Проверка
- `cmd /c "mvnw.cmd -q -pl bot-core,bot-telegram,bot-vk,bot-max -DskipTests compile"` (`java-bot`) - passed
- `cmd /c "mvnw.cmd -q -DskipTests compile"` (`spring-panel`) - passed
- `cmd /c "mvnw.cmd -q -pl bot-core -Dtest=TicketServiceInboundTransportTest test"` (`java-bot`) - passed
- `cmd /c "mvnw.cmd -q -Dtest=InboundClientMessageIngestionServiceTest,ConversationTicketCreationIngestionServiceTest test"` (`spring-panel`) - passed
