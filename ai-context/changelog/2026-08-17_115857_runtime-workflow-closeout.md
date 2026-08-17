# 2026-08-17 11:58:57 - runtime workflow closeout

## Пользовательский промпт

`добить аудит оставшихся live runtime-path на скрытые отличия от panel-side workflow;
добей ты уже эту задачу!`

## Что изменено

- в `spring-panel` internal bot write boundary закрыты последние найденные живые support-chat drift-сценарии:
  - runtime reopen теперь принимает optional `operatorIdentity`, переводит тикет в canonical `pending` и при наличии оператора назначает `responsible`;
  - добавлен edit path для operator reply из Telegram support-chat:
    - `PUT /internal/api/bot/tickets/{ticketId}/operator-messages/{telegramMessageId}`;
    - edit обновляет canonical `chat_history`, назначает `responsible` при необходимости и публикует realtime refresh через `operator_message_edited`;
- `UiEventOutboxAppendService`, `UiEventOutboxWatcher` и `DialogRealtimeEventService` расширены новым `operator_message_edited` event, чтобы panel UI получал history refresh и для operator-edit сценария из runtime;
- в `java-bot/bot-core`:
  - `PanelTicketWriteClient` и `TicketService` расширены под reopen с `operatorIdentity`;
  - добавлен panel-owned path для operator message edit;
  - локальный JDBC fallback сохранён через `ChatHistoryService`;
- в `bot-telegram` обработка `editedMessage` теперь различает:
  - edit клиентского сообщения;
  - edit operator reply в support-chat, который теперь тоже синхронизируется в canonical backend history;
- обновлены targeted tests:
  - `BotRuntimeTicketWriteServiceTest`;
  - `BotRuntimeWriteApiControllerWebMvcTest`;
  - `DialogTicketLifecycleServiceTest`;
  - `TicketServiceInboundTransportTest`.

## Проверка

- `cmd /c "mvnw.cmd -q -Dtest=BotRuntimeTicketWriteServiceTest,BotRuntimeWriteApiControllerWebMvcTest,DialogTicketLifecycleServiceTest test"` в `spring-panel`
- `cmd /c "mvnw.cmd -q -pl bot-core,bot-telegram -Dtest=TicketServiceInboundTransportTest,SupportBotTest test"` в `java-bot`
- `cmd /c "mvnw.cmd -q -DskipTests compile"` в `spring-panel`
- `cmd /c "mvnw.cmd -q -pl bot-core,bot-telegram -DskipTests compile"` в `java-bot`
