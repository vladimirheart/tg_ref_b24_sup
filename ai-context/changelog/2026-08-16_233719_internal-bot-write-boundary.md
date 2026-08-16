# 2026-08-16 23:37:19 - internal bot write boundary

## Промпт пользователя
- `давай следующий крупный пункт задачи`

## Что сделано
- в `spring-panel` добавлен internal write API `/internal/api/bot/**` с token-based доступом для bot runtime;
- реализован `BotRuntimeTicketWriteService`, который берёт на себя panel-owned write operations для reopen ticket, register/clear activity и record operator relay;
- добавлен `UiEventOutboxAppendService`, чтобы backend сам append-ил `ticket_reopened` в `ui_event_outbox`;
- `DialogReplyTargetService.touchTicketActivity(...)` расширен так, чтобы обновлять `last_seen` даже без `operatorIdentity`, сохраняя legacy-поведение helper path;
- в `java-bot/bot-core` добавлен `PanelTicketWriteClient`;
- `TicketService` в `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq` переведён на panel-owned writes для `reopenTicket(...)`, `registerActivity(...)`, `clearTicketActivity(...)` и нового `recordOperatorRelay(...)`;
- `SupportBot.handleOperatorMessage(...)` больше не пишет `chat_history` и `ticket_active` напрямую после успешного relay операторского ответа клиенту;
- добавлены targeted tests для нового write-side boundary на стороне `spring-panel` и `java-bot`.

## Затронутые файлы
- `spring-panel/src/main/java/com/example/panel/controller/BotRuntimeWriteApiController.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeTicketWriteService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogReplyTargetService.java`
- `spring-panel/src/main/java/com/example/panel/service/UiEventOutboxAppendService.java`
- `spring-panel/src/test/java/com/example/panel/controller/BotRuntimeWriteApiControllerWebMvcTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeTicketWriteServiceTest.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/PanelTicketWriteClient.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/TicketService.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/TicketServiceInboundTransportTest.java`
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
- `ai-context/tasks/task-details/01-181.md`

## Проверка
- `cmd /c "mvnw.cmd -q -pl bot-core,bot-telegram,bot-vk,bot-max -DskipTests compile"` (`java-bot`) - passed
- `cmd /c "mvnw.cmd -q -DskipTests compile"` (`spring-panel`) - passed
- `cmd /c "mvnw.cmd -q -pl bot-core -Dtest=TicketServiceInboundTransportTest test"` (`java-bot`) - passed
- `cmd /c "mvnw.cmd -q -Dtest=BotRuntimeReadApiControllerWebMvcTest,BotRuntimeWriteApiControllerWebMvcTest,BotRuntimeTicketWriteServiceTest test"` (`spring-panel`) - passed
