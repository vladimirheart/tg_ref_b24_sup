# 01-278 R265: восстановление feedback после гибели delivery JVM

Время: 2026-11-02 10:03 MSK.

Промпт пользователя: «делай» — продолжение после R264, проверка восстановления
feedback после полного перезапуска процесса; рабочий стенд не перезапускался.

## Выполнено

- Проверены clean HEAD/origin/main/actual remote
  ae1743e51fa703e5d40c908fc6fb4f4002169a18 (R264,parent8dbb08320), scope4 и
  content manifests R264. Main Java/SQL/POM после R263 не менялись.
- Добавлены test-only FeedbackProcessWorker/FeedbackProcessRestartTest в
  opt-in bot-feedback-acceptance. Отдельная JVM содержит production listener,
  dispatcher/MessagingService, ledger/receipt transactions и signed HTTP clients.
  Channel reader и loopback provider остаются управляемыми тестовыми границами.
- Родитель force-kill процесса после committed claim, committed prepare,
  положительного provider response до receipt, committed receipt и committed
  delivered до ACK. До kill проверяются реальные durable rows/queue consumer.
- RabbitMQ автоматически возвращает исходное unacked сообщение: ready1/
  consumers0 до нового процесса, другой PID/redelivered=true/тот же payload
  SHA256. Нет ручного fixture replay/republish. Финальный ACK проверен после
  graceful close нового consumer: empty queue/consumers0/DLQ0.
- При revoked channel/catalog и API503 positive receipt переводит sending в
  delivered без повторного provider/admission; нет receipt → processing/sending
  blocked. Уже delivered/receipt/outbox rows и timestamps exact. Kill до SDK
  тоже не открывает слепой повтор без положительной квитанции.
- 323 выполнения/4 suites GREEN:194 chain +12 ledger +48 service recovery +69
  новых (60 =4 scope ×3 routes ×5 crash points,8 input guards,1 smoke). Source
  и replay независимо GREEN, не646 тестов; failures/errors/skips0. Первичный
  smoke также GREEN, в итоговый count второй раз не добавлен.
- Первый report guard потребовал уникальность всех XML names;319 имён/323
  выполнения объясняются существующим RepeatedTest(5) одной ledger race. Guard
  v2 принимает ровно эти5 повторов и требует69 уникальных имён нового suite;
  product/tests/assertions не ослаблялись, старый operator сохранён.
- 292 прежних compiled inputs/212 core/1435 panel classes exact;17 acceptance
  test classes/9 inputs frozen и одинаковы в source/replay. Нет случайного
  устаревшего executable JAR, test-модуль не включается в runtime image. Это не
  новый прогон всех769 R263 source tests и не full Maven/assets rebuild.
- Worker принимает только isolated loopback DB/AMQP/API, owned schema и fake
  credentials; не наследует Spring/provider/datasource ENV, не читает runtime
  application/shared-config. Heap96/Metaspace96MiB, один worker за раз.
- Owned PG192/Rabbit256MiB/1CPU не OOM/restart; схемы/child JVM не утекли,
  оба временных контейнера и одноразовые данные удалены по exact ID/label guard.
  Все18 рабочих containers/config/БД V85/settings455/accounts/grants/roots/
  pending/ratings/outbox/queues unchanged. Ledger17delivered/receipt0/readinessUP;
  прежний inboundDLQ1 не исправлялся. R264 backup SHA/rollback image проверены.

## Не доказаны и остаток

Это source-only test package, не изменение product behavior/rollout. Нет working
migration/backfill/flags/provider/customer sends, staging/commit/push. Runtime
R264: runnerR263/image9898448385a0, web/workerR257/image90cd, V85 прежние;
для test-only пакета rebuild/restart не нужен. История R264 не переписывалась.

Полный kill отдельной delivery JVM не подменяет live SDK/client rating callback,
Spring boot/штатный platform application/launcher/всю fleet, exactly-once или S6.
Нет startup/batch scanner, разрешения unknown без receipt или operational
review/retention workflow. Оставшиеся direct/legacy/media/GENERAL/policies/AI и
полная приёмка сохраняются. Задача🟡, пять крупных блоков уточнены; S2B снятR190.

## Файлы и evidence

Два test Java, acceptance README, task detail01-278, S0 contract,
BOT_RUNTIME_CONTRACT и эта append-only запись —7 project files. Product main,
миграции, POM, Compose/env не менялись. Локальные operators/reports/fingerprints
и resource/cleanup proofs: .tmp/01-278-r265-evidence/ (не входят в git).
