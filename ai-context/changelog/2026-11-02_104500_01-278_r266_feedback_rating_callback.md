# 01-278 R266: callback оценки и запрет ложного подтверждения

Время проверки source/replay: 2026-11-02 10:42–10:46 MSK.

Промпт пользователя: «делай» — продолжение после R265, изолированная приёмка
ответа клиента на запрос оценки; внешние клиентские сообщения не отправлялись.

## Выполнено

- Проверены clean HEAD/origin/main/actual remote
  f1c02b1faee83a84d88e6f9d26f78f3c9a024318 (R265,parentae1743e51), scope7 и
  точные applied manifests предыдущего пакета.
- Обнаружен product defect: bot FeedbackService при backend false только писал
  warning и нормально возвращался, позволяя Telegram/VK/MAX выполнить success
  acknowledgement. Новый unit RED6/1 failure/0 errors → GREEN6. RabbitMQ path
  теперь бросает IllegalStateException "not confirm" до thank-you/feedback_saved.
  Void API/подтверждённый успех и legacy JDBC behavior прежние. Unknown HTTP
  не трактуется как доказанная недоставка/отказ, успех не выдумывается.
- Добавлены FeedbackRatingCallbackFixture/Test: real prompt/outbound ledger/
  receipt → shared bot FeedbackService → signed PanelTicketRead/Write HTTP →
  production backend controllers/services. Настоящие JPA pending/feedback repos,
  native V84 immutable context/owner locks, AOP/JpaTransactionManager и JDBC
  scoped UI outbox работают одной транзакцией. Узкий Ticket repository adapter
  читает real JDBC owner rows; provider/неполный bootstrap — тестовые границы.
- 241 callback execution:4 scope ×3 routes; round trip/duplicate/reload,8
  изменений контекста после чтения, ratings0/6, lost commit response с idempotent
  retry,3 SQL failure stages, concurrent replies, HMAC401/API503, wrong-user/
  channel lookup и1 smoke. Ровно1 feedback/1 scoped UI event/expiry; точный
  Unicode/raw ID/bigint, отсутствие новых provider calls, delivery rows exact.
- Всего501 execution/5 suites GREEN:6 core +194 chain +12 ledger +48 recovery
  +241 callback;242 новых. Source/replay независимо GREEN, не1002 теста.
  Старый RepeatedTest(5) ledger race учтён как5 выполнений,497 XML names.
  R265 process-kill suite и все769 R263 source tests здесь не повторялись.
- 296 compiled inputs/232 bytecode files frozen и source/replay exact. Только
  FeedbackService.class изменён:211 unrelated core classes и1435 panel classes
  exact. Нет full Maven/assets rebuild, новой SQL migration, POM/DTO/Compose/env.
- Первый CLI probe исправлен quoting dotted Maven property для PowerShell.
  Callback smoke потребовал JavaTimeModule как в Boot и передачи исходных JSON
  bytes вместо MockMvc default ISO-8859-1 string decoding. Unicode assertions,
  транзакции и негативные проверки не ослаблялись. SQL P0001/HTTP401/500/503
  намеренно injected только в disposable fixture, не ошибки рабочего стенда.
- Owned PG192/Rabbit256MiB/1CPU без OOM/restart, собственные схемы и контейнеры/
  одноразовые данные удалены по exact ID/label guards.18 working containers,
  config/ENV/БД V85/settings455/accounts/grants/roots/pending/ratings/outbox/
  ledger17delivered/receipt0/queues unchanged, readinessUP; inboundDLQ1 прежний.
  R264 rollback image и backup SHA проверены, rollback не выполнялся.

## Не доказаны и остаток

Это source-only functional fix, не rollout. Runtime R264 остаётся на runnerR263/
image9898448385a0 и web/workerR257/image90cd. Исправление потребует отдельного
clean-commit bot-runner release; новых migrations/flags нет, acceptance module
нельзя включать в runtime image. Нет working migration/backfill/flags, provider/
customer sends, deploy/staging/commit/push.

Shared callback acceptance не подменяет реальный SDK/webhook/client flow,
полный Spring Security/TLS, UI/SSE delivery или отдельные child transport
principals. Launcher/fleet restart, unknown review/retention/startup recovery,
direct/legacy/media/GENERAL/policies/AI и первый S6 остаются. Задача🟡, пять
крупных блоков уточнены; исторический S2B снятR190.

## Файлы и evidence

FeedbackService/main+unit,2 новых callback test Java, acceptance README,
task detail01-278/S0/BOT и эта append-only запись —9 project files. Локальные
operators/reports/frozen manifests/resource/cleanup/runtime proofs:
.tmp/01-278-r266-evidence/ (не входят в git).
