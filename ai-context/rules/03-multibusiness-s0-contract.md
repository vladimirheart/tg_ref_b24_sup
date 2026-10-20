# S0-контракт: независимые бизнесы Iguana

## Статус

S0-контракт от `2026-10-10`; S1 source foundation подготовлен и проверен в
изолированной PostgreSQL-схеме. Документ фиксирует технический контракт,
который должен быть реализован по стадиям задачи `01-278`; он не разрешает
запуск Flyway-миграции, backfill, изменение production/runtime или внешних
систем.

Требующие бизнес-решения пункты помечены **PENDING**. Пока они не утверждены,
код не должен подставлять бизнес по текстовому имени, каналу, последнему выбору
оператора или иной эвристике.

## Среда выполнения и сессии

Уточнение R137 (2026-10-19): по уточнению пользователя рабочий Docker
и весь запущенный проект находятся на другой локальной машине, в другой сессии
агента относительно source/test-сессии R137. Docker/image observations R135/R136
относятся к той runtime-сессии; слова «на этой машине» из неё не переносятся в
текущую. Runtime revision/DB/flags/rollback отсюда не проверены. Местный Docker
допустим для изолированных Testcontainers и не доказывает состояние рабочего
экземпляра. Одинаковый путь checkout не идентифицирует машину или сессию.

## Зафиксированные продуктовые решения

- Бизнес — самостоятельная доменная сущность, а не iiko-объект, юридическое
  лицо, канал или текстовое поле локации.
- `Суши` и `Блины` создаются как обычные предустановленные записи с устойчивыми
  `code`; новое направление не наследует их обращения, вопросы и шаблоны.
- У одного сотрудника одна учётная запись `users.id`; доступ может быть к
  одному, нескольким или всем бизнесам.
- Режим «Все доступные» объединяет только уже разрешённые business IDs и не
  расширяет полномочия.
- Изоляция проверяется на сервере и в SQL read/write paths. UI filter,
  выбранный browser-local context либо название бизнеса не являются authority.
- PostgreSQL остаётся единственным relational source of truth, а Flyway и
  canonical business schema принадлежат `spring-panel`.
- Для ручного бизнес-обращения без бота/интеграции создаётся локальный клиент с новым
  ID, принадлежащий ровно одному выбранному бизнесу. Это не аккаунт `users.id`
  и не клиент внешнего канала. Ручной путь использует отрицательный `user_id`,
  выделенный отдельной последовательностью; ноль недопустим. Новый `ticket`
  получает явный `business_id` до записи корня, без вывода из legacy текста.
- В общем канале человек явно выбирает бизнес **или вариант «Без бизнеса» до первого вопроса**.
  `business_id` не выводится из названия локации, текста, последнего выбора
  оператора или факта привязки канала. Пока выбора нет, вопросник не начинается;
  бизнес-ответ сверяется с активными привязками канала, а выбранный scope
  сохраняется с сессией/обращением и переносится через transport/replay.

## Свежий baseline и проверенные факты

Аудит выполнен на `main`/`origin/main`
`08c1bd0add49cd39d9d23dd6240d27f985607f23`; рабочее дерево до S0 было чистым.
Предыдущий commit `da969404009276263c9b51bc362ce233ea6a7cf0` не содержит
мультибизнесовой реализации.

Подтверждено чтением текущего source и PostgreSQL migration chain V1–V50:

- `tickets` имеет ключ `(user_id, ticket_id)`, но `messages.ticket_id` пока
  уникален, а большинство дочерних read/write paths принимает один `ticket_id`.
  В частности, так работают `DialogLookupReadService`, bot runtime read/write,
  `UiEventOutboxWatcher` и часть task links. Новая модель не вправе считать
  строковый `ticket_id` вечным глобальным ключом.
- `ConversationTicketCreationIngestionService` в одной транзакции создаёт
  root `messages`, `tickets`, историю, attributes, active/span records; в
  payload есть только legacy `business` string. Поэтому scope обязан быть
  определён до создания root ticket/message и сохранён в event envelope.
- `DialogLocationBusinessApiController` меняет `messages.business` по одному
  `ticket_id`, а `DialogAuthorizationService` проверяет `PAGE_DIALOGS` и
  глобальные action permissions. Ни один из них пока не проверяет business
  scope.
- Аутентификация использует `users.id`; `user_authorities` и `roles` дают
  глобальные page authorities. `panel_users` не является отдельным
  каноническим account store. Business-role нельзя выводить из `ROLE_ADMIN` или
  из права на страницу.
- `channels.question_template_id` и `channels.questions_cfg` существуют,
  однако runtime берёт общую canonical `bot_settings` конфигурацию. В
  `SettingsParameterService` и `IikoDepartmentLocationCatalogService` всё ещё
  есть текстовые aliases и специальные имена двух legacy-бизнесов. Это
  compatibility input, но не будущий business identity.
- `ticket_attributes` хранит answers по `(ticket_id, question_id)`;
  `tasks`/`projects`/boards и `notifications` не имеют business scope;
  `ui_event_outbox` несёт `ticket_id` и `channel_id` без business ID.
- Ранее зафиксированный read-only data audit остаётся входным evidence: 47
  tickets и root messages, 619 history, 108 attachment metadata, 44 непустых
  legacy business labels, 3 пустых labels, 2 mixed channels из 3 и 3 history
  channel mismatch. Значения aliases не переносятся в этот открытый документ.

## Каноническая модель данных

### Бизнес, участник и полномочия

```text
users 1 ──< business_memberships >── 1 businesses
users 1 ──< business_access_grants
businesses 1 ──< business_channel_bindings >── 1 channels
businesses 1 ──< business_locations
businesses 1 ──< tickets
```

### Предлагаемые новые сущности

| Сущность | Обязательные поля и ограничения | Назначение |
| --- | --- | --- |
| `businesses` | identity PK, immutable unique `code`, display name, `status` (`active`/`archived`), timestamps, optional JSON settings | Единственный canonical business identity. Предустановленные записи получают codes `sushi` и `bliny`; display names не используются как ключи. |
| `business_memberships` | `user_id` FK `users`, `business_id` FK, `role_code`, `permission_overrides`, active state, unique `(user_id, business_id)` | Явный доступ и функциональная роль пользователя в одном бизнесе. |
| `business_access_grants` | `user_id` FK, `scope=ALL_BUSINESSES`, `role_code`, active state, один active grant данного scope на пользователя | Динамический доступ ко всем текущим и будущим businesses. Это не `ROLE_ADMIN` и не доступ к global/system data. |
| `business_channel_bindings` | `channel_id` FK, `business_id` FK, `resolution_mode`, active state, unique `(channel_id, business_id)` | Разрешает одному каналу обслуживать несколько бизнесов без неявного default. |
| `business_locations` | identity PK, `business_id` FK, display fields/status, unique key только внутри business | Позволяет одноимённым локациям существовать в разных бизнесах. Маппинг iiko хранится отдельно. |
| `external_location_mappings` | `business_location_id` FK, source type, external ID, unique `(source_type, external_id)` | Привязка iiko/CRM и иных источников к существующей локации, а не создание business identity из внешнего значения. |

`role_code` определяет только функциональные capability в business context
(`VIEW`, `OPERATE`, `MANAGE`, `CONFIGURE`); его точный vocabulary и relation с
глобальными page permissions должны быть утверждены до S1. Effective permission
всегда равен пересечению page authority, membership/all-business grant и
business capability. `ALL_BUSINESSES` добавляет data scope и базовую
business-role, но не system-admin capability.

### Tickets и дочерние записи

- Бизнес-обращение имеет canonical owner `tickets.business_id`. `NULL` в этом
  поле сам по себе означает только legacy/unresolved, но не подтверждённый
  отказ от бизнес-привязки. Намеренно общее обращение имеет отдельный признак
  `tickets.is_general=TRUE`, `business_id=NULL` и сохранённый источник
  `USER_SELECTION` (общий канал) либо `GENERAL_CHANNEL` (явно настроенный
  канал без бизнесов). Неопределённый scope нового ticket — отказ/ручная очередь,
  а не запись с guessed default.
- `messages.business` остаётся legacy display/compatibility metadata и не
  участвует в авторизации. Нельзя удалять или переписывать это поле в S1.
