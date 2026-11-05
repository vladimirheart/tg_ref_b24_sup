# 01-278 / R286 — MAX published followup media и outbox replay

Промт пользователя:

> делай

Дата:2026-11-05, Europe/Moscow +03.

Клиент MAX подключён к backend R285: прямые document/photo/audio/video batches
для опубликованного v4 обращения, BUSINESS и GENERAL, включая отсутствующую
session. Signed /questionnaire-media/max-batch допускается только с version3,
exact channel/user/raw ticket, корректным nullable owner/template/catalog/quota.
Текущий catalog/binding не подставляется. Сохранённая выбранная session при её
наличии обязана совпадать с canonical admission; chat mismatch проверяется до
outbox lookup и любого download. Unknown canonical root по-прежнему fail-closed.

Весь batch1–10 описывается до I/O; owned writer R283 сохраняет actual bytes
1байт–10MiB в frozen MAX namespace. После всех файлов один versioned batch
уходит через прежний transactional TicketService в SQL outbox с MAX route,
deterministic event ID и positive BIGINT provider ID, включая Long.MAX_VALUE.
History/metadata пишет только backend R285 после whole-batch verification.
Forward/reply/opaque/mixed/unknown формы явно отклоняются; подпись не становится
текстовым ответом, warning обязан быть подтверждён. Legacy downloader/shared
service-chat/single-message writer не являются fallback для v4.

Durable pending здесь — существующий SQL outbox, а не новый session JSON или
второй Redis journal. До нового admission/download читается committed исходный
batch по channel/user/provider, независимо от current root и потери session/
transport dedup. queued/processing/failed/published означают уже сохранённый
payload, не подтверждённую backend/provider доставку. Retry только ACK исходного
ticket: timestamp/profile/caption/items не пересоздаются, dispatch не запускается
и status не меняется. Исходный initial-media event также распознаётся по scoped
history, чтобы replay первого вопросника не стал новым followup. Ambiguous/
corrupt journal закрывает путь; legacy initial keys не перехватываются SQL filter.
До committed outbox возможен повтор download; session CAS при followup не нужен,
так как состояние вопросника не меняется. Partial storage до отказа не означает
частичную history/outbox acceptance; orphan bytes отдельно не удаляются.

884 fresh JVM tests/40 suites GREEN,80 новых; skips/errors/failures0. Frozen
baseline80:26 assertions +8 expected missing-capability/old-writer exceptions,
46 controls;4 final test source hashes/5 class hashes/case names exact RED→GREEN.
Включены signed loopback HTTP/HMAC и raw ticket encoding, real controller/writer/
filesystem, real outbox serialization+publisher с in-memory JDBC fixture,
commit-before-exception replay после session/dedup loss без изменения payload,
existing MAX/Telegram/VK capability/transport/legacy/session/source-layout tests.
Это не real PostgreSQL JSONB/transaction/concurrent quota proof, Redis/Rabbit/
provider/CDN/S3, full JVM kill или S6. Перед release нужны isolated PostgreSQL/
Rabbit outbox→inbox и concurrent quota/rebind/uncertain-result/drain gates.
Existing tests не ослаблялись. В новом fixture исправлены chat setup и пустые
test Rabbit routes; failed logs/sources сохранены. Первый GREEN-кандидат выявил
прежний chat-before-I/O contract: исправлен product order, не старые tests.
R279 bot cache hash-verified, все stale Java inputs принудительно инвалидированы;
fresh baseline compile143 core/60 core tests/23 MAX/32 MAX tests. Не package.

Fresh HEAD/origin/main/actual remote7aa9642d4 (R285), parentf93973310;10 prior
paths/content сверены с Git blobs и сохранённой R285 source-копией. Detached
worktree, repository Maven wrapper/JDK17. Scope14:6 bot main,4 new tests,
task/S0/BOT/new append-only changelog. POM/schema/flags/session JSON неизменны.
Root apply отдельно от staging/commit/push/deploy; push остаётся пользователю.

Source/test machine:C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий экземпляр
проекта и Docker — на другой машине/в другой сессии; здесь они не измерялись
и не изменялись, их текущий state неизвестен. Docker/PG/Redis/Rabbit/provider
не запускались. R280 release/health, R281 cleanup и stopped20 containers R282
сохранены как исторические факты исходной runtime-сессии. Новых migrations/
flags нет, V83/V84/V85 не повторять. Для включения нужен coordinated backend
R284/R285 before bots R284/R286 release с preserved ENV/rollback, не bare restart.
Live provider acceptance, legacy/recipient/child principals/global policies/AI/S6
остаются; S2B снятR190, общий статус задачи🟡.

Изменённые файлы:

- `ai-context/changelog/2026-11-05_01-278_max_followup_client_r286.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `docs/BOT_RUNTIME_CONTRACT.md`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/InboundClientMessagePublisher.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/IntegrationTransportOutboxService.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/PanelRuntimeConfigClient.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/RuntimeConfigService.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/TicketService.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/MaxFollowupAdmissionTest.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/MaxFollowupOutboxTest.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/MaxFollowupPublisherTest.java`
- `java-bot/bot-max/src/main/java/com/example/supportbot/max/MaxWebhookController.java`
- `java-bot/bot-max/src/test/java/com/example/supportbot/max/MaxPublishedFollowupMediaTest.java`
