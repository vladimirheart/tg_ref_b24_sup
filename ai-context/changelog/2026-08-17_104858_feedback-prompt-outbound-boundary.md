# 2026-08-17 10:48:58 - feedback-prompt-outbound-boundary

## User prompt

`хорошо. бери в работу следующий крупный шаг`

## Что изменено

- в `spring-panel` добавлен первый panel-owned outbound RabbitMQ flow для системных bot-runtime сообщений:
  - новые outbound Rabbit properties в `app.integration.rabbitmq`;
  - `OutboundFeedbackPromptPublisher`;
  - outbound exchange/dlx wiring в `RabbitIntegrationTransportConfig`;
- добавлен `FeedbackPromptDispatchSchedulerService`, который в `rabbitmq`-режиме сам выбирает несентнутые `pending_feedback_requests`, собирает prompt и публикует `feedback.prompt.dispatch`;
- добавлен `PanelBotSettingsService`, который на backend-side выбирает rating template/scale из canonical `bot_settings` и формирует основу prompt без участия runtime scheduler-а;
- `BotRuntimeContractService` теперь автоматически прокидывает channel-scoped outbound Rabbit contract в запускаемый бот-процесс:
  - `APP_INTEGRATION_RABBITMQ_OUTBOUND_EXCHANGE`;
  - `APP_INTEGRATION_RABBITMQ_OUTBOUND_DLX`;
  - `APP_INTEGRATION_RABBITMQ_OUTBOUND_QUEUE`;
  - `APP_INTEGRATION_RABBITMQ_OUTBOUND_DLQ`;
  - `APP_INTEGRATION_RABBITMQ_OUTBOUND_ROUTING_KEY`;
- internal bot read API дополнен безопасным `GET /internal/api/bot/channels/{channelId}` для lookup канала без fallback на runtime-side local JPA;
- в `java-bot/bot-core` добавлен outbound consumer slice:
  - `OutboundFeedbackPromptListener`;
  - `OutboundFeedbackPromptDispatchService`;
  - `OutboundFeedbackPromptEvent`;
- `ChannelService.findById(...)` в `rabbitmq`-режиме теперь ходит в `spring-panel` через `PanelChannelClient`, а не читает локальный runtime repository;
- `EngagementTasks.dispatchPendingFeedbackRequests()` в `rabbitmq`-режиме отключён и оставлен только как JDBC/legacy fallback.

## Проверка

- `cmd /c "mvnw.cmd -q -pl bot-core,bot-telegram,bot-vk,bot-max -DskipTests compile"` в `java-bot`
- `cmd /c "mvnw.cmd -q -DskipTests compile"` в `spring-panel`
- `cmd /c "mvnw.cmd -q -pl bot-core -Dtest=ChannelServiceTest,OutboundFeedbackPromptDispatchServiceTest,EngagementTasksTest test"` в `java-bot`
- попытка прогона panel-side targeted tests через Maven упёрлась в уже существующий общий `test-compile` хвост `spring-panel` в старых тестах `BotRuntimeContractServiceTest` / `BotProcessServiceTest`, не относящийся к этому шагу; main-сборка `spring-panel` при этом зелёная

## Что дальше

- следующий быстрый крупный шаг по `01-181` теперь логично брать в оставшийся runtime-owned config/settings perimeter:
  - `SharedConfigService`-зависимые lookup path в платформенных адаптерах;
  - остальные engagement/notification/settings сценарии в `bot-core`;
  - затем аудит более вторичных legacy-boundary сервисов.
