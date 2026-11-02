# Bot Runtime Contract

Документ фиксирует текущий runtime contract между `spring-panel` и `java-bot`
после `Phase 5` рефакторинга.

## Launcher Contract

- основной источник правды: `app.bots.*` в `spring-panel/src/main/resources/application.yml`;
- `app.bots.launch-mode` поддерживает `auto`, `jar`, `maven`;
- `auto` сначала пытается запустить явный или найденный executable `jar`, затем
  откатывается на `spring-boot:run` как dev fallback;
- `app.bots.executable-jars` задаёт явный contract `module -> jar path`;
- `app.bots.preferred-production-launcher` фиксирует рекомендуемый production launcher;
- `app.bots.recommended-artifact-directory` задаёт рекомендуемую директорию для runtime jar;
- для production предпочтителен explicit `jar` contract, а не `target` scan.

## Runtime Inputs

Обязательные cross-platform env keys для child runtime, который запускается `bot-runner` в production `rabbitmq` contour:

- `APP_DB_MODE=postgresql`
- `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq`
- `APP_PANEL_INTERNAL_API_BASE_URL`
- `APP_PANEL_INTERNAL_API_TOKEN`
- `APP_BOT_CHANNEL_ID` — canonical positive ID записи канала панели, не внешний chat ID
- `APP_BOT_PLATFORM` — canonical `telegram|vk|max`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `TELEGRAM_BOT_TOKEN`
- `TELEGRAM_BOT_USERNAME`
- `GROUP_CHAT_ID`
- `APP_BOT_LOG_PATH`
- `SPRING_PROFILES_ACTIVE`
- `JAVA_TOOL_OPTIONS`

`bot-runner` передаёт canonical PostgreSQL datasource через защищённую production configuration. Legacy SQLite business paths (`APP_DB_BOT*`, `SUPPORT_BOT_DATABASE_PATH`) не передаются дочерним процессам и не могут быть live fallback для ticket/channel/feedback/blacklist операций.

Business operations в `rabbitmq` режиме используют queue/internal panel API boundary там, где это требуется transport contract. Прямой JDBC доступ остаётся только к canonical PostgreSQL datasource: он не создаёт локальную копию business data.

`spring-panel` больше не имеет SQLite runtime mode: `APP_DB_MODE=sqlite` отклоняется до формирования child runtime contract. Legacy `APP_DB_BOT*` / `SUPPORT_BOT_DATABASE_PATH` могут встречаться только в historical/test evidence и не являются runtime inputs для production child process.

Platform-specific:

- `VK`: `VK_BOT_ENABLED`, `VK_BOT_TOKEN`, `VK_OPERATOR_CHAT_ID`,
  `VK_WEBHOOK_ENABLED`
- `MAX`: `MAX_BOT_ENABLED`, `MAX_BOT_TOKEN`, `MAX_CHANNEL_ID`,
  `MAX_SUPPORT_CHAT_ID`, `SERVER_PORT`, `SERVER_ADDRESS`,
  `SPRING_MAIN_WEB_APPLICATION_TYPE`

Опциональные env keys зависят от платформы и network route:

- `VK_GROUP_ID`, `VK_CONFIRMATION_TOKEN`, `VK_WEBHOOK_SECRET`
- `MAX_WEBHOOK_SECRET`
- `APP_NETWORK_*`, `HTTP_PROXY`, `HTTPS_PROXY`, `ALL_PROXY`

Для live `rabbitmq` transport contour обязательны ещё и backend boundary keys:

- `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq`
- `APP_PANEL_INTERNAL_API_BASE_URL`
- `APP_PANEL_INTERNAL_API_TOKEN`

Если эти параметры не настроены, `java-bot` больше не должен молча откатываться
в local `JPA/SQLite` business-path для ticket/channel/feedback/blacklist
операций.

## Рабочий тестовый bot runtime — R267, 2026-11-02

Clean R266/d0b467204c68b205033bee0070119568ce377245, только bot-runner:
sha256:9cc3a7c69d9595094e4d372de868ea6cd33a3dc8302f327c7909d7d745cb53e1,
tag iguana-panel:account-r266-d0b467204-20261102, container9612381dedaa,
healthy/restart0/1 replica. Shared FeedbackService fix активен: неподтверждённая
запись не разрешает platform success acknowledgement, unknown не считается
доказанной недоставкой.212 tested core classes exact,211 unchanged; заменён
только FeedbackService.class в nested STORED core трёх platform executable JAR.
Все outer payloads/panel JAR неизменны; offline core package/COPY/network none,
не full Maven/Sass build.501/5 — reused source/replay proof, не новый прогон;
296 inputs/232 class hashes сверены, test-only module не включён в runtime.

