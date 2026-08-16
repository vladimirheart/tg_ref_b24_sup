# 2026-08-16 23:49:53 - panel auto-close ownership

## Промпт пользователя
- `хорошо. давай следующий большой шаг по задаче`

## Что сделано
- в `spring-panel` добавлен `DialogAutoCloseSchedulerService`, который в `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq` сам исполняет auto-close sweep вместо `bot-core`;
- panel-side auto-close теперь сам закрывает stale tickets, завершает `ticket_spans`, обновляет `work_time_total_sec`, пишет `system_event`, append-ит `ticket_closed_auto` в `ui_event_outbox` и обновляет `pending_feedback_requests`;
- `UiEventOutboxAppendService` расширен поддержкой `ticket_closed`/`ticket_closed_auto`;
- `ChatHistoryRepository` и `TicketSpanRepository` расширены lookup-методами для panel-owned auto-close logic;
- добавлен `PanelTaskService` и `DialogAutoCloseFollowUpTaskService`, чтобы backend сам создавал follow-up task для auto-closed dialog;
- в `java-bot/bot-core` `MaintenanceTasks.autoCloseInactiveTickets()` теперь пропускает bot-side auto-close execution в `rabbitmq`-режиме и оставляет ownership panel scheduler-у;
- добавлены targeted tests для нового panel-owned auto-close path и bot-side skip логики.

## Затронутые файлы
- `spring-panel/src/main/java/com/example/panel/repository/ChatHistoryRepository.java`
- `spring-panel/src/main/java/com/example/panel/repository/TicketSpanRepository.java`
- `spring-panel/src/main/java/com/example/panel/service/UiEventOutboxAppendService.java`
- `spring-panel/src/main/java/com/example/panel/service/PanelTaskService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogAutoCloseFollowUpTaskService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogAutoCloseSchedulerService.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogAutoCloseFollowUpTaskServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogAutoCloseSchedulerServiceTest.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/MaintenanceTasks.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/MaintenanceTasksTest.java`
- `ai-context/tasks/task-details/01-181.md`

## Проверка
- `cmd /c "mvnw.cmd -q -pl bot-core,bot-telegram,bot-vk,bot-max -DskipTests compile"` (`java-bot`) - passed
- `cmd /c "mvnw.cmd -q -DskipTests compile"` (`spring-panel`) - passed
- `cmd /c "mvnw.cmd -q -pl bot-core -Dtest=TicketServiceInboundTransportTest,MaintenanceTasksTest test"` (`java-bot`) - passed
- `cmd /c "mvnw.cmd -q -Dtest=BotRuntimeReadApiControllerWebMvcTest,BotRuntimeWriteApiControllerWebMvcTest,BotRuntimeTicketWriteServiceTest,DialogAutoCloseSchedulerServiceTest,DialogAutoCloseFollowUpTaskServiceTest test"` (`spring-panel`) - passed
