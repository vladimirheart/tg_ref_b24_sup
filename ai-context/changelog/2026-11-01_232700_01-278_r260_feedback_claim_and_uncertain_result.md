# 01-278 R260: конкурентный claim и неопределённый результат feedback

Дата: 2026-11-01, Europe/Moscow.
Промпт пользователя: «делай».

## Изменения

- Fresh clean a3cec82d1f002c7cce752f0aa28ad33a871bb76f HEAD/origin/main/remote,
  parenta88cd2d09 и пользовательский R259/scope11 проверены.
- Bot-core OutboundTransportDeliveryLedgerService: INSERT ON CONFLICT, CAS
  retryable/status/updated_at, independent REQUIRES_NEW transitions, durable
  sending и uncertain; stale/старый failed/unknown не разрешают новый send,
  поздний failed не открывает terminal/post-handoff state. Новых колонок нет.
- MessagingService: сохранён3-argument API, новый durable hook перед provider,
  pin/recheck исходного sender proof после admission и DB I/O.
- OutboundFeedbackPromptDispatchService: pre-provider ошибки retryable,
  post-handoff false/exception/result persistence failure блокируют повтор;
  нет fallback, reroute, автоматического сброса/пересылки.
- Адаптированы3 прежних core test fixtures к hook/lifecycle/isolated PG override;
  admission/raw Unicode/bigint assertions сохранены. Добавлен
  FeedbackProviderHandoffTest (24 cases).
- Добавлен DeliveryLedgerConcurrencyTest:12 executions, включая пять повторов
  детерминированной PG гонки; baseline RED11fail/0error/0skip, frozen fixed GREEN.
- Acceptance fixture и matrix:194 delivery +12 ledger =206/2 suites GREEN.
 36 cases provider false/потеря HTTP-ответа/сбой записи результата и caller
  rollback; безопасный explicit retry доказан отдельно до provider. Прежний
  post-provider503 retry заменён блокировкой, а не ослаблением admission.
- Всего593 executions/11 suites (core387/9 +acceptance206/2), failures/errors/
  skips0. Повторные прогоны не суммируются. Wrapper/offline, PG192/Rabbit256MiB,
  по1 CPU, Maven96/test JVM128MiB. Новый core JAR проверен в изолированной сборке,
  panel1435 exact R257 classes переиспользованы; это не full fleet/image build.
- README/state table, task/S0/BOT_RUNTIME_CONTRACT уточняют выполненное,
  reconciliation/live/fleet остаток и запрет mixed old/new ledger rollout.
- Exact detached source/replay/blob/content/compiled-input guards; evidence —
  .tmp/01-278-r260-evidence/. Рабочие18 containers/config/контрольные данные/flags/
  queues/readiness прежние. Собственные fixture containers и одноразовые данные
  удалены; рабочие объекты не удалялись. Нет внешних сообщений, schema migration,
  backfill, рабочего requeue/purge, deploy, staging/commit/push.

## Границы

Это source-only R260; рабочие children ещё R252. Старый код не знает новых
статусов и может их перехватить: будущий выпуск/откат требует отдельного gate
с остановкой старых consumers и целым новым fleet, не mixed rollout. Сверка
неопределённого исхода по квитанции и операторское разрешение блокировки ещё
не реализованы. Exactly-once/live SDK/customer callback/full process/fleet/S6
не заявлены. Остальные бизнес/GENERAL/service-chat/system/AI gates остаются;
задача🟡, S2B снятR190.