Согласованный cold stop всех old children/queues0, затем fresh Telegram1/MAX2/
Telegram3;3 consumers/ready0/unacked0. Live VK не запущен.61 actual ENV/mounts
exact, HostConfig semantic exact; raw hash отличается лишь порядком binds и
empty DNS, полный исходный hash восстановлен bounded guard.5 старых Compose/
env inputs прежние. Новый runner-only overlay .tmp/01-278-r267-rollout/
compose.services.json требует preserved-environment operator.17 нетаргетных
containers exact unchanged, web/worker R257/image90cd, исходный db-migrate exited.

Свежий pg_dump custom10208054 bytes/SHA256
E0813789EB95886C2B864FABF9EAA3004AD9F1C794A5C44C2661C195EEC9068B
восстановлен в isolated PG192MiB/network none, same-host backup сохранён,
verifier container/volume удалены. Working DB не восстанавливалась. V85/85/
checksum -2092309160,14 columns/10 constraints/FK прежние; settings455/ledger17
delivered/receipt0 и accounts/grants/roots/pending/ratings/outbox/flags unchanged.
Нет migrations/backfill/flags/bootstrap/repair/purge/requeue/provider messages.

61 live access/SSE/readiness и24 signed admission GREEN, logout выполнен;
4 running JAR hashes exact, fresh runner/child ERROR/fatal0/restart0.
ReadinessUP; общий healthDOWN/inboundDLQ1 остаётся отдельным operational gate.
Rollback tag iguana-panel:rollback-r267-bot-runner-20261102-9898448385a0 сохранён
на R263/image9898, старые pointers прежние. Это не выполненный rollback;
необходимы cold stop/queues0/совместимые ledger+receipt+V85, не удаление данных.
Live SDK/client callback/in-flight application recovery/unknown review/retention/
full S6 не доказаны. Task Runtime R267/.tmp/01-278-r267-rollout/ — evidence;
следующие записи описывают исторические source/runtime checkpoints.

## Тестовый bot runtime — R264, 2026-11-02 (исторический checkpoint)

Clean R263/8dbb083206c36426d5aab229d6ae36510bf57cd3, только bot-runner:
image9898448385a0/tag iguana-panel:account-r263-8dbb08320-20261102,
containerc068584505b2/healthy/restart0/1replica. Telegram1/MAX2/Telegram3
запущены после cold stop всех прежних children/3 queues0 consumers/ready/unacked
и применения V85. Нет mixed old/new fleet. Live VK child не запущен.
212 core/72 platform frozen tested classes exact,1435 panel classes прежние;
panel JAR дополнен только V85 SQL.4 running JAR hashes exact. COPY/network-none,
не full Maven/Sass build;769/21 — reused source proof, не новый прогон.

V85/checksum -2092309160/85 successful history rows применена schema-only
Flyway10.17/one-shot Compose db-migrate --no-deps, не полным Spring boot:
нет security bootstrap/legacy normalize/repair/baseline/clean. Оригинальный
db-migrate и все17 остальных containers прежние, web/worker R257/image90cd.
61 ENV/mounts/HostConfig и5 существующих Compose/env inputs exact. Новый overlay
.tmp/01-278-r264-rollout/compose.services.json требует preserved-environment
operator, не самостоятельный Compose up с отсутствующими placeholders.

Backup восстановлен изолированно; V85 сохраняет V1–V84 history/данные, old84 SQL
validate/migrate0 на V85 проверены. Settings455/ledger17delivered/receipt0;
accounts/grants/roots/pending/ratings/outbox/flags unchanged.61 live access/SSE/
readiness и24 signed admission GREEN,3 fresh startups/3 consumers/ready0/unacked0.
Fresh ERROR/fatal0; общий healthDOWN/inboundDLQ1 прежний, operational gate не
закрыт. Нет backfill/flags/purge/requeue или внешних acceptance messages.

Rollback tag iguana-panel:rollback-r264-bot-runner-20261102-904fba5c4e40 сохранён
на R260, не выполнен. Только cold stop всех новых children/consumers + пустой
receipt journal + exact ledger/inflight + V85 compatibility. Не удалять журнал
и не восстанавливать working DB автоматически. Same-host backup сохранён,
temporary restore container/volume и migrator удалены. Старые pointers прежние.
Receipt reconciliation на replay с committed positive evidence активна; нет
startup/batch scanner, unknown без receipt, live SDK/client rating callback,
in-flight process kill/restart proof, scoped review/retention или первого S6.
Подробности/evidence — task Runtime R264/.tmp/01-278-r264-rollout/.