- S2A фиксирует новый public route key как отдельный immutable
  `ticket_public_id`: 32 lowercase hexadecimal symbols, уникальный только для
  non-null значений. Legacy routes не расширяются и остаются в подтверждённом
  compatibility window; internal callers без public ID обязаны нести полный
  `(user_id, ticket_id)` tuple. Bare `ticket_id` не считается глобальным ключом.
- S2A также предоставляет только read-only preview legacy assignment: он
  сравнивает `messages.business` с `business_legacy_aliases.alias` побайтно,
  без trim/lowercase или иных догадок. `UNRESOLVED`, duplicate mapping и
  conflict остаются отдельными состояниями; сервис не меняет `tickets` и не
  является backfill.
- Child records получают business scope через canonical ticket. Там, где
  таблица хранит только `ticket_id`, этап S2 обязан добавить/вывести достаточный
  tuple или public reference до включения strict filtering. `chat_history`
  принадлежит ticket, а не своему историческому `channel_id`.
- Изменение business у существующего ticket — отдельная audit-only privileged
  операция после проверки зависимостей. Смена UI filter не меняет owner data.

### Каналы и вопросники

`business_channel_bindings.resolution_mode` допускает только:

- `SINGLE_BUSINESS` — канал связан с одним бизнесом;
- `EXPLICIT_SELECTION` — пользователь сначала выбирает бизнес;
- `VERIFIED_EXTERNAL_MAPPING` — business получен из подтверждённого external
  mapping;
- `VERIFIED_LOCATION_MAPPING` — business получен из однозначного approved
  mapping локации.

При нескольких кандидатах, отсутствии кандидата или conflict runtime должен
создать управляемое `UNRESOLVED` состояние/ручное назначение и не начинать
question flow. Business, template version и selection source сохраняются с
ticket до конца retries, edits, feedback и replay.

Вариант `GENERAL` — самостоятельный сценарий без бизнеса, а не разновидность
`UNRESOLVED` или `GLOBAL_SYSTEM`. В общем канале он выбирается явно; в
предварительно настроенном канале только для общих обращений выбор не нужен.
Для него используется отдельный от
business-scoped шаблон из canonical `bot_settings.question_templates`:
произвольный набор вопросов, типов и порядок редактируются в редакторе
вопросников; бизнес-локации и бизнес-presets не подмешиваются. В общем канале
вариант «Без бизнеса» должен выбирать этот шаблон до первого вопроса.
Персоналу такие обращения нужны в отдельной явно разрешённой очереди; grant
`ALL_BUSINESSES` сам по себе не разрешает их читать или изменять.

Read-only очередь GENERAL использует отдельный явный grant
`general_ticket_read_grants` на канонический `users.id`, без автоматических
назначений. Доступ требует одновременно `PAGE_DIALOGS`, единственную canonical
учётную запись с enabled=TRUE/is_blocked=FALSE и активный READ grant. R134
проверяет уникальность case-insensitive username также при чтении, не выбирая
произвольную enabled запись из нескольких. `ROLE_ADMIN`, business membership и
`ALL_BUSINESSES` не заменяют этот grant. Установка/отзыв доступны только
системному администратору с `PAGE_SETTINGS` и CSRF. READ не разрешает отвечать,
менять статус либо выполнять другие write operations.

R135 вводит отдельный opt-in general_ticket_operate_grants: OPERATE действует
только вместе с current READ, не наследуется из ROLE_ADMIN/ALL_BUSINESSES и
никому не назначается миграцией. Системный admin с PAGE_SETTINGS/CSRF может
выдать его текущему READ account; отзыв READ атомарно отзывает OPERATE, возврат
READ сам по себе работу не восстанавливает. Новый write scope этого этапа —
только GENERAL triage pending↔open. Это не resolve/reopen/close, не назначение
ответственного и не отправка клиенту; legacy lifecycle/feedback не вызываются.

Страница /general-dialog-work/{publicId} требует PAGE_DIALOGS и current READ;
POST также OPERATE и настоящий CSRF. Public locator сервером разрешается в
expected GENERAL tuple/generation; raw owner не экспортируется в model.
V65 добавляет general_work_revision, автоматически увеличиваемую PostgreSQL
при смене GENERAL status/owner/scope даже другим writer, и запрещает её ручную
правку. CAS проверяет revision/status, exact owner, unique raw ID, current
account/READ/OPERATE в финальном UPDATE. В одном SQL атомарны status, immutable
metadata audit и V61 GENERAL outbox ticket_status_changed; ошибка любого INSERT
откатывает всё. Новых grants, классификации/backfill старых owner или runtime
flags миграция не создаёт. Readiness требует V65 schema до объявления READY.
На этапе R135 GENERAL reply/edit/delete/resolve/reopen/assignment оставались
PENDING; добавленный в R136 текстовый reply path описан ниже.

R136 добавляет отдельный текстовый reply path для GENERAL pending/open.
PAGE_DIALOGS, current READ + OPERATE и CSRF обязательны. Уникальный request key
привязан к frozen tuple/public ID, actor, revision, тексту и SHA-256 destination
snapshot; его повтор не создаёт вторую отправку.
Форма несёт fingerprint исходного destination; перенастройка канала до
POST требует перечитать форму, а не принять новый destination молча.
QUEUED→SENDING фиксируется до сети. Отправку инициирует только этот POST;
фонового sender/автоматического replay очереди в R136 нет.
После claim повторно проверяются root message/channel, account/grants,
revision и destination; блокировки этих rows удерживаются до результата.
Непосредственно перед HTTP send транспорт снова вызывает guard. Неизвестная
платформа не превращается в Telegram; credentials в outbox не сохраняются.

SENT и canonical history/public generation, metadata audit и GENERAL outbox
фиксируются одной транзакцией. При timeout/неясном результате — UNKNOWN;
crash/ошибка сохранения оставляют SENDING/UNKNOWN для ручной сверки, без
автоматического retry и без обещания exactly-once у внешнего провайдера.
Повторный POST того же request key не отправляет SENDING/SENT/UNKNOWN заново.
Очередь/состояния видны только через текущий GENERAL READ и exact public owner.
V66 additive, без backfill/grants. Новая история несёт ticket_public_id;
NULL старых rows не классифицируется автоматически. Reply media/edit/delete,
resolve/feedback, activity/span attribution, reconciliation/retention policy и provider E2E остаются gates.

R137 открывает ручные GENERAL pending/open→closed и closed→pending. Прежний
pending↔open сохранён. Во final SQL действуют те же owner/account/READ/OPERATE,
expected status/revision и новый V67 general_ticket_transition_allowed gate.
Close фиксирует resolved_at/resolved_by/closed_count; reopen очищает resolution,
повышает reopen_count/last_reopen_at и возвращает в pending. V65 увеличивает
revision; старую форму нельзя применить после close/reopen цикла. Status,
metadata/counters, immutable audit и GENERAL outbox атомарны; partial failure
откатывает всё. V67 меняет только allowed transition CHECK, сохраняет старые
records/grants и immutable trigger; readiness требует новую function schema.
Закрытый ticket не принимает новый reply, старый queued reply теряет revision;
reopen не включает retry. Close сериализуется с уже guarded network attempt
через существующие reply row locks. Legacy lifecycle/feedback/assignment,
GENERAL activity/span attribution и reconciliation остаются отдельными scope.

R138 добавляет наблюдаемые интервалы работы GENERAL, без attribution старых
ticket_spans/ticket_active/work_time_total_sec. Только новый manual pending→open
создаёт typed interval полного user/raw/public owner с revision/actor/time
snapshot. При open→pending/closed учитывается время только при совпадении
opened revision с прежним root revision и неубывающих часах. Внешний status/
owner/scope ABA и clock regression исключают интервал из итога (UNCONFIRMED
при чтении, INVALIDATED при следующем переходе того же полного owner).
Разные owner/public generation не наследуют интервалы. Старый open root без
observed start не получает выдуманный duration. Это частичный учёт.

