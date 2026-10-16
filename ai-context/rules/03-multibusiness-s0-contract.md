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
- Для ручного обращения без бота/интеграции создаётся локальный клиент с новым
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
назначений. Доступ требует одновременно `PAGE_DIALOGS`, включённую учётную
запись и активный READ grant. `ROLE_ADMIN`, business membership и
`ALL_BUSINESSES` не заменяют этот grant. Установка/отзыв доступны только
системному администратору с `PAGE_SETTINGS` и CSRF. READ не разрешает отвечать,
менять статус либо выполнять другие write operations.

`/api/general-tickets` и `/general-dialogs` закрыты foundation и отдельным
default-off `multibusiness.general-ticket-queue`. Каждый SQL read повторно
проверяет grant и выбирает только `is_general=TRUE`, `business_id IS NULL` и
канонический public ID. Legacy/unresolved NULL и бизнес-обращения не входят
в эту очередь. История использует composite owner; attributes и attachment
namespace дополнительно требуют однозначности legacy ticket ID. Grant этого
read-only контура не доказывает готовность legacy API, write paths, SSE,
поиска/уведомлений и фоновых заданий; их изоляция остаётся gate до rollout.

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
outbox продвигается и по отклонённым событиям. Это не business-recipient
authorization и не отдельный realtime GENERAL: им ещё нужен свой контракт
с действующим READ grant. Системные SSE без ticket ID сохраняют поведение.

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
| 2 tasks без ticket link | `UNRESOLVED` либо `GLOBAL_SYSTEM`, не произвольный business | **PENDING:** policy и право на global Kanban |
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
