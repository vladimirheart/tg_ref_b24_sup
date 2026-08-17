# 2026-08-16 23:58:41 - client-message-edit-boundary

## User prompt

`бери в работу следующий крупный шаг`

## Что изменено

- в `spring-panel` internal write API расширен новой mutation-операцией для редактирования клиентского сообщения по `channel_id + tg_message_id`;
- `BotRuntimeTicketWriteService` теперь backend-side обновляет `chat_history.original_message`, `chat_history.message`, `edited_at` и сам append-ит `client_message_edited` в `ui_event_outbox`;
- `UiEventOutboxAppendService` получил отдельный helper для `client_message_edited`, чтобы panel-owned append path оставался централизованным;
- в `java-bot/bot-core` добавлен `PanelTicketWriteClient.markClientMessageEdited(...)` и новый `TicketService` gateway с сохранением JDBC fallback;
- `bot-telegram` переведён с direct `chatHistoryService.markClientMessageEdited(...)` на `ticketService.markClientMessageEdited(...)`;
- добавлены и обновлены targeted tests для controller/service/transport delegation на обеих сторонах boundary.

## Проверка

- `cmd /c "mvnw.cmd -q -pl bot-core,bot-telegram -DskipTests compile"` в `java-bot`
- `cmd /c "mvnw.cmd -q -DskipTests compile"` в `spring-panel`
- `cmd /c "mvnw.cmd -q -pl bot-core -Dtest=TicketServiceInboundTransportTest test"` в `java-bot`
- `cmd /c "mvnw.cmd -q -Dtest=BotRuntimeWriteApiControllerWebMvcTest,BotRuntimeTicketWriteServiceTest test"` в `spring-panel`

## Что дальше

- проверить, остались ли ещё runtime-side delete/lifecycle ветки сообщений с direct DB access;
- после этого добивать финальный perimeter cleanup по оставшимся direct reads/writes, которые уже не относятся к основным dialog flows.