V68 additive: unique OPEN для full tuple, immutable start/identity/terminal
snapshot, sequential close-before-insert helper внутри authorized root CAS.
Root status/metadata/counters/revision, interval, audit и GENERAL outbox атомарны;
failure любого шага откатывает всё. Новых grants/backfill/flags нет; readiness
компилирует table и helper probe WHERE 1=0 без выполнения writer.
Чтение summary/последних 10 повторно требует current READ/account/flags и exact
explicit GENERAL/NULL business unique owner до aggregate/order/limit. Только
COMPLETED входят в FLOOR суммы точных seconds; незавершённый интервал показан
отдельно. UI явно сообщает partial tracking, raw IDs не выдаёт.
Legacy attribution, session/activity heartbeat, assignment, reconciliation/
retention и runtime acceptance на другой машине/в другой сессии остаются gates.

R139 добавляет отдельное ручное GENERAL assignment pending/open: назначение,
смена и снятие ответственного через PAGE_DIALOGS + current READ/OPERATE + CSRF.
Target обязан быть canonical unique enabled/unblocked account с GENERAL
READ+OPERATE. Candidate pool закрыт для READ-only; page authority target по-прежнему
нужна для его входа в «Диалоги», назначение само не выдаёт доступ/grants.
V69 — append-only scoped assignment events с exact user/raw/public tuple,
actor/assignee snapshot и отдельной assignment revision. Новых grants/backfill
и переноса ticket_responsibles нет. Current/history требуют READ и exact owner
до latest/limit; owner/generation не наследуют старое назначение. Утративший
права target показан недоступным, автоматического переназначения нет.

Root FOR UPDATE сериализует assignment CAS с status/owner и другим assignment;
account/grants actor и target удерживаются FOR SHARE до commit. Проверяются
expected work revision, assignment revision, explicit GENERAL и unique raw ID.
Event и GENERAL assignment_changed outbox атомарны; stale/ABA/no-op отказ не
создаёт event. Root status/work revision не меняются, interval/reply не прерываются.
Closed assignment — read-only. Readiness требует V69; missing schema не открывает
legacy fallback. Metadata-only invalidation идёт через отдельный GENERAL
SSE/Redis/current READ path без legacy callbacks. Автоназначение/балансировка,
историческая attribution, bell/служебная доставка и retention остаются gates.

R140 открывает отдельное ручное GENERAL creation без бизнес-привязки и канала.
Нужны PAGE_DIALOGS, current GENERAL READ+OPERATE/account и одновременно default-off
foundation/general-ticket-queue/manual-ticket-creation. Форма не принимает
business/client ID: новый local client из manual_general_clients получает
отрицательный ID общей non-cycling V54 sequence с проверкой занятых namespaces.
Business-local registry остаётся отдельным. Ticket создаётся pending с revision 0,
explicit GENERAL/NULL business и MANUAL source, без legacy spans/active records.
V70 source allowlist сохраняет USER_SELECTION/GENERAL_CHANNEL, допускает MANUAL
только в GENERAL и сохраняет non-general source=NULL. Старые rows не меняются,
новых grants/backfill нет; bot transport allowlist не расширен.

Request key привязан к actor ID/input SHA-256 и immutable exact tuple/public ID.
Same-key запросы сериализуются advisory lock; account/grants удерживаются FOR SHARE
до commit и проверяются в client INSERT. Повтор возвращает прежний public ID
только при current exact GENERAL MANUAL owner/root/registry и том же actor/input;
при owner/generation/source changes, revocation, missing root или collision
нет дубля/нового owner capture. Client/root message/ticket/operator_note history/
request audit/GENERAL ticket_created outbox атомарны. Строковые SQL inputs digest
явно CAST text и проверены в default/unspecified JDBC mode. No provider/AI/legacy
callbacks; interval возникает только при последующем manual переводе в работу.
UI предупреждает о локальном клиенте и отсутствии канала отправки; redirect
содержит только public ID. Readiness требует V70 tables/function. Retention/
reconciliation keys, media/feedback, auto-assignment и live acceptance остаются gates.

R141 добавляет отдельные Telegram EDIT/DELETE только для подтверждённых R136
GENERAL text replies. Нужны PAGE_DIALOGS/current READ+OPERATE/account, CSRF,
foundation/general-ticket-queue и pending/open exact owner с unique raw ID.
Canonical history имеет exact tuple/public generation, sender operator,
operator_message, без attachment; старые/NULL-generation/client/note rows
не классифицируются. Source reply/destination/provider ID и text/timestamps
фиксируются в форме/immutable command V71. Смена actor/input/snapshot/owner/
work revision/channel/config закрывает запрос без recapture. Reply/history
update locks и root/ticket/channel/account/grant share locks сохраняются через
provider call; transport повторяет guard прямо перед HTTP и использует frozen
channel. Старые transport entrypoints остаются совместимыми.

Single QUEUED→SENDING claim не допускает повторного HTTP для key. Подтверждение
provider атомарно меняет canonical text/edited_at либо deleted_at, APPLIED/audit
и metadata GENERAL outbox; исходная отправка сохраняется как audit. Любой
неясный внешний результат — UNKNOWN либо durable SENDING при ошибке closeout;
partial unique index блокирует последующие команды этого source reply. Нет
auto-retry или автоматической reconciliation. Safe guard denial — CANCELLED.
Устаревший content fingerprint не перезаписывает более новое сообщение.
Media, VK/MAX edit/delete, feedback и retention/reconciliation — gates.
V71 не переписывает старые data/grants и не включает flags.

R142 добавляет отдельную multipart/CSRF отправку Telegram GENERAL documents.
PAGE_DIALOGS/current READ+OPERATE/account, foundation/general-ticket-queue,
pending/open, exact tuple/public generation, revision и unique raw ID/root/channel
обязательны; только USER_SELECTION/GENERAL_CHANNEL, не MANUAL local client.
Один файл 1..10 МиБ, caption<=1024 UTF-16 units, безопасное имя<=200 units,
forced application/octet-stream, без inline preview. Raw ID<=255 из-за старого
metadata field; storage key основан на public ID. Actual bounded bytes и SHA-256
сохраняются в immutable V72 intent. Key привязан к actor/input/destination/work
snapshot; единственный QUEUED→SENDING claim, guarded HTTP и frozen payload/channel.
Share locks удерживаются через provider call и closeout. Только positive provider
ID и проверенные key/size/SHA object storage допускают atomic SQL history/
metadata/SENT/audit и metadata-only GENERAL outbox. Неясный результат — UNKNOWN
либо durable SENDING, auto-retry нет; новый key означает новый intent.

Document download требует SENT registry + exact canonical history/generation/
owner/provider и available unique metadata key/name/hash/size. Legacy fallback
исключает document marker ИЛИ registry-linked history даже при порче marker.
Bounded read проверяет actual bytes/size/SHA, затем повторяет current READ/metadata
proof после IO; attachment/octet-stream/nosniff/no-store, mismatch/revocation=404.
Object storage не участвует в SQL rollback, возможен orphan после внешнего успеха.
Immutable SQL payload и external objects требуют отдельной retention/quotas/
reconciliation policy; no auto-cleanup. V72 не меняет старые rows/grants/flags.
Inline media, VK/MAX uploads и media edit/delete остаются gates; live acceptance
на рабочей машине не выполнена.

`/api/general-tickets` и `/general-dialogs` закрыты foundation и отдельным
default-off `multibusiness.general-ticket-queue`. Каждый SQL read повторно
проверяет grant и выбирает только `is_general=TRUE`, `business_id IS NULL` и
канонический public ID. Legacy/unresolved NULL и бизнес-обращения не входят
в эту очередь. История использует composite owner; attributes и attachment
namespace дополнительно требуют однозначности legacy ticket ID. Grant этого
read-only контура не доказывает готовность legacy API, write paths,
глобального поиска/уведомлений и фоновых заданий; их изоляция остаётся gate до rollout.

R134 добавляет отдельный GET /api/general-tickets/events/stream: PAGE_DIALOGS
проверяется на handshake, foundation/general-ticket-queue и live GENERAL READ
проверяются сервисом. Каждый emitter перед отправкой получает отдельный SQL
proof текущего account/grant и frozen tuple/public generation с is_general=TRUE,
business_id=NULL и unique raw ID. Регистр username сопоставляется canonical
case-insensitive, пробелы principal не отбрасываются и не дают чужой grant.
GENERAL не становится допустимым UiEventTicketScope.isValid для legacy callbacks:
отдельный GeneralTicketEventScope создаётся только из полного GENERAL V61 snapshot.