## Rating callback acceptance и неподтверждённая запись — source R266

RabbitMQ FeedbackService.storeFeedback теперь бросает IllegalStateException,
если PanelTicketWriteClient не подтвердил updated. Все3 platform call sites
выполняют thank-you/feedback_saved только после нормального возврата; false,
stale context и unknown HTTP не разрешают ложное подтверждение. Это не означает,
что unknown точно не записан. Void API/success/legacy JDBC path сохранены.

Изолированный prompt/receipt → shared bot service → signed read/write HTTP →
backend controllers/JPA/native V84 locks/transactional scoped UI outbox проверен:
501 executions/5 suites GREEN,242 новых (241 callback +1 unit), source/replay
независимы. Atomic rating/UI event/expiry, duplicate/concurrent replies, lost
response/idempotent retry, context drift, Unicode/bigint, HMAC/API refusal и3
SQL failure stages без new provider. Ticket owner adapter читает real JDBC rows;
это не live platform webhook/SDK/client callback или full Spring Security/TLS.
211 unrelated core classes/1435 panel exact, одна main class изменена.
В R267 выше выполнен отдельный clean-commit runner release без новых migrations/
flags; acceptance module в image не включён. Остальные
launcher/fleet/unknown review/retention/S6 gates остаются. Task Source R266/
.tmp/01-278-r266-evidence/.

## Process-loss acceptance — source R265, 2026-11-02

Test-only delivery worker запускается отдельной JVM с production listener,
dispatcher, MessagingService, ledger/receipt и signed HTTP clients. Родитель
принудительно убивает процесс после committed claim/prepare, ответа provider,
committed receipt/delivered-before-ACK. RabbitMQ автоматически возвращает исходное
unacked сообщение; новая JVM/другой PID получает redelivered=true/тот же payload
SHA256. Нет ручной публикации/replay. Positive receipt завершает delivered без
SDK/admission, unknown без receipt остаётся blocked, уже delivered/receipt/
outbox rows и timestamps неизменны, redelivery ACKed. Revoke channel/catalog и
API503 не превращают историческую квитанцию в новое разрешение доставки.

60 matrix cases на4 scope ×3 routes ×5 crash points,8 input guards,1 smoke;
323 выполнения/4 suites GREEN с прежней регрессией. Source/replay независимы,
не суммируются.212 core/1435 panel classes exact, product main не меняется;
runtime R264/V85 остаётся прежним, test-only acceptance не включать в образы.
Worker принимает только isolated loopback endpoints/owned schema/fake credentials,
не наследует Spring/provider/datasource ENV и не читает application/shared-config.
Это не live SDK, Spring boot/launcher/fleet restart, client callback, exactly-once,
startup/batch recovery, unknown review или S6. Подробности: acceptance README,
task Source R265/.tmp/01-278-r265-evidence/.

## Исторический тестовый bot runtime — R261, 2026-11-01/02

Clean R260/5731c6d483010990aebe0a35df21089defce0d9a выложен только в
bot-runner: image904fba5c4e40/tag iguana-panel:account-r260-5731c6d48-20261101,
containerccae25c43d88, healthy/restart0/1replica. Telegram1/MAX2/Telegram3
стартовали с новым core после остановки всех старых children/consumers.
Cold gate0 consumers/ready/unacked исключил mixed fleet. Web/worker остаются
R257/7b9fea55a/image90cd11900131; все17 нетаргетных контейнеров exact unchanged.
61 ENV/mounts/полный HostConfig exact; старые overlays/rollback tags сохранены.
Новый .tmp/01-278-r261-rollout/compose.services.json требует preserved ENV,
не самостоятельного Compose up с потерей process placeholders.

В трёх executable JAR заменён ровно один nested bot-core;208 tested classes и
все остальные entry payloads exact, panel JAR R257 не менялся.4 JAR hashes
проверены в образе и работающем runner. Это incremental COPY/network-none,
593/11 — прежний R260 proof, не новый прогон/full build. Live VK child отсутствует.
24 signed admission и61 access/SSE/readiness checks GREEN,3 fresh startup/3
canonical consumers, ready/unacked0. Readiness UP, ERROR/fatal0; общий health
DOWN из-за прежнего inbound DLQ1. Миграций/backfill/flags/purge/requeue и внешних
acceptance messages нет; ledger17 delivered/точный MD5 прежний.

Rollback tag iguana-panel:rollback-r261-bot-runner-20261101-d9c597493338 сохранён,
но откат не выполнен: R252 не понимает новые статусы. Нужны остановка новых
children/consumers и проверка ledger/inflight compatibility. Backup успешно
восстановлен изолированно; рабочая БД не восстанавливалась. Reconciliation по
receipt, live rating callback/customer feedback и full in-flight process restart
остаются. Этот локальный test release не является первым production S6.
Подробный checkpoint/evidence — task Runtime R261/.tmp/01-278-r261-rollout/.

