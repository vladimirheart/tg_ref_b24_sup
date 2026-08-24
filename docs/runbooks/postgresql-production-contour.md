# PostgreSQL Production Contour Runbook

Актуально на `2026-08-24`.

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
- `VK` и `MAX` webhook delivery path на `2026-08-20` уже не ограничен только shared read-model:
  - update delivery fenced через shared ingress ownership;
  - duplicate/in-flight webhook deliveries dedupe'ятся через общий delivery guard;
  - session mutation/save/delete идут через optimistic CAS, а не через last-write-wins overwrite.

Практический вывод:

- backend/transport contour уже multi-instance safe на стороне PostgreSQL/Redis/RabbitMQ;
- long-poll bot ingress теперь тоже закрыт через distributed singleton ownership;
- webhook question-flow state и mutation semantics для `VK`/`MAX` уже externalized и coordinated;
- remaining non-ideal zone теперь смещена в более глубокий worker replay/observability/debug слой и внешний observability/alerting closeout, а не в базовое session-sharing.

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

На `2026-08-20` remaining contour debt уже не про SQLite datasources, не про transport ownership и не про базовый webhook session-sharing. Основной незакрытый хвост:

- deeper worker-forensics/replay surface за пределами panel-side transport snapshots и текущего recovery audit trail;
- более широкий внешний alerting/integration observability слой поверх уже собранного contour.

Всё остальное production contour следует считать уже собранным вокруг canonical `PostgreSQL + Redis + RabbitMQ + object storage + incident workbench`.

## 7. Worker forensics addendum

На `2026-08-19` panel-side observability по transport contour уже включает не только общий snapshot/trend слой, но и отдельный worker drilldown.

Через `/analytics` -> `Integration Transport Ops` теперь доступны:

- current worker health status и lag thresholds;
- worker snapshot history;
- worker-specific manual operations;
- related incidents и operator recommendations;
- worker-specific signal incidents `integration_transport/panel-runtime-checkpoints/<worker_key>`.

Практический смысл:

- operator может начать разбор с runtime checkpoint row и уйти в `Inspect`, не собирая историю worker'а вручную по таблицам;
- incident trail теперь есть не только по overall transport degradation, но и по конкретному watcher/worker pressure;
- remaining residual debt по worker-side observability смещён уже не в отсутствие базового drilldown, а в более глубокий replay/forensics и внешний alerting/automation слой.
## Operator readiness surface

В `Settings -> Production readiness` доступен read-only snapshot основных runtime gates. Он не заменяет startup fail-fast verifier и не выполняет никаких repair/restart/provision действий.

Интерпретация общего статуса:

- `ready` — datasource mode `postgresql`, и все обязательные production-компоненты отвечают без деградации;
- `degraded` — хотя бы один обязательный probe недоступен/деградирован, Rabbit transport не `rabbitmq`, DLQ не пуст, либо durable incident delivery имеет unresolved failed/stale-processing записи;
- `compatibility` — приложение запущено не в canonical PostgreSQL mode; такой snapshot пригоден для dev/legacy диагностики, но не является production readiness.

Проверяемые компоненты:

- PostgreSQL: активный JDBC datasource + `SELECT 1` + product check в canonical mode;
- Redis: существующий shared coordination readiness ping;
- RabbitMQ: наличие/доступность inbound и ticket-created queues, плюс видимость DLQ backlog;
- MinIO/S3: тот же `HeadBucket` probe, который используется startup readiness;
- Incident alert delivery: текущие failed/queued/processing, stale processing и terminal success rate за 24 часа.

Если snapshot `degraded`, сначала устранить конкретный component reason в UI и только затем повторить проверку. Само обновление snapshot безопасно и не меняет runtime state.

## Bot worker DB isolation invariant (v35)

Для production bot child одновременно должны выполняться условия:

- panel runtime: PostgreSQL canonical;
- `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq`;
- `APP_DB_MODE=worker`;
- в child environment отсутствуют `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `DATABASE_URL`, `APP_DB_BOT_RUNTIME`, `SUPPORT_BOT_DATABASE_PATH`;
- business ticket/channel/feedback/blacklist paths идут только через RabbitMQ или internal panel API;
- временный worker SQLite файл из `%TEMP%` / `java.io.tmpdir` стартует без business schema; self-owned technical tables для worker coordination/dedup допустимы, но случайный repository/JDBC доступ к business tables должен завершаться ошибкой, а не работать как hidden local fallback.

Проверка runtime contract без старта:

```text
GET /api/bots/{channelId}/runtime-contract
```

Для production-ready channel required keys должны содержать `APP_DB_MODE` и `APP_INTEGRATION_TRANSPORT_MODE`, но не `SPRING_DATASOURCE_URL`. Если child стартует с direct PostgreSQL datasource, production boundary считается нарушенной даже при доступной БД.

## Windows log/console UTF-8 invariant (v36)

Все application log files panel и java-bot записываются Logback encoder-ами в UTF-8. На Windows тот же invariant распространяется на direct Maven execution:

- `spring-panel/mvnw.cmd` и `java-bot/mvnw.cmd` переключают console code page на UTF-8 (`65001`);
- Maven JVM и Surefire test JVM получают UTF-8 `file/stdout/stderr` runtime options;
- `run-windows.bat` по-прежнему остаётся штатным launcher и также использует code page `65001`;
- появление последовательностей вида `╨С╨╛╤В` в runtime/test output считается encoding regression, а не допустимым cosmetic warning.

Для чтения файловых логов из Windows PowerShell 5.1 кодировку лучше указывать явно:

```powershell
Get-Content ..\logs\spring-panel.log -Encoding UTF8 -Tail 100 -Wait
Get-Content ..\logs\bots.log -Encoding UTF8 -Tail 100 -Wait
Get-Content ..\logs\errors.log -Encoding UTF8 -Tail 100 -Wait
```

## Final `01-183` acceptance gate

`01-183` считается реализованной на уровне code/runtime-contract только для canonical production contour. Сам факт успешного Spring Boot startup недостаточен для production acceptance.

Перед ручным переводом задачи из `🟣` в `🟢` подтвердить одновременно:

- `Settings -> Production readiness` возвращает `ready`, а не `degraded`/`compatibility`;
- active datasource product — PostgreSQL;
- Redis readiness ping успешен;
- transport mode — `rabbitmq`, required queues существуют, необъяснённого DLQ backlog нет;
- object-storage provider — S3-compatible и bucket probe успешен;
- incident durable delivery не имеет unresolved failed/stale-processing состояния;
- production bot runtime contract содержит `APP_DB_MODE=worker` и `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq`;
- child environment не содержит canonical `SPRING_DATASOURCE_*`/`DATABASE_URL`;
- реальный bot child проходит startup/workflow smoke;
- application и process logs читаются в UTF-8 без mojibake.

`sqlite`, `jdbc` и `local_fs` остаются допустимыми только для явно выбранных compatibility/dev/import сценариев. Snapshot `compatibility` не является production-ready состоянием.

После выполнения этого gate дальнейшие richer reporting, external alerting и worker-forensics следует вести отдельными задачами: они улучшают maturity, но не открывают заново базовый production-contour scope `01-183`.