Watcher направляет известные GENERAL event types только в этот realtime, до
initial-message lookup и legacy AI/bell/assignment/support-chat. Cursor проходит
отклонённые/старые rows. Consumer не захватывает owner повторно; смена tuple,
business/kind/generation, удаление, collision либо missing schema закрывают delivery.
Redis использует отдельный topic <existing-ui-topic>:general:v1 и version=1
envelope без текста/media. Старый subscriber не получает этот topic; local mode
применяет те же per-emitter guards. Redis error не открывает local/legacy fallback.
Сами invalidations best-effort, не журнал доставки и не durable event replay.

Браузер получает только ticketPublicId, известный reason и emittedAt в событии
general_tickets_changed; raw tuple/IDs и содержимое диалога не выдаются. Отдельная
GENERAL очередь coalesce-ит обновления, перечитывает список/открытую карточку через
текущие READ API, а reconnect исправляет пропущенные invalidations новым чтением.
Heartbeat повторно проверяет grant/account/flags; при отказе посылается только
general_access_revoked без ticket data, emitter закрывается. UI очищает данные
при отзыве/ошибке stream, отменяет pending refresh и не применяет старые HTTP
ответы. Pagehide закрывает подписку. Сам этап R134 не добавлял migrations/flags.
GENERAL triage/OPERATE, text reply, close/reopen, intervals, assignment, creation, Telegram text edit/delete и documents описаны в R135–R142 выше;
остальные GENERAL write, отдельные bell/внешняя доставка и live Redis/proxy/provider acceptance PENDING.

Основные legacy dialog reads (список/summary, карточка, история, предыдущие
диалоги, участники и client context), ticket-bound `DialogAuthorizationService`
и HTTP downloads `AttachmentService` исключают GENERAL независимо от flags
очереди. `hasUniqueNonGeneralTicket` требует единственного raw-ID кандидата
во всей таблице и явного `is_general=FALSE`. Сначала отфильтровать GENERAL,
а затем считать уникальность нельзя: mixed-scope collision остаётся отказом.
`business_id=NULL,is_general=FALSE` сохраняет legacy/unresolved поведение;
этот guard не проверяет business membership и не заменяет strict scope.
Внутренний `hasUniqueTicket` сохраняет проверку identity для ботов/background.
READ grant GENERAL не открывает старые маршруты и write actions.

Legacy ticket-bound realtime использует тот же non-GENERAL guard: проверка
выполняется до dispatch из UI outbox, до уведомлений/назначений/AI в
`DialogRealtimeEventService`, до публикации и повторно перед доставкой SSE.
Адресный Redis fanout не обходит проверку; `dialogs_changed` и
`dialog_history_changed` требуют точного строкового ticket ID, а любой иной
envelope с `ticketId` тоже проверяется по canonical ticket. Неизвестный marker,
отсутствующая схема, GENERAL и коллизия закрывают legacy delivery. Курсор
outbox продвигается и по отклонённым событиям. Отдельный realtime GENERAL R134
использует описанный выше собственный контракт с действующим READ grant. Системные SSE без
ticket ID сохраняют поведение.

Legacy ticket-bound SSE дополнительно проверяет business READ каждого
получателя непосредственно перед каждой отправкой в emitter, включая
несколько соединений одного пользователя. Локальная доставка и адресный/
broadcast Redis fanout применяют один и тот же guard. Один SQL snapshot
требует точного unique raw-ID, явного non-GENERAL owner и текущего business
READ по общему с notifications predicate: единственный enabled/unblocked
canonical account, active business, membership или ALL_BUSINESSES с READ.
Доверять полям scope/business из envelope нельзя; ROLE_ADMIN не даёт grant.
Отзыв прав не требует переподключения; отказ пропускает ticket event, сохраняя
соединение для остальных разрешённых событий. Ошибка/отсутствие access schema
закрывает ticket delivery. UNRESOLVED с доказуемым public locator совместим
при default-off strict-read; при strict-read-scope=true скрывается.

Перед ticket-bound fanout фиксируется внутренний UiEventTicketScope: точный
canonical tuple, non-null immutable public ID, BUSINESS/UNRESOLVED и business ID.
Snapshot читается одним SQL statement с unique raw-ID/non-GENERAL proof и
передаётся отдельно от browser payload в Redis envelope либо local fallback.
Перед каждым emitter один SQL snapshot сверяет tuple/public ID/scope/business
и текущий READ получателя. Смена owner, пересоздание tuple с другим public ID,
смена business/scope, удаление или raw-ID collision запрещают старую delivery
даже получателю с ALL_BUSINESSES. Consumer не перезахватывает текущий owner
для старого payload. Необязательное поле ticketScope сохраняет чтение старых
system envelopes; ticket events без snapshot, с некорректным snapshot либо
другим payload.ticketId отклоняются. Наличие ticketScope само делает событие
ticket-bound и не позволяет переименованием события обойти guard.

Ticket с NULL/invalid public ID либо отсутствующая locator schema не позволяют
публиковать ticket SSE, включая legacy/UNRESOLVED при strict-read=false:
одного composite tuple недостаточно, чтобы отличить его пересоздание.
Автоматического присвоения public ID/backfill нет. Browser event names/payload
сохраняются; внутренний owner в них не добавляется. Сам transport-only этап
не добавляет migrations/flags.

Persistent DB outbox получает additive V61 snapshot при исходном INSERT:
точный существующий ticket_id, ticket_user_id, nullable ticket_public_id,
ticket_scope_kind и ticket_business_id. Panel и bot writers копируют owner
одним INSERT SELECT с проверкой уникальности raw ID; GENERAL сохраняется
отдельным scope, не превращается в UNRESOLVED. Исторические/старые producer
rows остаются без snapshot; автоматического backfill нет. CHECK запрещает
частичный owner, trigger — изменение snapshot, включая raw ID и присвоение
owner старой строке. FK намеренно отсутствует, snapshot переживает owner.
Readiness требует четыре новые columns; отсутствие V61 не позволяет объявить
runtime READY. Сам этот source-пакет не запускает migration.

Watcher до initial-message lookup и legacy callback сверяет frozen tuple,
public ID, kind/business и текущую unique non-GENERAL identity одним SQL.
GENERAL, старые unclassified rows, NULL locator и устаревший owner закрыты;
typed UNRESOLVED с locator совместим только до strict-read. Курсор проходит
отклонённые события. Initial-message suppression сравнивает полный snapshot,
поэтому события другой generation не подавляют bell нового обращения.
Проверка перед dispatch сама по себе не блокирует owner на время callback.

Bell/SSE ветви callback принимают именно snapshot V61: scoped entrypoints
DialogRealtimeEventService передают его через AlertQueueService до условного
notification INSERT и через UiEventStreamService до fanout/local delivery.
Scope не захватывается заново по raw ID. Notification SQL и recipient child
reads требуют совпадения tuple/public ID/kind/business, non-GENERAL, unique ID;
действующий business READ каждого получателя R122/R123 сохраняется. Пустой или
невалидный expected scope закрывает scoped API, а не включает legacy fallback.
Scoped UNRESOLVED разрешён только до strict-read. Поздний отказ queue может
попробовать fallback recipients лишь с тем же frozen scope и тем же SQL guard.
Scoped incoming callback передаёт исходный V61 owner через отдельные
processScopedIncomingClientMessage facade/flow и notifyScopedOperatorsEscalation
до NotificationService.notifyScopedAiDialogUsers. Условный INSERT проверяет
frozen owner и текущий READ каждого получателя R122/R126 в том же SQL snapshot;
поздняя смена owner/grants не открывает raw fallback, recapture или повторный send.

Это безопасная эскалация оператору; полноценный business-scoped AI ещё PENDING.
V62 добавляет отдельные ticket_ai_scoped_control/state/events с обязательным
immutable tuple/public ID/kind/business. Ключ control/state — public generation
и business (NULL имеет отдельный generated key); новая generation или другой
business не наследует прежние записи. Events immutable, legacy AI tables не
изменяются и не переносятся. Снимок создаётся только условным SQL из current
unique non-GENERAL owner, active business и исходного V61 expected scope.
UNRESOLVED automation совместим только до strict, actor business access из
него не возникает. Отсутствие schema/flags и конфликт snapshot закрывают путь.

