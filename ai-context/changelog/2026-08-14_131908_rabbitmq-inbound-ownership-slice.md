# 2026-08-14 13:19:08 - rabbitmq inbound ownership slice

## Промпт пользователя
- `делай. и да, RabbitMQ нужен сразу`

## Что сделано
- в `java-bot/bot-core` подключён `spring-boot-starter-amqp`, добавлены transport properties, RabbitMQ exchange config и publisher для normalized inbound события `client_message.active_ticket`;
- `TicketService` получил новый gateway `recordActiveClientMessage(...)`, который в `jdbc`-режиме сохраняет старый direct write path, а в `rabbitmq`-режиме публикует событие вместо прямой записи в business tables;
- active inbound message paths переведены на этот gateway в `Telegram`, `VK` и `MAX`, чтобы первый ownership-slice работал сразу для всех текущих transport adapters;
- в `spring-panel` добавлены RabbitMQ topology/config, `@RabbitListener`, inbox-таблица `integration_inbound_event_inbox` и ingestion service, который сам выполняет backend-owned запись в `chat_history`, `ticket_active`, attachment metadata и sync клиентского профиля;
- для `spring-panel` добавлены миграции `postgresql/V16__integration_inbound_event_inbox.sql` и `sqlite/V37__integration_inbound_event_inbox.sql`;
- bootstrap и локальный compose обновлены под RabbitMQ-first запуск: `docker-compose.local-postgres.yml`, `.env.example`, `scripts/bootstrap-first-run.ps1` и `scripts/bootstrap-first-run.sh` теперь знают про `RabbitMQ` и `APP_INTEGRATION_TRANSPORT_MODE`;
- `README.md` и карточка `01-181` обновлены под новый ownership split status;
- добавлены targeted tests `TicketServiceInboundTransportTest` и `InboundClientMessageIngestionServiceTest`.

## Затронутые файлы
- `java-bot/bot-core/pom.xml`
- `java-bot/bot-core/src/main/resources/application.yml`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/BotIntegrationTransportMode.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/IntegrationRabbitProperties.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/RabbitIntegrationTransportConfig.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/ActiveInboundClientMessageCommand.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/InboundClientMessageEvent.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/InboundClientMessagePublisher.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/TicketService.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/TicketServiceInboundTransportTest.java`
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
- `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java`
- `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java`
- `spring-panel/pom.xml`
- `spring-panel/src/main/resources/application.yml`
- `spring-panel/src/main/java/com/example/panel/PanelApplication.java`
- `spring-panel/src/main/java/com/example/panel/config/IntegrationRabbitProperties.java`
- `spring-panel/src/main/java/com/example/panel/config/PanelIntegrationTransportMode.java`
- `spring-panel/src/main/java/com/example/panel/config/RabbitIntegrationTransportConfig.java`
- `spring-panel/src/main/java/com/example/panel/entity/ChatHistory.java`
- `spring-panel/src/main/java/com/example/panel/repository/MessageRepository.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/InboundClientMessageEvent.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/IntegrationInboundEventInboxService.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/InboundClientMessageIngestionService.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/InboundClientMessageListener.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V16__integration_inbound_event_inbox.sql`
- `spring-panel/src/main/resources/db/migration/sqlite/V37__integration_inbound_event_inbox.sql`
- `spring-panel/src/test/java/com/example/panel/service/integration/InboundClientMessageIngestionServiceTest.java`
- `docker-compose.local-postgres.yml`
- `.env.example`
- `scripts/bootstrap-first-run.ps1`
- `scripts/bootstrap-first-run.sh`
- `README.md`
- `ai-context/tasks/task-details/01-181.md`

## Проверка
- `cmd /c "mvnw.cmd -q -pl bot-core,bot-telegram,bot-vk,bot-max -DskipTests compile"` (`java-bot`) - passed
- `cmd /c "mvnw.cmd -q -DskipTests compile"` (`spring-panel`) - passed
- `cmd /c "mvnw.cmd -q -pl bot-core -Dtest=TicketServiceInboundTransportTest test"` (`java-bot`) - passed
- `cmd /c "mvnw.cmd -q -Dtest=InboundClientMessageIngestionServiceTest test"` (`spring-panel`) - passed
- `powershell -ExecutionPolicy Bypass -File scripts/bootstrap-first-run.ps1 -ValidateOnly -SkipDocker` - passed
- `bash scripts/bootstrap-first-run.sh --validate-only` - not run, `bash` is unavailable in the current Windows environment
