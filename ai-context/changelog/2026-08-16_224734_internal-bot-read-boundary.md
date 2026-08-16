# 2026-08-16 22:47:34 - internal bot read boundary

## Промпт пользователя
- `продолжи выполнение задачи`

## Что сделано
- в `spring-panel` добавлен internal read API `/internal/api/bot/**` с token-based доступом для bot runtime;
- реализован `BotRuntimeTicketReadService`, который отдаёт active ticket lookup, ticket details, recent tickets, last-ticket context и client request number;
- расширены panel repositories (`MessageRepository`, `TicketActiveRepository`, `FeedbackRepository`), чтобы backend-owned read contract воспроизводил старую bot-side lookup-логику без новой schema migration;
- `BotRuntimeContractService` теперь прокидывает bot runtime internal env contract: `APP_PANEL_INTERNAL_API_BASE_URL` и `APP_PANEL_INTERNAL_API_TOKEN`;
- в `java-bot/bot-core` добавлен `PanelTicketReadClient`;
- `TicketService` в `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq` переключён на panel-owned reads для `findActiveTicketForUser(...)`, `findByTicketId(...)`, `findRecentTicketsForUser(...)`, `findLastMessage(...)` и `resolveClientTicketNumber(...)`;
- обновлены targeted tests для нового read-side boundary и env wiring.

## Затронутые файлы
- `spring-panel/src/main/resources/application.yml`
- `spring-panel/src/main/java/com/example/panel/security/SecurityConfig.java`
- `spring-panel/src/main/java/com/example/panel/repository/MessageRepository.java`
- `spring-panel/src/main/java/com/example/panel/repository/TicketActiveRepository.java`
- `spring-panel/src/main/java/com/example/panel/repository/FeedbackRepository.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeContractService.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeTicketReadService.java`
- `spring-panel/src/main/java/com/example/panel/controller/BotRuntimeReadApiController.java`
- `spring-panel/src/test/java/com/example/panel/controller/BotRuntimeReadApiControllerWebMvcTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeContractServiceTest.java`
- `java-bot/bot-core/src/main/resources/application.yml`
- `java-bot/bot-core/src/main/java/com/example/supportbot/config/IntegrationPanelApiProperties.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/PanelTicketReadClient.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/TicketService.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/TicketServiceInboundTransportTest.java`
- `ai-context/tasks/task-details/01-181.md`

## Проверка
- `cmd /c "mvnw.cmd -q -pl bot-core,bot-telegram,bot-vk,bot-max -DskipTests compile"` (`java-bot`) - passed
- `cmd /c "mvnw.cmd -q -DskipTests compile"` (`spring-panel`) - passed
- `cmd /c "mvnw.cmd -q -pl bot-core -Dtest=TicketServiceInboundTransportTest test"` (`java-bot`) - passed
- `cmd /c "mvnw.cmd -q -Dtest=BotRuntimeReadApiControllerWebMvcTest,BotRuntimeContractServiceTest test"` (`spring-panel`) - passed