Scoped incoming при глобальном ai_agent_enabled=true атомарно записывает
non-processing state escalate_only и event operator_handoff_requested без
текста клиента/media; это запрос handoff, не доказательство доставки. Затем
может уведомить оператора bounded preview исходного текста. ai_disabled из
scoped control проверяется и в state/event statement, и в финальном AI bell
INSERT. auto_reply_blocked сохраняется, не препятствуя human handoff. Отказ
после записи handoff не переименовывает event в доставленный и не повторяет send.
Actor reads state/control/events требуют live READ до ORDER/LIMIT; control
mutation требует live OPERATE (OPERATOR/MANAGER/ADMIN через membership либо
ALL_BUSINESSES) в том же SQL. VIEWER только читает, ROLE_ADMIN без business grant
не даёт доступа. Account enabled/unblocked, unique canonical identity и active
business обязательны; отзыв grants действует на следующую SQL-операцию.
R133 открывает эти primitives через отдельную BUSINESS-страницу обращения;
raw controllers и legacy controls по-прежнему не используют scoped records.

Legacy state/control rows не имеют canonical owner; raw retrieval использует
MAX(messages.business) и общие knowledge/memory rows. Scoped путь не вызывает
raw media normalization, state/control/event writes, retrieval, LLM, memory
learning или auto-reply; attachment не загружается и его storage key не включается
в уведомление.
Глобальное ai_agent_enabled=false оставляет эту AI-ветвь без эффектов.
auto_reply/assist_only/escalate_only не дают scoped пути доступ к raw pipeline.
Per-dialog legacy AI control также не читается/не изменяется этим путём.
UNRESOLVED сохраняет compatibility handoff/notification только до strict;
GENERAL, NULL/invalid scope закрыты. Legacy direct callers без expected scope
пока сохраняют свой контракт и требуют отдельного аудита.

V63 вводит отдельные ai_scoped_sources/events для явно созданных business-owned
KNOWLEDGE и MEMORY. Business обязателен и immutable; identity источника —
business/kind/exact key, origin MEMORY — immutable полный BUSINESS ticket snapshot.
KNOWLEDGE не зависит от наличия обращения. Legacy memory/knowledge/link rows,
aliases и global/unclassified sources не копируются и не используются scoped API.
Global-source policy этого этапа — DENY: даже ALL_BUSINESSES не расширяет retrieval
одного обращения до других бизнесов или общих источников. Разрешение sharing/
global sources требует отдельного контракта, а не пустого business или text alias.

ScopedAiSourceService создаёт только DRAFT; явный revise с expected revision
выполняет review/approval/archive и атомарно пишет metadata audit без title/body.
Любая mutation требует текущего CONFIGURE (business ADMIN через membership либо
ALL_BUSINESSES), unique enabled/unblocked account и active business внутри SQL.
MANAGER не имеет CONFIGURE по текущей матрице. READ библиотеки/history использует
тот же live business guard. MEMORY при создании, изменении и чтении требует
действующего origin tuple/public ID/business, unique raw ID/non-GENERAL; смена
origin скрывает запись, не переопределяя её владельца. Новый origin не наследует
source identity; для нового MEMORY в той же business scope нужен отдельный key.

AiRetrievalService.retrieveScoped — отдельный actor READ API без legacy intent/
context/scoring. Только APPROVED текущего BUSINESS попадают в SQL до ORDER/LIMIT;
GENERAL/UNRESOLVED/invalid scope закрыты независимо от strict compatibility
human handoff R130. Проверяется текущий request owner и scoped ai_disabled.
Источник возвращается отдельным immutable Source snapshot с revision/origin;
isCurrentCandidate повторно проверяет полный payload, version, request/origin,
READ/control перед будущим handoff. Это statement-time proof, не network lock.
Scoped result всегда autoReplyAllowed=false; raw Candidate/policy не используются.
R133 подключает к incoming отдельный trusted-worker поиск approved references
для human handoff, описанный ниже. Actor API retrieveScoped сохраняет отдельный
контракт. Автоматического learning/legacy sync нет.
R132 добавляет server-rendered каталог /business-ai-sources и editor/review:
foundation+ui-selector gates, явный один business без aggregate/default-all,
PAGE_KNOWLEDGE_BASE на каждом GET/POST и текущий READ внутри data SQL. Список
доступных business names также требует enabled/unblocked unique canonical account
и live READ. Catalog возвращает summaries без body/origin; literal q, kind/status
и current MEMORY origin фильтруются до id-keyset ORDER/LIMIT (25 строк на странице).
Private HTML получает Cache-Control: no-store; titles/body/keys экранируются
Thymeleaf, raw origin/user ID не передаются в editor model. Навигация использует
existing PermissionService и flags; авторитетным page gate остаётся server @PreAuthorize.

POST create создаёт только KNOWLEDGE DRAFT с новым opaque key формы; переданный
client status/kind/actor не определяет тип, одобрение или автора записи. POST revise
требует текущего CONFIGURE и CSRF стандартного SecurityFilterChain. В final UPDATE
reviseExpected совпадают immutable source ID, business/kind/exact key и revision;
старую форму нельзя применить к другому business или пересозданному key с той же
revision, даже при ALL_BUSINESSES. Отзыв account/grants после controller precheck
закрывает SQL mutation. Existing R131 revise API сохранён для внутренних callers.
Draft save — первая/default кнопка; approval/archive требуют отдельного действия.
Конфликт identity/version/доступа либо ошибка atomic audit дают 409 без overwrite.

R133 добавляет /business-dialog-ai/{publicId}, доступную из карточки BUSINESS
обращения. Foundation/uiSelector, PAGE_DIALOGS и current business READ обязательны;
GENERAL/UNRESOLVED/коллизии не открывают страницу. Public locator разрешается
сервером, raw ticket/user IDs не передаются в model. Private HTML no-store,
Thymeleaf экранирует текст. Управление требует live OPERATE и стандартного CSRF.
Старая форма доказывает business и expected updated_at в final mutation;
отсутствующая запись создаётся только ON CONFLICT DO NOTHING. Existing control
меняется только CAS с полным current owner и monotonic updated_at; audit atomic.
Конфликт владельца, прав, версии либо ошибка audit дают 409 без overwrite.

Создание MEMORY требует дополнительно PAGE_KNOWLEDGE_BASE и live CONFIGURE.
Оператор выбирает текстовый вопрос клиента и последующий ответ человека из
existing composite-owned history; NULL-owner/unlinked, AI, deleted и attachment
rows исключены. Показаны последние 50 сообщений, preview до 1000 символов.
Пара ID и SHA-256 полного текста, current root tuple/public generation/business,
аккаунт и grants повторно доказываются в одном INSERT snapshot. Подготовленный
вручную текст и явное подтверждение удаления PII/secrets обязательны; автоматической
PII-проверки нет. Создаётся только MEMORY DRAFT, затем требуется отдельный review
в каталоге R132. Новые metadata evidence хранят ID пары и fingerprints, не raw
диалог. Source, metadata audit и evidence атомарны; повтор key не создаёт дубль.
Это не backfill и не доказательство исторической business/generation принадлежности
всех chat_history rows: используется существующая привязка user_id/ticket_id,
а полная child attribution и перенос старых данных остаются отдельным gate.
Правка history после создания не переписывает curated source или его provenance.

При BUSINESS incoming глобальный ai_agent_enabled и scoped ai_disabled сохраняются.
Trusted worker использует frozen V61 scope и current owner/control в едином SQL:
literal case-insensitive substring до 500 символов, только APPROVED того же business
с current MEMORY origin, LIMIT 3 после фильтров. Query/body/title не копируются
в event/evidence. State escalate_only, event и ссылки source ID/revision записываются
атомарно; отсутствие совпадений не отменяет handoff. UI показывает summaries
последнего handoff только при PAGE_KNOWLEDGE_BASE и live READ; повторно проверяются
request owner/control, точная source revision/approval/origin. Отзыв прав, archive,
новая revision или смена owner скрывают ссылку без raw/global fallback. Evidence
не означает доставку оператору и не разрешает автоответ. Для UNRESOLVED остаётся
прежний compatibility handoff до strict, без поиска источников.

Полноценный scoped AI требует scope до media/LLM handoff и client delivery,
privacy/retention, более точного ranking и policy источников для автоответа. Они
PENDING вместе с producer context до INSERT и global page/system policy. V62–V64 —
source migrations, проверяемые в Testcontainers; V64 добавляет только отдельные
immutable-update evidence tables, без классификации старых rows. Рабочая DB/backfill
не выполняются; новых flags/rollout нет.

