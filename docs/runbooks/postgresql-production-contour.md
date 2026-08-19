# PostgreSQL Production Contour Runbook

Актуально на `2026-08-19`.

Документ фиксирует фактический production contour Iguana после крупных пакетов `01-181`..`01-183` и служит коротким operator/engineering runbook для живого `PostgreSQL-first` запуска.

## 1. Canonical runtime contour

Production contour теперь предполагает следующий live stack:

- `spring-panel` как canonical backend-owner для business state, incident domain, transport inbox/outbox и operator UI;
- `PostgreSQL` как единственный canonical business/runtime storage;
- `Redis` как coordination layer для leases, shared cooldown/cursor semantics и multi-instance runtime decisions;
- `RabbitMQ` как integration transport backbone для inbound/outbound message flows;
- `MinIO/S3-compatible` object storage как обязательный attachment boundary;
- `java-bot` как transport/integration runtime, а не owner business schema.

Что не считается production contour:

- implicit fallback в `SQLite`;
- direct business writes из integration workers в business DB;
- local-disk-first attachments как live source of truth;
- отдельные `settings.db`, `objects.db`, `panel_identity.db`, `bot-<channelId>.db` как самостоятельные production контуры.

## 2. Ownership boundaries

- `spring-panel` владеет dialog/task/incident/object-facing business state и canonical Flyway schema ownership.
- `java-bot` публикует inbound transport события и получает outbound delivery work через queue/API boundary.
- Incident lifecycle, watchers, routes, delivery ledger и transport degradation incidents живут в canonical backend contour.
- Attachments в production должны резолвиться через object storage boundary, а не через local repo/runtime disk как live storage.
- `java-bot` long-poll ingress (`Telegram`, `VK`, `MAX`) теперь должен жить под shared ingress lease через Redis coordination, а не как uncontrolled multi-instance consumer.
- `VK`/`MAX` webhook question-flow session state теперь тоже должен жить в shared Redis-backed bot session store, а не в process-local памяти одного bot instance.

## 3. Multi-instance audit

### 3.1. Shared side-effectful flows, которые уже coordinated

- `HousekeepingScheduler`, `DialogAutoCloseSchedulerService`, `FeedbackPromptDispatchSchedulerService`, `OperatorNotificationWatcher`, `UiEventOutboxWatcher`, incident/monitoring sync jobs и остальные panel-side mutating schedulers идут под `RuntimeCoordinationService.runWithLease(...)`.
- `OutboundFeedbackPromptPublishOutboxService`, `IncidentRouteDeliveryOutboxService`, `IntegrationTransportOutboxService` и `IntegrationInboundEventInboxService` используют durable outbox/inbox + claim/recovery semantics вместо local singleton assumptions.
- `RabbitListener` consumers в `spring-panel` и `java-bot` масштабируются через queue concurrency/prefetch, а не через локальные in-memory очереди.
- Round-robin assignment state больше не instance-local:
  - `ChannelAssignmentRoutingService`;
  - `SlaEscalationAutoAssignService`;
  теперь берут shared cursor через `Redis` coordination layer.
- SLA escalation webhook cooldown больше не живёт только в памяти одного backend instance:
  - `SlaEscalationWebhookNotifier` использует shared cooldown state через coordination layer.
- bot-side ingress leadership тоже больше не должен быть implicit local assumption:
  - `TelegramLongPollingLifecycle`;
  - `VkSupportBot`;
  - `MaxLongPollingLifecycle`;
  обновляют shared ingress lease и не должны одновременно держать live long-poll ownership для одного канала.
- bot-side scheduled flows, привязанные к ingress/session semantics, тоже больше не должны работать как "каждый instance сам по себе":
  - `VkSupportBot.sendUnblockDigest`;
  - `VkSupportBot.expireSilentQuestionFlowSessions`;
  - `MaxWebhookController.expireSilentQuestionFlowSessions`;
  теперь исполняются через отдельные shared job leases, а не через неявную зависимость от ingress owner.

### 3.2. Harmless local-only loops

Эти циклы могут выполняться на каждом instance независимо и не считаются business side effects:

- `HikariPoolPressureReporter` — diagnostic logging only;
- `UiEventStreamService.sendHeartbeat()` — heartbeat только для локально подключенных SSE emitter'ов;
- `SidebarStatusWatcher` и `SidebarBotStatusWatcher` — локальный UI refresh signal для текущего panel instance;
- `IntegrationNetworkService.routeUnavailableUntil` — process-local network failover hint, не canonical business state.

### 3.3. Explicit local-control invariants

Эти зоны не являются скрытыми shared writers, но их важно держать в голове при production эксплуатации:

- `BotProcessService` — локальный process-control слой панели. В production его нужно считать convenience/control-plane функцией, а не shared distributed orchestrator.
- Telegram question-flow session state всё ещё остаётся локальным runtime concern, но его ingress уже constrained через distributed singleton ownership на канал.
- `VK` и `MAX` webhook question-flow state больше не должен фрагментироваться между instance: session snapshots externalized в shared bot session store.

Практический вывод:

- backend/transport contour уже multi-instance safe на стороне PostgreSQL/Redis/RabbitMQ;
- long-poll bot ingress теперь тоже закрыт через distributed singleton ownership;
- webhook question-flow state для `VK`/`MAX` уже externalized;
- remaining non-ideal zone теперь смещена в более глубокий worker replay/observability/debug слой, а не в базовое session-sharing.

## 4. Operator workbench and recovery surface

Основной operator-facing surface для contour:

- `/incidents`:
  - incident workbench;
  - watchers/routes management;
  - route redelivery;
  - incident event/runbook notes;
  - `Integration recovery` tab для failed/stale inbound/outbound transport events, payload inspection, ticket-scoped debug, checkpoint overrides, recovery audit trail и transport observability alerts.

Если transport contour деградировал:

1. Открыть `/incidents`.
2. Проверить `Integration recovery` и incident list по `signal_type=integration_transport`.
3. Проверить transport alerts, stale checkpoints и recent recovery operations.
4. Для stuck/failed inbound использовать replay/requeue actions.
5. Для route delivery failures использовать incident route redelivery.
6. Если cursor у worker ушёл в неверное состояние, использовать manual checkpoint update только как осознанный recovery action.
7. Для конкретного `ticket_id` использовать targeted transport debug, чтобы увидеть inbound/outbound history, связанные incidents и ручные recovery operations в одном месте.

Для более широкой transport observability дополнительно использовать `/analytics` -> `Integration Transport Ops`:

- current health snapshot по inbox/outbox/checkpoints;
- recent health snapshots и trend summary за окно наблюдения;
- sustained pressure alerts, если contour остаётся unhealthy несколько snapshot-циклов подряд;
- recent recovery operations как быстрый audit trail по ручным replay/requeue действиям.

## 5. Release checklist

- Подтвердить доступность `PostgreSQL`, `Redis`, `RabbitMQ` и object storage до старта panel/backend.
- Убедиться, что `APP_DB_MODE=postgresql`, а не compatibility `sqlite`.
- Не запускать integration workers с direct business DB ownership contract.
- Для long-poll bot ingress убедиться, что `APP_COORDINATION_MODE=redis` и lease ownership реально работает через общий Redis.
- Для `VK`/`MAX` webhook ingress требуется общий Redis coordination/session layer; без него webhook multi-instance contour не считается поддержанным.
- После релиза проверить:
  - `/incidents`;
  - transport alerts / recovery audit trail / stale checkpoint cards;
  - transport incidents;
  - route delivery;
  - worker checkpoints;
  - object-storage readiness;
  - leased schedulers в логах без duplicate side effects.

## 6. Residual production debt

На `2026-08-19` remaining contour debt уже не про SQLite datasources, не про transport ownership и не про базовый webhook session-sharing. Основной незакрытый хвост:

- deeper worker-forensics/replay surface за пределами panel-side transport snapshots и текущего recovery audit trail;
- более широкий внешний alerting/integration observability слой поверх уже собранного contour.

Всё остальное production contour следует считать уже собранным вокруг canonical `PostgreSQL + Redis + RabbitMQ + object storage + incident workbench`.
