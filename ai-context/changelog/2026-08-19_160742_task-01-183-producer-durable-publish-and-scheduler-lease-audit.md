# Task 01-183 - Producer Durable Publish And Scheduler Lease Audit

## Что сделано

- В `spring-panel` добавлен durable producer outbox для outbound feedback prompt:
  - новый сервис `OutboundFeedbackPromptPublishOutboxService`;
  - новые миграции `V24__integration_transport_outbox.sql` и `V45__integration_transport_outbox.sql`;
  - `OutboundFeedbackPromptPublisher` переведён с direct `RabbitTemplate.convertAndSend(...)` на enqueue в `integration_transport_outbox`;
  - dispatch выполняется по `@Scheduled` с Redis lease `integration-transport-outbox-dispatch`;
  - publish теперь подтверждается через Rabbit publisher confirms + mandatory publish semantics.

- В `java-bot` добавлен durable producer outbox для inbound transport publish:
  - новый сервис `IntegrationTransportOutboxService`;
  - `InboundClientMessagePublisher` и `ConversationTicketCreatedPublisher` переведены на enqueue в `integration_transport_outbox`;
  - добавлен scheduled dispatcher с row-claim/retry/recover semantics;
  - включены publisher confirms и publisher returns в Rabbit config;
  - схема `integration_transport_outbox` добавлена в `schema-postgres.sql`, `schema-sqlite.sql`, `schema.sql`;
  - добавлен `app.integration.outbox.dispatch-interval-ms`.

- Для `bot-core` transport outbox добавлена встроенная сериализация/десериализация `OffsetDateTime`, чтобы payload-контракт не зависел от внешней регистрации Jackson JavaTime modules.

- `PostgresRuntimeReadinessVerifier` теперь проверяет и `integration_transport_outbox`, чтобы READY marker не выходил при поломанном producer-side transport contour.

- По remaining `@Scheduled` в `spring-panel` отделены shared-side-effect jobs от harmless/local:
  - под lease переведены `KnowledgeBaseNotionSyncScheduler`, `HousekeepingScheduler.cleanupDrafts`, `AiOfflineEvaluationService.runScheduledEvaluation`, `IikoApiMonitoringScheduler`, `IikoDepartmentLocationsSyncScheduler`, `NetBoxObjectPassportSyncScheduler`;
  - локальными оставлены `HousekeepingScheduler.warmUpAnalyticsCache`, `HousekeepingScheduler.clearCaches`, `UiEventStreamService.sendHeartbeat`, `SidebarStatusWatcher`, `SidebarBotStatusWatcher`, `HikariPoolPressureReporter`.

- По remaining `@Scheduled` в `java-bot` подтверждена классификация:
  - `MaintenanceTasks` и `EngagementTasks` уже harmless в rabbit contour, потому что bot-side ownership отключается через `integrationTransportMode.isRabbitMqMode()`;
  - scheduled session expiry в `SupportBot`, `VkSupportBot`, `MaxWebhookController` остаётся local-only, потому что работает по in-memory conversation/session state конкретного инстанса;
  - новый `IntegrationTransportOutboxService.dispatchScheduled()` безопасен для multi-instance за счёт durable row claim semantics.

## Проверки

- `spring-panel`: `./mvnw.cmd -q -DskipTests compile`
- `spring-panel`: `./mvnw.cmd -q "-Dtest=IntegrationInboundEventInboxServiceTest,DialogAutoCloseSchedulerServiceTest,BotRuntimeBlacklistServiceTest,OperatorNotificationWatcherTest,InboundClientMessageIngestionServiceTest,ConversationTicketCreationIngestionServiceTest,PostgresRuntimeReadinessVerifierTest,OutboundFeedbackPromptPublishOutboxServiceTest,AiOfflineEvaluationServiceTest,IikoDepartmentLocationsSyncSchedulerTest" test`
- `java-bot`: `./mvnw.cmd -q -pl bot-core,bot-telegram,bot-vk -am -DskipTests compile`
- `java-bot`: `./mvnw.cmd -q -pl bot-core "-Dtest=IntegrationTransportOutboxServiceTest,TicketServiceInboundTransportTest" test`

## Что остаётся после этого пакета

- Финальный разбор live-scaling semantics на самих integration workers/channel runtimes, если захотим довести transport coordination глубже, чем current row-claim + Rabbit consumer concurrency.
- Остаточные infra/business gaps вне этого блока: observability, incident automation depth, и любые внешние integration-side compensations/replay tools, если они ещё не покрыты в других пакетах.