Auto-assignment из scoped ticket_created callback передаёт исходный V61
snapshot в DialogResponsibilityService.assignScopedResponsibleIfMissing.
Условный INSERT и последующее чтение existing/new responsible каждый
требуют полного expected tuple/public ID/kind/business, unique raw ID и
non-GENERAL marker в своём SQL snapshot. NULL/invalid scope либо отсутствие
flags/access schema закрывают scoped путь без raw fallback. BUSINESS INSERT
также требует текущего OPERATE у предложенного ответственного: единственный
enabled/unblocked canonical account, active business и active membership
либо ALL_BUSINESSES с OPERATOR/MANAGER/ADMIN, согласно initial capability
matrix BusinessAccessService. VIEWER, чужой business и ROLE_ADMIN этого не дают.
Existing responsible не переназначается; прежние trim/actor fallback/ai_agent
ограничения сохранены. Typed UNRESOLVED с locator совместим только до strict.
Отказ назначения не подменяет независимый recipient READ для bell/SSE.
Это statement-time proof, не блокировка owner на всё исполнение callback и
не attribution/backfill старых child rows. Раннее business-scoped построение
candidate pool/load balancing, остальные raw assignment writers и миграция
child identity ещё PENDING; отказ не запускает выбор другого кандидата.

Scoped auto-close callback передаёт исходный V61 scope и channel ID события
в DialogNotificationService.notifyScopedSupportChat. Один SQL capture требует
полного expected owner, unique raw ID/non-GENERAL, совпадения canonical
ticket.channel_id с событием, активного канала и, для BUSINESS, active business.
Он читает immutable target из canonical channels, не из JPA-объекта callback:
token, support_chat_id, platform, platform_config, delivery_settings,
credential_id. После сборки HTTP request и client/network setup один повторный
SQL непосредственно перед HttpClient.send сверяет тот же owner/channel и все
поля target, включая NULL. Telegram/VK/MAX используют этот scoped guard;
VK/MAX scoped client учитывает IntegrationNetworkService, прежние raw paths
сохраняют свой transport contract. Detached channel copies не меняют frozen
target; ошибки конфигурации/отправки не открывают raw fallback и в scoped
логах не раскрывают токен через exception text. Target.toString не выводит секреты.
UNRESOLVED с locator допускается только до strict-read; GENERAL, invalid scope,
missing schema, неактивный либо неизвестный destination закрывают отправку.
Это proof последнего SQL перед handoff, не транзакционная блокировка HTTP,
не version registry настроек канала на момент исходного события и не проверка
прав участников внешнего чата. Inherited/global network profiles и credential
contents отдельно не версионированы. Действующие grants внешнему destination,
остальные raw support-chat producers и полноценные scoped AI effects ещё PENDING.
Уже корректно записанный bell не откатывается при позднем отказе support-chat.

Mixed-version consumers без нового guard
не доказывают эту изоляцию; отдельный rollout/preflight остаётся обязательным.

Ticket-bound bell delivery сохраняет точный `ticketId` через
`NotificationService.notifyDialogUsers` до условного SQL INSERT: snapshot
требует единственного owner и явного `is_general=FALSE`. Возвращаются только
реально записанные получатели; отказ не считается успешной рассылкой.
Recipient child reads повторяют тот же predicate, а поздний отказ не запускает
fallback на всех операторов. Legacy queue, realtime, AI escalation и
`OperatorNotificationWatcher` используют этот путь. GENERAL history/SLA rows
не обрабатываются этим watcher. Общие system notification methods сохраняют
свой контракт; их URL не является authority для ticket scope.

Additive V60 сохраняет у новых ticket-bound уведомлений immutable snapshot:
точный `(ticket_user_id, ticket_id)`, nullable `ticket_public_id`,
`ticket_scope_kind` и `ticket_business_id`. Snapshot получается из owner
в том же условном INSERT, а не из URL/текста. BUSINESS требует business ID,
UNRESOLVED и GENERAL — его отсутствия; частичный tuple запрещён CHECK.
Trigger запрещает изменение всех пяти полей, включая назначение owner старой
строке с NULL. Исторические и generic/system/task уведомления сохраняют NULL
без автоматической классификации и backfill. FK на изменяемое/удаляемое
обращение намеренно отсутствует: snapshot остаётся историческим фактом.

Legacy bell list/page/count/read/read-all применяют один SQL predicate:
получатель совпадает, canonical tuple/public ID и business соответствуют
snapshot, текущий owner имеет `is_general=FALSE`. Исчезновение owner, смена
scope/business или другого non-null public ID закрывают чтение и read update.
GENERAL исключён даже при отдельном READ grant; его bell ещё не реализован,
а realtime R134 изолирован отдельными endpoint/topic. Фильтрация предшествует cursor pagination/aggregation, read
updates условны в том же SQL и сигнализируют только фактическое изменение.
Коллизия bare ID после сохранения не подменяет owner с полным tuple; legacy
dialog URL при этом по-прежнему закрывает свой ambiguous route.

При default-off `strict-read-scope` сохраняется совместимость unclassified
NULL и подтверждённого non-GENERAL UNRESOLVED. В strict-режиме оба варианта
скрыты, включая generic/system/task rows, пока для них не введена отдельная
явная global/system policy. Для типизированных BUSINESS-уведомлений действующий
business READ проверяется одним SQL predicate при INSERT, list/page/count и
условных read/read-all. Он требует единственного case-insensitive canonical
аккаунта получателя, `enabled=TRUE`, `is_blocked=FALSE`, активного бизнеса и
активной membership либо `ALL_BUSINESSES` с одной из текущих READ-ролей
`VIEWER/OPERATOR/MANAGER/ADMIN`. Системный `ROLE_ADMIN` и отдельный GENERAL READ
не подменяют business grant. Архивация бизнеса, блокировка аккаунта и отзыв
всех независимых grants закрывают следующую SQL-операцию, включая старую сессию.
Фильтр действует до pagination/aggregation; read updates не меняют скрытые rows.
Новый future business сразу доступен по действующему `ALL_BUSINESSES`.

Предикат business READ разделяется JDBC writer, JPA native repository и SSE;
проверка ticket identity/owner остаётся обязательной. Наличие read capability
у всех четырёх текущих ролей соответствует `BusinessAccessService`; разрешения
не выводятся из текста/URL, чужой users DB либо browser-selected business.
UNRESOLVED/unclassified сохраняют описанную выше совместимость; этот фильтр
не классифицирует исторические строки. Глобальная page authority, явная
global/system/task policy, полноценный scoped AI pipeline, внешняя support-chat recipient policy, остальные assignment writers и прочие producers ещё
требуют отдельного контракта/аудита. Это не разрешение включать strict flag
или выполнять rollout.

Для канала только с общими обращениями `GENERAL_CHANNEL` включается отдельной
записью `general_channel_question_template_bindings`: активный канал должен
явно выбрать тот же ID вопросника, не иметь активных business bindings, а
сохранённый SHA-256 должен совпадать с текущим шаблоном. Изменение состава или
порядка вопросов требует повторного подтверждения привязки. На текущем этапе
general-only runtime отклоняет preset-вопросы, пока для них не определён
безопасный, не связанный с бизнесами каталог. Отсутствие бизнес-привязки само
по себе никогда не включает `GENERAL`.

Основной internal channel runtime endpoint при активной general-only привязке
выдаёт тот же проверенный вопросник и явные `scopeKind=GENERAL`,
`scopeSelectionSource=GENERAL_CHANNEL`, ID/SHA-256 шаблона. Устаревшая привязка
или выключенный флаг дают конфликт, а не legacy fallback. Bot session обязана
сохранять выбранный scope до создания обращения; старые ответы другого scope
не подставляются в general-only опрос. ID/SHA-256 передаются из сессии через
command/event, outbox и inbox в nullable immutable поля ticket. Новое обращение
`GENERAL_CHANNEL` принимается только при точном совпадении версии с проверенной
текущей привязкой; устаревший или неполный snapshot отклоняется до записи корня.
После создания replay сверяет сохранённую версию ticket, не текущую привязку.
Смена/удаление ID либо SHA-256 при повторном event запрещена. Старые обращения
не получают версию из сегодняшней конфигурации. Завершение старых незаконченных
сессий после rebind пока требует отдельного version registry/UX; без него
устаревший опрос нужно начать заново. Очередь и права GENERAL, shared-channel
selection и сквозная provider-приёмка всё ещё обязательны до rollout.

