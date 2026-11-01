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
Source-only: runtime R256 по-прежнему R255/0df04ecf6 на web/worker и R252 на runner.
Отдельная выкладка требуется; migrations/flags этим контрактом не разрешены.

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