## Durable provider receipts — source R263

Для новой runtime receipt path обязательна backend/Flyway V85. Она сохраняет
прежние ledger rows и добавляет integration_outbound_delivery_receipts. Java-bot
не создаёт receipt table: отсутствие схемы останавливает runtime bean до SDK.
В исходном source-only checkpoint R263 рабочий стенд оставался R261 без V85.
В R264 выше отдельный schema gate выполнен, V85 и новые children уже активны.
Это не разрешает обходить schema/backup/cold-fleet gates при новом выпуске.

Runtime dispatcher использует5-argument constructor с обязательным receipt
service; старый4-argument constructor и boolean Messaging API остаются source
compatibility contract, не новым способом отключить runtime journal. Observer
фиксирует committed nonce после durable sending и до SDK, pinned identity
проверяется после DB I/O. В journal — exact kind/route/channel/user/raw ticket/
request/platform/sender fingerprint/prompt SHA256, не prompt/token/full body.

Telegram/VK/MAX receipt adapters возвращают ProviderSendResult с подтверждённым
ID ответа и не повторяют SDK. Telegram проверяет chat+positive message_id, VK
positive SDK Integer для исходного peer, MAX — message.body.mid + точный integral
recipient.user_id из [официального POST /messages](https://dev.max.ru/docs-api/methods/POST/messages).
Compatibility default может вернуть boolean success без ID, но не создаёт
фиктивную квитанцию; legacy/support-chat sends не переделаны в receipt flows.

Положительный receipt сохраняется отдельной REQUIRES_NEW транзакцией до
markDelivered. Nonce/sender/user/полный ledger context и неизменность provider ID
проверяются; prepare/record/reconcile имеют максимум3 persistence attempts.
После восстановления service/consumer явный replay sending/uncertain с таким
receipt записывает delivered/observed accepted_at, не обращаясь к SDK/admission.
Нет автоматической разблокировки unknown без evidence, startup scan, requeue,
текущей recipient policy evaluation или выдачи новых прав. Receipt — локально
сохранённый результат ответа, не cryptographic proof и не read receipt клиента.

769/21 suites GREEN/135 новых, включая V85 Flyway/real PG fault injection,
SDK/HTTP response validation и48 recovery/unknown E2E на4 scope ×3 routes.
Source/replay независимы. При source-only R263 working migration/deploy не было;
фактическая V85/выкладка отдельно зафиксирована R264 выше, без backfill/flags/messages.
Full process kill/restart, live provider/client callback, неизвестный исход без
сохранённого receipt и operational review/retention/S6 остаются. Для будущего
релиза нужны отдельные V85 backup/rollback и coordinated fleet gates.

## Подтверждение уже известного успеха — source R262

Если provider вернул true, а запись результата или ответ commit потеряны,
markDelivered повторяет только ledger-транзакцию — максимум3 попытки, каждая
REQUIRES_NEW/READ_COMMITTED/timeout10s. Повторяются DataAccessException и
TransactionException; interrupt/ownership/programming error останавливают цикл.
SDK/recipient/sender admission/beginDelivery в этом цикле не вызываются.
Повторно найденный committed delivered с delivered_at подтверждается read-only,
без перезаписи исходных timestamps/metadata. sending → delivered — единственный
новый переход; uncertain и неизвестные результаты не переоткрываются.

625/12 suites GREEN (419 core +206 isolated PG/Rabbit/HTTP),32 новых real-PG
cases, включая потерю acknowledgement после настоящего commit и точный предел3.
Это не receipt reconciliation после process loss, не live provider/callback и
не exactly-once. Schema/DTO/API signatures прежние; source-only/no deploy,
рабочий runtime остаётся R261. Все прежние неизвестные исходы fail-closed.

## Feedback claim и неопределённый результат — source R260

INSERT ON CONFLICT DO NOTHING и CAS по status/прочитанному updated_at дают
одного claimant для наблюдавшейся retryable-генерации. Только новый pre-provider
`retryable` разрешён для явного replay с новой recipient/sender авторизацией.
Старые `failed`, processing (включая stale), sending, uncertain, delivered и
неизвестные статусы не разрешают новую отправку. Автоматического reclaim по
15-минутному таймауту больше нет; старые failed не классифицируются эвристикой.

MessagingService сохраняет processing → sending через durable hook до SDK,
независимо от caller transaction. Sender identity закреплён до admission и
перепроверен после DB I/O. Provider true + сохранённый результат → delivered;
false/exception/нет ответа после handoff → uncertain, сбой сохранения результата
может оставить sending. Оба состояния запрещают повтор. До handoff отказ →
retryable; поздний markFailed не открывает sending/uncertain/delivered.
Boolean false, в том числе локальный provider503, не универсальная гарантия
недоставки live SDK. Receipt reconciliation/операторское разрешение блокировки
не реализованы; нельзя предполагать успех/недоставку по времени ожидания.

593 executions/11 suites GREEN:387 core +206 isolated PostgreSQL/Rabbit/HTTP,
включая deterministic race/rollback и36 unknown-result cases. Это не exactly-once,
live SDK/callback/full-process/fleet/S6. Production changes только3 bot-core
services, schema/Rabbit DTO не меняются. R260 source активирован в R261 выше.
Выкладка исключила одновременную работу R252–R259 и R260 children; старый ledger
не знает новых статусов. Rollback при новых ledger rows требует отдельной
проверки, не автоматического запуска старого consumer. В самом source-пакете
R260 не было деплоя, внешних сообщений, миграций, flags или рабочей очереди;
последующий R261 deploy фиксируется отдельно.

## Изолированная feedback delivery chain — source R259

Opt-in `feedback-chain-acceptance` подключает `bot-feedback-acceptance`; обычные
четыре bot modules и runtime images не меняются. Подготовка и ограничения —
[README модуля](../java-bot/bot-feedback-acceptance/README.md).

157 unique cases GREEN: реальный panel outbox/V84, PostgreSQL и RabbitMQ с
publisher confirm, production container factory/listener/converter, persistent
delivery ledger, реальные signed HTTP clients и panel controllers/HMAC/recipient
SQL/channel JPA/credential resolver, MessagingService и loopback provider HTTP.
BUSINESS/GENERAL/SHARED_GENERAL/LEGACY × Telegram/VK/MAX; replay после consumer
restart, late revoke/expiry/ABA/rotation, API/HMAC deny, подмена child/user/prompt,
explicit retry после известного provider503. Unicode/raw IDs и bigint >2^53
сохранены, private feedbackTicketContext не уходит в broker wire DTO.

Catalog и чтение канала ботом — контролируемые seams; provider — имитатор, не
platform SDK. HTTP bridge standalone MockMvc с UTF-8, не full Boot security/TLS/
proxy. Consumer restart создаёт новый dispatcher/ledger, но не новый JVM или
launcher lease. Sequential replay не доказывает concurrent failed/stale claim,
exactly-once или recovery при неизвестном provider outcome. Live client/rating
callback/fleet/S6 ещё впереди. Рабочий runtime остаётся R258; пересборки, deploy,
миграций, изменений flags/рабочих очередей и внешних отправок в R259 нет.

## Прямые Telegram-отправки панели — source R255

ChannelTransportService (admin test, configured support/broadcast/custom send и
recipient preview) использует общий BotChannelCredentialResolver. Explicit binding
positive/unique/active/same-platform/nonblank; broken reference не берёт inline
token. Inline допустим только без binding. Каталог перечитывается для каждого
recipient и после network setup; late mismatch credential snapshot/captured
channel ID запрещает HttpClient.send. Это не lease на persisted channel и не
policy прав участников внешнего чата.

Удаление credential сохраняет ссылки каналов broken до явного rebind/clear.
Создание учитывает максимум catalog IDs и всех channel references, включая
inactive: новая запись не должна случайно занять ещё referenced ID удалённой.
Long.MAX_VALUE даёт409 без catalog write; concurrent catalog writers этим не
сериализуются. Неизвестный configured target отклоняется, без support-chat fallback.
Provider failure не считается success, internal retry/fallback нет.
GetMe/metadata и остальные direct paths в этот packet не включены.

R255 source proof:598 tests/12 suites. В R256 (2026-11-01) код выложен из clean
0df04ecf6 на web/worker image e02456c19ea4; runner/три child JVM остаются R252/
d286cd6cd/image d9c597493338. Bot JAR unchanged; panel JAR incremental, не full
Maven/Sass build.61 live access +24 signed child/backend admission GREEN,
один реальный diagnostic send через новый resolver в сохранённую Telegram-группу
подтверждён provider ok=true. Это не positive MessagingService/feedback chain
и не participant policy внешних чатов. Подробности/rollback — task checkpoint R256;
повторный deploy/migrations/flags этим документом не разрешены.


### Свежий channel delivery snapshot — source R257

testChannel/sendConfiguredMessage фиксируют transport context до выбора targets,
один на весь batch. Captured object и свежая scalar projection строки channels
сверяются до network setup и перед каждым send. Native REQUIRES_NEW/read-only/
READ_COMMITTED query не использует прежний JPA/OSIV entity или caller snapshot.
id/active/platform/token/credential binding/supportChatId/deliverySettings/
platformConfig должны совпадать; missing/mismatch/read error deny, без reroute
или retry. R255 shared credential rechecks сохранены, snapshot.toString redacted.
Catalog credentials разрешаются заново на каждого получателя, не заморожены batch.

835/15 tests GREEN,35 real PostgreSQL cases подтверждают cached REPEATABLE_READ
boundary. Provider mocked; positive feedback и реальные права участников чатов
не проверены. Нет lease/атомарности последней проверки и provider I/O, ABA/
exactly-once не обещаются. Global network config/прочие direct paths не включены.
В R258 (2026-11-01) R257 выложен из clean7b9fea55a на web/worker image90cd11900131;
runner/три child JVM остаются R252/d286cd6cd.61 live access +24 signed admission
GREEN; один diagnostic TG send подтвердил native fresh snapshot на рабочей БД
и provider ok=true. Полная feedback/revoke chain этим не проверена.
Rollback/evidence — task R258; повторный rollout/migrations/flags не разрешены.

## Привязка отправителя к provider credentials — source R252

Новые `APP_BOT_CHANNEL_ID`/`APP_BOT_PLATFORM` передаются launcher и связываются через
shared YAML с immutable `BotRuntimeSenderIdentity`. Нельзя подставлять сюда
`GROUP_CHAT_ID`/`VK_OPERATOR_CHAT_ID`; `MAX_CHANNEL_ID` уже canonical, но не заменяет
общий contract. Missing inputs/provider identity запрещают send; default метода
`OutboundMessenger.senderIdentity()` — deny, не event-derived fallback.

Telegram/VK/MAX adapters получают fingerprint именно из token фактического
provider; в новом signed POST `/internal/api/bot/outbound/authorize-sender` идут
только `channelId`, `platform`, `tokenSha256`. Перед каждой отправкой через
MessagingService backend заново читает current channel и разрешает effective
credential общим с launcher resolver. Explicit binding должен быть positive,
unique, active, same-platform, с nonblank token; missing/duplicate/inactive/foreign
binding не переключается на inline token. Inline token допустим только без binding.
Digest сверяется exact; response содержит только channelId/platform/boolean allowed.
API read-only использует прежний internal token/HMAC/body guard, не write-idempotency
cache. HTTP error/404/unavailable/malformed/mismatch/deny запрещают provider call.

После I/O повторяются local channel/proof checks: mutation/rotation во время
authorization не отправляет сообщение. Support-chat ID также не должен поменяться.
Raw provider token не передаётся в новом API; proof.toString редактирован. Прежние
recipient authorization R249 и child route R251 не заменяются sender admission.

Это закрывает credential matching для MessagingService, не direct legacy provider
calls и не права всех участников внешнего service-chat. Shared internal API/SQL
credentials не стали отдельными per-child principals; API→provider atomicity и
exactly-once/in-flight revoke не обещаются. Сначала нужны оба новых panel-web API
R249/R252, затем launcher bot-runner R252 и совместимые три bot JAR. Старый web404
или старый launcher без canonical identity намеренно запрещает отправку.

Исторический R252 candidate с тремя bot JAR и incremental panel JAR затем
развёрнут в R253 из clean d286cd6cd: web/worker/runner и3 child JVM, V84/flags
прежние. R254 подтвердил diagnostic panel→Telegram send, не feedback chain.
R256 обновил только web/worker до R255; runner/bot artifacts не менялись.
Positive feedback/revoke/replay и первый production S6 остаются отдельной приёмкой.

## Child feedback route admission — source R251

`OutboundFeedbackPromptListener` фиксирует `outbound-routing-key` и
`outbound-queue` при создании из `IntegrationRabbitProperties`. До dispatcher
проверяются positive canonical `event.channelId`, exact lowercase platform
`telegram|vk|max`, received routing key и обе настройки child process:

- `integration.outbound.feedback.prompt.<platform>.channel.<channelId>`;
- `iguana.integration.outbound.feedback-prompt.<platform>.channel.<channelId>.bot`.

Все значения должны совпасть точно с существующим canonical launcher contract:
без topic wildcard, trim, case folding, leading-zero aliases или default channel.
Null/blank/mismatch отклоняется generic exception до ledger/API/provider side
effects. Mutable properties не перепривязывают созданный listener; valid event
передаётся прежнему dispatcher один раз, его exception не глотается/не ретраится
listener. DTO/env/bindings/container DLQ/requeue policy не меняются.

Это защита от foreign/misrouted consumer delivery, не authenticated per-child API
principal и не проверка соответствия фактических provider credentials настроенному
route. Согласованно неверный launcher contract/credentials и API→provider TOCTOU
ещё требуют отдельного gate. R248–R251 source-only, runtime остаётся R246/R247;
будущим новым bot artifacts сначала нужен panel-web API R249.

## Авторизация отложенного feedback prompt — source R249

Дополнение R250 (source-only): `MessagingService` требует resolved positive
channel ID, active channel и explicit platform с exact registered messenger для
user/support-chat send. Missing/blank/unknown platform/provider не переключает
отправку на Telegram; numeric recipient IDs нельзя интерпретировать в другой
платформе. Case-insensitive registry lookup остаётся. Duplicate providers одного
normalized platform отвергаются при создании service вместо выбора credentials
порядком списка; provider false/exception не запускает другую платформу.

Feedback dispatcher проверяет ID==event.channelId/active/platform до и после
authorization call. Изменившийся за время API I/O локальный Channel запрещает
send и отмечается failed. Это не удостоверение channel/credential конкретного
child process, не блокировка PostgreSQL через provider I/O и не проверка прав
всех получателей внешних служебных чатов; эти gates остаются отдельными.
Стенд R246/R247 не обновлялся, R248–R250 пока source-only.

Новый bot-core перед `MessagingService.sendToUser` выполняет signed JSON POST
`/internal/api/bot/feedback-prompts/authorize-delivery`. Body: прежний feedback
event и полученный routing key; формат Rabbit DTO не меняется. Применяются
существующие internal token/HMAC/body digest rules. Ответ содержит только
`eventId` и boolean `allowed`; несовпадающий ID/не-boolean allow/неполный JSON/
HTTP error/API unavailable запрещают отправку. Авторизация не кешируется и не
использует write idempotency replay. Локальная БД/старое разрешение не являются
fallback. Дубликат, уже обработанный delivery ledger, API повторно не вызывает.

Backend читает private snapshot из persisted owning outbox, а не из request.
Exact event JSON/route/envelope/type/source/status `processing|published`
сверяются с V84 pending/root tuple+epoch/created_at, владельцем/активным каналом/
платформой; pending должен быть активен, raw ticket ID однозначен. `processing`
нужен для consumer, опередившего commit published marker. Историческое сообщение
без captured metadata не разрешается и не перепривязывается к сегодняшнему root.

Проверяются существующие scope switches/bindings: BUSINESS требует foundation,
active business и его active SINGLE_BUSINESS/EXPLICIT_SELECTION channel binding;
standalone GENERAL — foundation/general resolution и explicit active GENERAL
binding без business/shared conflict; shared GENERAL — foundation/shared resolution
и explicit active shared GENERAL binding без standalone conflict. Legacy unresolved
путь возможен только foundationOFF без active scoped bindings. Отсутствие бизнеса
не означает GENERAL. Это customer recipient admission, не operator grant.

После ledger claim/channel resolution бот проверяет также local active/platform,
затем обращается к backend непосредственно перед provider call. При отказе/
исключении ledger отмечается failed; действуют прежние listener retry/DLQ rules,
без новой purge/quarantine policy. Миграций и новых flags не требуется, минимумV84.

Проверка не удерживает PostgreSQL locks через HTTP/provider I/O и не обещает
atomic delivery/exactly-once/in-flight revocation. Между решением API и provider
call остаётся TOCTOU; per-child authenticated channel identity, provider retry и
сквозная приёмка всех платформ — отдельные gates.

Runtime R247 пока остаётся на прежних артефактах. Для будущего обновления сначала
нужен panel-web с этим API, затем проверенные bot artifacts/supervisor. Новый бот
со старым web получает404 и намеренно не отправляет prompt. Source work R249 не
выполняет rollout, не меняет flags и не разрешает миграции/очистку очередей.

## Readiness Contract

- startup timeout задаётся через `app.bots.startup-readiness-timeout`;
- poll interval задаётся через `app.bots.startup-poll-interval`;
- success signal: Spring Boot `Started ...Application in ...`;
- failure signal: banner `APPLICATION FAILED TO START`.

Если success marker не найден до timeout, panel считает запуск неподтверждённым
и завершает процесс как startup failure.

## Diagnostic API

Для проверки контракта без реального старта используется endpoint:

- `GET /api/bots/{channelId}/runtime-contract`

Он возвращает:

- модуль бота;
- configured launch mode;
- resolved launcher kind;
- источник артефакта (`explicit-config`, `target-scan`, `maven-fallback`);
- путь к executable jar, если найден;
- required/optional environment keys;
- warnings по текущему launcher contract;
- readiness expectations;
- production readiness и blocking reasons;
- lifecycle expectations (`running/stopped/error`, startup/timeout behavior).

Diagnostic payload должен также явно показывать DB boundary:

- explicit `APP_DB_MODE=sqlite` у панели отвергается до runtime diagnostics; diagnostic contract проверяет canonical PostgreSQL backend и отдельно сигнализирует неподдержанный non-PostgreSQL external vendor;
- production-ready статус для bot runtime допустим только при canonical PostgreSQL backend + `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq` + `APP_DB_MODE=postgresql`;
- child process получает canonical datasource через production configuration; прямое хранение business data в local SQLite считается нарушением production boundary.
- normal runtime панели является PostgreSQL-only; SQLite panel contract отсутствует, а legacy SQLite остаётся за пределами live runtime.
- per-channel `bot-<channelId>.db` больше не считается допустимым live runtime-path: legacy shard-файлы являются historical/test evidence и не имеют first-party live import path в текущем runtime.
- в `APP_INTEGRATION_TRANSPORT_MODE=rabbitmq` bot-side business reads/writes должны идти через internal panel API или queue boundary; silent fallback в local business storage больше не считается допустимым live поведением.

## Production Recipe

Рекомендуемый production path сейчас такой:

1. Собрать prebuilt runtime jar для каждого bot module.
2. Положить артефакты в `dist/` внутри `java-bot` или в другой заранее известный каталог.
3. Заполнить `app.bots.executable-jars` явными путями к jar.
4. Оставить `app.bots.launch-mode=auto` или `jar`.

Production-ready contract считается выполненным, когда:

- canonical panel runtime работает на PostgreSQL;
- child transport явно равен `rabbitmq`;
- child database mode равен `postgresql` и использует canonical datasource;
- launcher резолвится в `jar`;
- executable jar найден;
- jar взят из `explicit-config`, а не через `target` scan;
- panel не зависит от `spring-boot:run` для боевого запуска.

## Dynamic Supervisor

В Docker production contour процесс `bot-runner` имеет роль `APP_RUNTIME_ROLE=bot-runner` и является единственным supervisor для всех активных каналов. После старта он читает их конфигурацию из PostgreSQL и поднимает отдельный prebuilt runtime JAR для каждого канала, поэтому число Telegram, VK и MAX-ботов не ограничено Compose services.

- `bot-runner` должен иметь ровно одну реплику; `panel-web` и `ops-worker` масштабируются независимо.
- Для Telegram Redis lease строится по SHA-256 fingerprint токена; для MAX/VK ownership привязан к channel. Это исключает логирование token в coordination keys.
- Canonical production compose не содержит static `bot-telegram`, `bot-vk` или `bot-max`. Emergency compatibility services находятся только в `docker-compose.production-legacy-bots.yml`; guarded helper отказывает в start при работающем `bot-runner` и откатывает legacy start, если runner появляется конкурентно.
- Child runtimes обращаются к internal panel API по `http://panel-web:8080`. Клиент повторяет только временные transport failures и HTTP `5xx`, с ограничением существующими `retry-attempts` и `retry-backoff`; write requests сохраняют один idempotency key.
- Ручные `start/stop` из web UI не создают child JVM в `panel-web`: web-роль отправляет signed + idempotent internal command на `http://bot-runner:8080/internal/api/bot/runtime/{channelId}/{action}` и принимает результат только при совпадающих `commandId`, `channelId`, `action` и непустом `runnerInstanceId`.
- Internal lifecycle endpoint выполняет команду только в роли `bot-runner` (или локальной `all`), поэтому ownership процесса остаётся single-owner.
- Изменение числа или набора каналов не требует добавления Docker service. Изменения токена, активности либо module configuration вносятся через settings/API и подхватываются supervisor lifecycle.

## Lifecycle Contract Test

В test-layer есть адресный lifecycle contract test, который:

- собирает runnable test jar;
- запускает его через `BotProcessService`;
- подтверждает `running` после readiness marker;
- проверяет `stop/status` и cleanup pid-файла.

## Remaining Gaps

- Нужен полноценный automated end-to-end process contract test: создать несколько каналов, проверить запуск, остановку и замену token без duplicate lease.
- Перед каждым go-live остаётся обязательной ручная проверка доставки входящего текста и media в каждом реально подключённом внешнем канале.
- Emergency static compatibility contour остаётся ручным recovery-инструментом и должен сохранять source-contract coverage для pre/post ownership guard.