На S4 вводится business-scoped registry template bindings. Existing
`question_template_id`, `rating_template_id`, `auto_action_template_id` и
`questions_cfg` сохраняются как legacy transport/config fields. Новый template
для business создаётся clone с новым stable template ID в canonical
`bot_settings`; редактирование template B не может менять template A. Пока
JSON-owner и precedence не утверждены, S1 не меняет эти поля.

### R143: read-only handshake выбора BUSINESS в общем канале

GET /internal/api/bot/channels/{channelId}/questionnaire/choices требует
существующий internal token/HMAC guard и foundation + shared-channel-resolution.
Меню версии 1 содержит только ID/code/display name бизнесов канала и ID/SHA-256
их шаблонов, без вопросов/глобальных settings. Все активные привязки должны
быть EXPLICIT_SELECTION, business/channel/template — active, шаблон — distinct,
не legacy/general-only, без unsupported presets и duplicate question IDs.
Неполный/stale/mixed набор закрывает весь новый каталог. До 100 вариантов;
более крупный каталог требует отдельного pagination/UX-контракта.

GET .../questionnaire/selection принимает businessId и catalogSha256 исходного
меню. Любое изменение видимого состава/имени/template version требует нового
выбора; шаблон не захватывается молча из свежей привязки. Конфигурация выбранного
BUSINESS/USER_SELECTION получается из того же read snapshot, только с одним
вопросником и пустыми locationTree/presetDefinitions. Оба ответа no-store.
Старый business runtime endpoint сохраняет R112 semantics.

Основной unscoped runtime endpoint больше не выдаёт legacy settings каналу
с активными business bindings, даже при выключенных flags: нужен явный выбор.
Общий PanelRuntimeConfigClient не кэширует legacy/general runtime между
lookup и не возвращает local fallback при ошибке настроенного API. Новый SDK
choices/select проверяет channel/business/version/catalog hash, membership
выбора, точную форму settings и SHA-256 фактических bytes шаблона. Неизвестные
scope/версии, подмена payload или глобальные presets/settings закрыты.
Совместимость legacy runtime остаётся только для unbound channels; отключённый
Panel API/другие transport modes ещё требуют отдельного аудита.

R143 сам по себе — read-only handshake, не право создать ticket. Контракт
BUSINESS producer/INSERT/replay добавлен в R144 ниже; MAX selection UX — в R145.
Кнопки Telegram/VK, GENERAL option общего канала, scoped locations и сквозная
приёмка resume/UX после rebind остаются PENDING. Новый shared-channel flow нельзя включать до
подключения совместимых consumers; существующий general-only flow сохранён.

### R144: создание BUSINESS по сохранённому выбору вопросника

QuestionnaireScope для BUSINESS содержит положительный businessId,
USER_SELECTION, stable template ID, SHA-256 шаблона и catalogSha256. Неполный
BUSINESS snapshot не десериализуется как legacy/GENERAL. Старый GENERAL snapshot
совместим с nullable новыми полями. Command и оба event DTO передают catalogSha256
без повторного lookup; Telegram/VK/MAX finalizers используют businessId и
отпечатки из scope сессии. Это подготовка finalizers, не подключение меню выбора.

Новый BUSINESS/USER_SELECTION INSERT требует foundation, strict ticket writes
и shared-channel-resolution. До записи корня backend проверяет platform канала,
полный актуальный каталог R143, membership выбора и точные template ID/SHA.
Изменение любого варианта меню, rebind или stale template требует начать выбор
заново; нельзя молча получить новую версию или вывести owner из display business.
Канал с активным EXPLICIT_SELECTION не принимает новый legacy/unresolved или
непроверенный GENERAL envelope, в том числе при выключенном strict flag.
SINGLE_BUSINESS сохраняет отдельную прежнюю проверку однозначной привязки.

V73 добавляет nullable immutable business_selection_source и
questionnaire_catalog_sha256 в tickets. CHECK допускает только полный
BUSINESS/USER_SELECTION с owner и версией шаблона; GENERAL поля не переиспользуются.
Исторические строки не получают сегодняшнюю конфигурацию. SQL trigger запрещает
смену/удаление новых полей; readiness требует новую схему до READY.

Outbox и inbox сверяют catalogSha256 наряду с owner/scope/template identity
до пропуска duplicate или reclaim. Replay существующего canonical tuple
сверяет frozen owner/source/template/catalog/channel, а не нынешние bindings;
новый event ID не позволяет потерять selection provenance. Отключение strict
flag также не превращает уже выбранный BUSINESS root в legacy replay.
При ошибке дочерней записи root/children откатываются, failed inbox допускает
точный повтор. Сами transport event IDs и display поля не выдают права доступа.

Подтверждены typed JSON/CAS scope и MAX state round-trip. Подключение MAX
pending choice/selection UX описано в R145; Telegram/VK остаются PENDING.
GENERAL option общего канала требует отдельной явной настройки, а не ослабления
general-only binding. Нет новых scoped presets/locations, version registry для
старых незавершённых опросов или разрешения на flags/migration/rollout.

### R145: MAX — выбор до первого вопроса и восстановление сессии

MAX при отказе primary runtime может запросить только проверенный BUSINESS
каталог, не локальные/global settings. Для choices/selection с forStart=true
обязательны foundation + shared-channel-resolution + strict writes, internal
token/HMAC, no-store и X-Iguana-Questionnaire-Writer-Version: 1. SDK требует
явный ответ этой версии на обоих этапах: старый backend, игнорирующий query,
не считается готовым к созданию обращений. Read-only R143 API совместим.
Новый selection path работает только с RabbitMQ + настроенным Panel API и
активным channel; audit старых non-Rabbit/disabled-API modes остаётся PENDING.

До первого вопроса сохраняются каталог, его SHA, случайный nonce меню и
исходный provider event key. Нативные MAX message buttons содержат business
name/code и ограниченный по длине код выбора. Код принадлежит этому nonce,
user/channel/chat и одному из business IDs сохранённого каталога. Чужая или
старая кнопка после /start не выбирает новый бизнес и не становится ответом
на первый вопрос. CAS не позволяет старому обработчику заменить чужой выбор.

Проверенный выбранный payload преобразуется в DTO без повторной нормализации,
смены order или channel/local overrides. Нет обязательных business/problem
вопросов и подстановки прошлых бизнес-ответов. Bootstrap, flow, ответы,
scope/ID/SHA/catalog и nonce сохраняются в существующем session store.
Старый legacy flow после смены режима канала требует новый /start.

До первой постановки в очередь выбор проверяется ещё раз; stale/unavailable
даёт явный /start без захвата новой версии. После подготовки публикации
сохранены последний ответ и фиксированный submission event key. Retry
публикует тот же command без свежих bindings. После принятия в очередь
сохраняется маркер с ticket ID: потеря delivery dedup cache не создаёт другой
опрос/обращение. Это транспортное состояние, не canonical ticket source of truth.
Новый follow-up ожидает именно этот canonical root, не другой active ticket;
pending/retry и terminal state не удаляются перед публикацией.

Создание и follow-up этого selected MAX flow не отправляют raw summary в
непроверенный support chat. Это локальная защита нового пути, не завершённая
recipient policy всех legacy/direct/active путей. Общие правила destinations,
TTL/retention сессий, одновременные обращения разных бизнесов, live Redis/MAX/
proxy и rolling-upgrade acceptance ещё обязательны до rollout.
Проверки используют реальный handler/JSON/CAS с изолированными doubles для
доставки и отдельные PostgreSQL suites; это не живой provider E2E.

Telegram/VK, GENERAL option общего канала, scoped locations/presets и release
остаются отдельными этапами. Новых migrations нет; minimum schema — V73.
Необходимость совместимого backend/fleet не разрешает менять рабочие flags,
запускать migration/backfill, rebuild или deployment без отдельного scope.

## Scope guard и API contract

Один сервис `BusinessAccessService` должен предоставлять:

```text
resolveAccessibleBusinessIds(authenticated users.id)
resolveEffectiveBusinessCapabilities(users.id, business_id)
requireReadableTicket(ticket locator, authenticated user)
requireWritableTicket(ticket locator, action, authenticated user)
requireSelectedBusiness(requested context, authenticated user)
```

Контракт:

- Доступ к сущности сначала резолвится по её canonical owner, затем проверяется
  against accessible IDs. Нельзя сначала загрузить entity по public ID и
  передать её в UI/notification до scope check.
- `business=all` — допустимый presentation context только для union resolved
  `accessibleBusinessIds`; конкретная write operation всегда имеет один
  business owner.
- Неавторизованный direct entity lookup возвращает нераскрывающий ответ (для
  data endpoint — `404`), а invalid/forbidden selected context — `403` с
  безопасным сообщением. Counts, pagination, search, export, attachments,
  autocomplete, SSE и AI retrieval используют тот же guard.
- Internal transport callbacks не получают operator grant. Они несут trusted
  resolved business context, который сверяется с ticket/channel binding и
  сохраняется до outbox. Unknown context останавливает обработку.
- У event/outbox/notification есть явный `scope_kind`:
  `BUSINESS`, `GENERAL`, `GLOBAL_SYSTEM` или `UNRESOLVED`. `GENERAL` — намеренно
  выбранное обращение без бизнеса, `GLOBAL_SYSTEM` — системные данные. Оба
  требуют отдельного права; `ALL_BUSINESSES` сам по себе его не предоставляет.

## Legacy compatibility и decision table

| Область | Безопасное временное поведение | Решение, необходимое до реализации |
| --- | --- | --- |
| Три непустых legacy aliases | Хранить исходный text; не normalise/assign автоматически | **PENDING:** approved alias → `business.code` map и ответственный за него |
| Три пустых `messages.business` | `UNRESOLVED`, исключены из произвольного business list | **PENDING:** ручное назначение или permanent unresolved policy |
| 3 history channel mismatch | owner берётся из ticket; исторический channel не переписывается | **PENDING:** классификация forwarding/error и правила отображения |
| Mixed channels | Явный выбор бизнеса или «Без бизнеса» до первого вопроса; без выбора flow не запускается | **УТОЧНЕНО 2026-10-16:** general — отдельный scope, не unresolved. **PENDING:** UX-представление и precedence для иных verified sources |
| 2 tasks без ticket link | Сохранять ручные задачи без автоматической классификации/назначения | **PENDING:** R12 manual-task scope policy и право на global Kanban |
| 105 incidents / system notifications | Не получают business ID массово | **PENDING:** business/shared/global classification и отдельные permissions |
| Existing ticket route | Legacy route доступен только в подтверждённом unique window | **РЕШЕНО S2A:** opaque immutable `ticket_public_id`; internal fallback — обязательный composite locator |
| Roles | Global page permissions не меняются автоматически | **PENDING:** business role matrix и administrator boundary |
| Projects/boards | Не показывают inaccessible tasks | **PENDING:** scope project/board and explicit-global task policy |

## Поэтапное применение

1. **S1, additive foundation.** Свободная на дату реализации Flyway version
   выше V50; новые tables/nullable FKs/indexes, `BusinessAccessService`,
   default-deny contract и PostgreSQL integration tests. Feature flags off;
   никаких existing-row mutation, UI switch или strict filtering.
2. **S2, assignment.** S2A фиксирует public/composite ticket locator и
   read-only preview, но не делает legacy assignment. S2B требует approved
   alias/location map, dry-run evidence, controlled writes, child tuple integrity
   и UNRESOLVED queue. Backfill
   запускается только отдельным явным scope.
3. **S3, server scope.** Read/write API, dialogs, attachments, tasks,
   projects/boards, analytics, notification and realtime/outbox paths; negative
   cross-business tests before strict flag.
4. **S4, channel runtime.** Shared-channel resolver, per-business template
   registry and frozen ticket context across Telegram/VK/MAX replay.
5. **S5, UI.** Accessible-business selector, business catalog/memberships,
   empty states and manual acceptance. Browser state can select scope but cannot
   grant it.
6. **S6, release.** A separately approved production preflight, immutable
   revision/image, migration/backfill checkpoint, rollback pointer and
   independent acceptance. It is not part of source implementation.

### S1 source checkpoint — 2026-10-10

- Добавлена V51, не запускавшаяся вне изолированной PostgreSQL Testcontainers
  schema. Она создаёт `businesses`, memberships, `ALL_BUSINESSES` grants,
  пустой approved-alias registry, channel/location mapping foundations и
  nullable `tickets.business_id` с FK/index; legacy rows не присваиваются.
- В V51 seed-ятся только предустановленные records `sushi` и `bliny`;
  реальных legacy aliases в migration нет.
- `BusinessAccessService` резолвит direct membership и `ALL_BUSINESSES` grant,
  применяет initial role matrix `VIEWER`/`OPERATOR`/`MANAGER`/`ADMIN` и по
  умолчанию отказывает при отсутствии active grant. Business `ADMIN` — не
  системный `ROLE_ADMIN`.
- `requireSelectedBusiness` принимает типизированный single/all context:
  конкретный business разрешён только из accessible IDs, а all-view возвращает
  их union (включая пустой). Это presentation scope, не grant и не write scope.
- `requireReadableTicket` пропускает только `TicketReference` с non-null
  canonical owner и effective `READ`; unassigned legacy ticket и чужой owner
  получают fail-closed отказ. Метод не подключён к endpoint до S3.
- Targeted PostgreSQL integration test подтверждает legacy ticket без
  assignment, denied-by-default, изоляцию selected membership, fail-closed
  selected/ticket contexts и включение future business в `ALL_BUSINESSES` без
  capability `CONFIGURE`.
- Existing API/UI/read paths намеренно не подключены к сервису до S3, поэтому
  V51 source foundation не меняет поведение existing users.

## Feature flags и rollout boundary

Предлагаемые flags выключены по умолчанию:

```text
multibusiness.foundation.enabled=false
multibusiness.ticket-write-requires-business=false
multibusiness.strict-read-scope=false
multibusiness.shared-channel-resolution=false
multibusiness.general-channel-resolution=false
multibusiness.ui-selector=false
multibusiness.manual-ticket-creation=false
```

`MultibusinessFeatureFlags` binds these exact keys and defaults all of them to
`false`. At the S2A source checkpoint no runtime path reads this bean: adding
the configuration does not enable filtering, assignment or a UI switch.

Ручное создание дополнительно требует `foundation.enabled=true`,
`ui-selector=true` для страницы и отдельный `manual-ticket-creation=true` для
POST. Исходное значение флага — `false`; наличие source/migration не включает
операторскую запись в рабочей среде.

Строгий read scope нельзя включать, пока вся показываемая legacy выборка не
получила approved business или не изолирована как `UNRESOLVED`. Каждый flag
имеет отдельный acceptance checklist и rollback в состояние `false`; source
merge сам по себе не является rollout.

## Обязательная test matrix

- PostgreSQL: FK/check constraints, unique business codes, membership and
  `ALL_BUSINESSES` effective scopes, no cross-business duplicate location
  collision.
- Security: user A не читает/не меняет B через list, direct ID, attachment,
  search, export, count, task, notification, SSE и AI context; `ALL_BUSINESSES`
  не получает global-system data.
- Ticket identity: existing IDs/history remain stable; ambiguous bare
  `ticket_id` is rejected after the compatibility window; all child lookups
  resolve the canonical ticket.
- Transport: single and shared channel, explicit selection, verified mapping,
  conflict/unresolved, idempotent replay/retry/edit/feedback.
- Templates: clone B does not mutate A, template version stays frozen on an
  existing ticket, new business works without iiko.
- UI/manual: one/many/all grants, forbidden context switch, all-view labels and
  counts, reload/realtime, empty business and business catalog membership save.

## Gates после S1

S1 не назначает legacy records и не включает strict scope, поэтому может быть
подготовлен без раскрытия sensitive aliases. S2A закрывает ticket public locator
contract и добавляет fail-closed read-only preview, но до S2B/S3 необходимо
закрыть релевантные оставшиеся PENDING
decisions из decision table и precise changed-file/test plan against fresh
`main`. Отдельное явное
разрешение на запуск migration, backfill или rollout по-прежнему обязательно:
ни один из них не следует из source foundation.
