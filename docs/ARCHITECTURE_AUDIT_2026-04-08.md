# Архитектурный аудит проекта Iguana CRM
**Дата:** 8 апреля 2026  
**Статус:** Актуально, но в активной фазе исправления  
**Актуализация:** 9 апреля 2026 (см. `docs/ARCHITECTURE_AUDIT_VALIDATION_2026-04-09.md`)  
**Последняя актуализация:** 30 апреля 2026

---

## 📋 Что уже сделано

✅ Проведён исходный аудит `spring-panel` и `java-bot`  
✅ Проверена и скорректирована часть исходных выводов аудита  
✅ Зафиксирован roadmap рефакторинга в `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`  
✅ Начат и существенно продвинут рефакторинг transport-layer для `dialogs` и `settings`  
✅ Добавлен foundation-слой для UI runtime, preferences и page presets  
✅ Усилен `Phase 6` safety net через targeted unit/WebMvc/lifecycle/smoke tests  
✅ Формализован bot runtime contract через launcher-strategy, explicit artifact contract и runtime diagnostics  
✅ `DialogService` уже заметно разгружен через несколько read/write service slices, хотя полный split ещё не завершён  

---

## 🧭 Текущее состояние

Этот документ больше нельзя читать как “чистый список проблем на старте”. К
23 апреля 2026 часть наиболее болезненных рисков уже снижена в коде.

Что уже существенно улучшено:

- ранний UI bootstrap централизован через `fragments/ui-head.html`;
- `theme`, `ui-config` и operator UI preferences получили единый runtime-слой;
- controller-level split домена `dialogs` в основном выполнен:
  `DialogReadController`, `DialogListController`, `DialogWorkspaceController`,
  `DialogQuickActionsController`, `DialogMacroController`,
  `DialogAiOpsController`, `DialogWorkspaceTelemetryController`,
  `DialogTriagePreferencesController`;
- внутри giant `DialogWorkspaceService` уже начат service-level split:
  внешний профиль клиента, parity/composer-сборка, navigation/queue meta и
  rollout/meta-config, client segments/profile health и context blocks/health
  плюс client payload support и context sources/attribute policies вынесены в
  отдельные workspace sub-services; теперь туда же вынесен и
  `context contract`, а старый мёртвый review-control дубль удалён из
  самого workspace service;
- из giant `DialogService` уже вынесен первый самостоятельный client context
  read-layer: история обращений клиента, profile enrichment, profile match
  candidates и related events теперь живут в `DialogClientContextReadService`,
  а `workspace/macro` сценарии используют его напрямую;
- туда же добавлен отдельный conversation read-layer:
  история сообщений, previous history и ticket categories теперь живут в
  `DialogConversationReadService`, а `DialogReadService`, `workspace` и
  `public form` перестали тянуть этот срез через giant service;
- следующим service-level пакетом из giant `DialogService` вынесены ещё два
  слоя: `DialogLookupReadService` теперь обслуживает summary/list/find
  сценарии, а `DialogResponsibilityService` — assignment/read-marker flows;
  на новые зависимости уже переведены `DialogListReadService`,
  `DialogWorkspaceNavigationService`, `DialogWorkspaceTelemetryService`,
  `DialogQuickActionService`, `DialogReadService`, `DialogWorkspaceService`,
  `DialogReplyService`, `DashboardController`, `DashboardApiController` и
  `DialogsController`;
- следующим пакетом туда же вынесены write-side lifecycle/audit срезы:
  `DialogTicketLifecycleService` теперь обслуживает
  `resolve/reopen/categories`, а `DialogAuditService` — dialog action audit и
  workspace telemetry logging; на новые зависимости уже переведены
  `DialogQuickActionService`, `DialogAuthorizationService`,
  `DialogWorkspaceTelemetryService`, `AnalyticsController` и
  `DialogTriagePreferencesController`;
- следующим consumer-split пакетом добавлен `DialogDetailsReadService`:
  сценарий `loadDialogDetails` теперь выделен отдельно, а его прямые
  потребители `DialogReadService`, `DialogWorkspaceService` и
  `DialogMacroService` уже переведены на новый слой; вдогонку
  `DialogAiAssistantService` переведён на `DialogResponsibilityService`, а
  `SlaEscalationWebhookNotifier` использует `DialogLookupReadService` и
  `DialogResponsibilityService`, так что notifier перестал использовать giant
  `DialogService` как технический фасад уже на основном routing/assignment
  пути;
- ещё одним пакетом снят уже не продуктовый, а технический coupling вокруг
  giant service: `DialogDataAccessSupport` вынесен из nested static helper-слоя
  `DialogService`, а `DialogResolveResult` больше не живёт как nested record
  giant service; на новые опоры переведены `DialogTicketLifecycleService`,
  `DialogQuickActionService`, `DialogQuickActionsController`,
  `DialogAuditService`, `DialogLookupReadService`,
  `DialogConversationReadService`, `DialogClientContextReadService` и
  `DialogResponsibilityService`, что уменьшает blast radius даже там, где
  remaining business logic ещё не вынесена полностью;
- вокруг telemetry/notifier слоя добавлены boundary-сервисы
  `DialogWorkspaceTelemetrySummaryService` и
  `DialogMacroGovernanceAuditService`, поэтому
  `DialogWorkspaceTelemetryService` и `WorkspaceGuardrailWebhookNotifier`
  больше не тянут `DialogService` напрямую и готовы к следующему real split
  summary/governance логики;
- следующим пакетом эти boundary-слои получили уже не только обёртки, а
  реальные data/support dependencies: raw workspace telemetry JDBC/read-model
  и агрегация вынесены в `DialogWorkspaceTelemetryDataService`, а helper-логика
  macro governance/usage/variables — в `DialogMacroGovernanceSupportService`;
  сам `DialogService` переключён на новые слои и потерял значимый кусок raw
  telemetry/macro helper-блоков;
- следующим пакетом `DialogMacroGovernanceAuditService` перестал быть тонкой
  обёрткой над giant service и стал самостоятельным owner’ом
  macro-governance audit slice;
- следующим пакетом и последний прямой consumer-хвост в основном
  service/controller-слое снят с giant service: `DialogWorkspaceTelemetrySummaryService`
  теперь работает через отдельный `DialogWorkspaceTelemetrySummaryBridgeService`,
  а не тянет `DialogService` напрямую; это ещё не полный вынос summary-логики,
  но уже переводит оставшуюся связность в явный compatibility bridge вместо
  прямой доменной зависимости;
- SLA/runtime дублирование между `DialogWorkspaceService` и
  `DialogListReadService` уменьшено через общий `DialogSlaRuntimeService`,
  который теперь держит lifecycle-state, deadline, minutes-left и config
  parsing для SLA-слоя;
- `settings` выведен из режима giant controller/update-method через
  `SettingsParametersController`, `SettingsItEquipmentController`,
  `SettingsUpdateService`, `SettingsDialogConfig*Service` и связанные subdomain
  services;
- `settings` продвинулся ещё дальше: `dialog_config` уже не просто вынесен из
  giant update-method, а дополнительно разрезан через
  `SettingsDialogTemplateConfigService` и
  `SettingsDialogRuntimeConfigService`, так что template/macro governance и
  runtime-настройки больше не живут в одном coordinator-слое;
- bot runtime boundary уже не только “начал уходить” от жёсткого
  `spring-boot:run`, а получил `BotRuntimeContractService`,
  `app.bots.executable-jars`, endpoint `/api/bots/{channelId}/runtime-contract`
  и lifecycle contract test с runnable test jar;
- `Phase 6` уже даёт не только точечные unit-тесты, но и пакет page bootstrap
  smoke tests плюс WebMvc coverage для sliced dialog/settings controllers,
  shared config/env foundation и runtime contract; теперь этот smoke-пакет
  покрывает ещё и `channels`, `tasks`, `users`, `object-passports`,
  `public forms` и аналитические subpages.

Что уже больше не выглядит как главный hotspot:

- giant controller-проблема в `dialogs/settings` уже не является центральным
  архитектурным риском: основной transport-layer split фактически завершён;
- тема/UI bootstrap больше не являются хаотичным page-by-page набором
  скриптов: foundation и ownership уже зафиксированы;
- bot runtime больше не выглядит как “один `spring-boot:run` без контракта”:
  теперь главный риск сместился с самого факта запуска на качество и
  полноту межмодульного runtime/platform boundary;
- часть старых helper-дублей в `workspace` и SLA runtime уже снята, поэтому
  текущий риск теперь скорее в remaining bounded contexts, чем в
  очевидном copy-paste и transport-level монолитах.

Что остаётся главным архитектурным риском:

- `DialogService` всё ещё слишком крупный и остаётся главным кандидатом на
  service-level split, хотя первый client-context read slice из него уже
  выделен и часть потребителей переведена на новый слой; теперь к нему
  добавлен и второй conversation read slice, что заметно уменьшает pressure
  на giant service; теперь к этому добавлены ещё lookup/responsibility slices,
  а теперь и lifecycle/audit/details slices; отдельно giant service уже
  потерял и дублирующий `macro governance audit` bounded context, который
  окончательно живёт в `DialogMacroGovernanceAuditService`, а не внутри
  giant service; но remaining bounded contexts и legacy helper blocks всё
  ещё не закрыты полностью; при этом сам класс уже заметно уменьшился и
  сейчас находится примерно на уровне `4900` строк, то
  есть речь уже не о “старом монолите без движения”, а о незавершённом, но
  активно режущемся giant service;
- `DialogWorkspaceService` всё ещё крупный, хотя уже начал разгружаться через
  выделенные workspace sub-services и уже прикрыт targeted service tests по
  parity, navigation, rollout, client profile, context blocks, client payload,
  context source policy и context contract;
- но сам `workspace` уже заметно сузился: из него дополнительно убраны
  мёртвые helper-блоки по SLA/source-coverage/export formatting;
- `settings` всё ещё содержит remaining subdomains, которые могут снова
  разрастаться в общих слоях; в первую очередь это `catalog/reference`,
  `partner/network` и часть `bot/integration` сценариев;
- `SharedConfigService` дублируется между `spring-panel` и `java-bot`;
- DTO/API contract и error contract всё ещё не унифицированы по проекту;
- persistence-слой по-прежнему смешивает raw JDBC и JPA/Repository подходы;
- boundary-wrapper слой вокруг telemetry/notifier уже подкреплён реальными
  `DialogWorkspaceTelemetryDataService` и
  `DialogMacroGovernanceSupportService`, а прямой consumer-зависимости
  `DialogWorkspaceTelemetrySummaryService -> DialogService` больше нет;
  следующий шаг там теперь уже не в raw JDBC/helper выносе, а в дожиме самой
  telemetry summary orchestration и постепенном схлопывании compatibility
  bridge/delegate слоя вокруг `DialogService`;
- важный реальный progress point: `buildMacroGovernanceAudit(...)` в
  `DialogService` теперь лишь compatibility delegate, а constructor giant
  service уже не тянет `DialogMacroGovernanceSupportService`, потому что
  macro governance audit полностью живёт в отдельном domain service;
- `settings` subdomain layer получил адресную test-страховку на уже
  вынесенных сервисах (`runtime/public-form/sla-ai/template/workspace`), но
  больше не ограничивается только ими: теперь targeted tests есть и на
  `SettingsTopLevelUpdateService`, `SettingsLocationsUpdateService`,
  `SettingsParameterService` и `UiPreferenceService`, так что top-level
  updates, `locations` sync, server-backed UI preferences и parameter CRUD
  тоже имеют прямую safety net; теперь сверху добавлен и первый integration
  net на реальный shared config boundary через
  `SharedConfigServiceTest`, `SettingsParameterSharedConfigIntegrationTest` и
  `SettingsUpdateSharedConfigIntegrationTest`, так что риск вокруг
  `settings.json/locations.json` уже заметно снижен; теперь тот же boundary
  дополнительно расширен на `org_structure.json` и `bot_credentials.json`,
  хотя полностью завершённым integration-слоем этот контур пока считать рано;
- orchestration/API слой `settings` тоже уже не “почти без прямых тестов”:
  есть `SettingsUpdateServiceTest`, `SettingsDialogConfigUpdateServiceTest`,
  `SettingsBridgeControllerWebMvcTest` и `ProfileApiControllerWebMvcTest`,
  так что основной `/settings` coordinator flow и server-backed UI preferences
  прикрыты не только subdomain services, но и coordinator/WebMvc-контрактом;
- в server-backed UI preferences был найден и закрыт реальный alias-bug:
  `sortMode/pageSize/updatedAtUtc` в `dialogsTriage` могли теряться из-за
  premature default-normalization, теперь этот контракт исправлен и покрыт
  targeted unit tests.
- merge-поведение server-backed UI preferences теперь тоже прикрыто отдельно:
  обновление базовых prefs подтверждено тестом не затирает уже сохранённый
  nested `dialogsTriage` payload.
- `SlaEscalationWebhookNotifier` больше не держит даже legacy field/branch на
  `DialogService`: notifier окончательно работает через
  `DialogLookupReadService`, `DialogResponsibilityService` и
  `DialogAuditService`, а legacy tests переведены на прямые зависимости;
- `Phase 6` расширен ещё и на UI preset contract в шаблонах:
  `channels`, `tasks`, `users`, `passports`, `public form`,
  `analytics/certificates` и `analytics/rms-control` теперь имеют explicit
  `data-ui-page` и прикрыты page bootstrap smoke tests.
- следующий пакет `Phase 6` закрыл ещё и detail/subpage contract:
  `dialogs/ai-ops`, `clients/unblock_requests`, `users/detail` и оба
  passport editor route (`/object-passports/new`, `/object-passports/{id}`)
  теперь тоже имеют explicit `data-ui-page` и прикрыты отдельными WebMvc
  smoke tests на ранний `ui-preferences/theme/ui-config` bootstrap.
- следующий пакет `Phase 6` добрал public-shell/runtime contract:
  `auth/login`, `error/403`, `error/404` и `error/500` теперь тоже имеют
  explicit `data-ui-page="public"`, `error/403` и `error/500` подтянуты к
  общему `fragments/ui-head` вместо выпадения в отдельный legacy shell,
  для них добавлен lightweight template-contract test, а
  `passports/detail` нормализован до explicit `data-ui-page="passports"`.
- shared config boundary больше не ограничен только
  `settings.json/locations.json`: `SharedConfigServiceTest` теперь добран
  round-trip сценариями для `org_structure.json` и `bot_credentials.json`,
  `AuthManagementApiControllerWebMvcTest` прикрывает API-контракт сохранения
  `org_structure`, `ChannelApiControllerWebMvcTest` — list/create/delete
  контракты `bot_credentials`, а `BotAutoStartServiceTest` фиксирует
  runtime-поведение автозапуска при активных и неактивных credentials.
- следующим пакетом этот слой расширен уже на orchestration/API/runtime
  boundary: `AuthManagementApiControllerWebMvcTest` прикрывает base payload
  `/api/auth/state`, а `AuthManagementApiController#getAuthState` сделан
  null-safe для nullable `current_user_id/org_structure`;
  `BotProcessApiControllerWebMvcTest` теперь покрывает `start/stop/status`
  вместе с `runtime-contract`, `BotRuntimeContractServiceTest` добран
  `vk`-contract сценариями, а `BotAutoStartServiceTest` — ветками
  `inactive/already-running/missing-credential`.
- следующим пакетом shared-config/runtime boundary расширен ещё глубже:
  `ChannelApiController#createBotCredential` теперь нормализует пустой
  `platform` в `telegram`, `ChannelApiControllerWebMvcTest` прикрывает
  embedded credential summary в `/api/channels`, validation/normalization
  для `bot_credentials` и `404` при удалении отсутствующего credential;
  `BotProcessApiControllerWebMvcTest` добран stopped-state веткой,
  `BotRuntimeContractServiceTest` — `max`-contract сценарием и optional
  `vk` env keys, а `BotAutoStartServiceTest` —
  веткой `channel without credential binding`.
- следующим пакетом `Phase 6` расширен уже на полноценный
  `ChannelApiController` orchestration contract:
  `createChannel` прикрыт нормализацией пустого `platform` в `telegram`
  и генерацией default `public_id/questions_cfg/delivery_settings`,
  `post/put` update-ветки — sync `credential_id/support_chat_id`,
  `network_route/platform_config`, alias-обновлением и invalid
  `questions_cfg` contract, а `deleteChannel` — success/404 сценариями.
- следующим пакетом тот же orchestration boundary расширен уже на
  runtime-операции каналов:
  `test-message` прикрыт `404`, non-telegram guard, missing recipient
  guard и successful send в `group/channel` с deduplication,
  а `refresh bot info` — `404`, non-telegram guard, failure-path через
  `Telegram getMe` и successful persistence `bot_name/bot_username`.
- следующим пакетом `panel-bot orchestration boundary` расширен ещё глубже:
  `BotProcessApiController` теперь имеет явный success/error contract для
  `start/stop/status` и прикрыт failure-ветками;
  `runtime-contract` добран `max` payload-сценарием;
  `BotAutoStartServiceTest` добран ветками `null channel id` и
  `continue after failed start`;
  `ChannelApiControllerWebMvcTest` добран ветками
  `test-message all failed` и `manual recipient only`.
- следующим большим пакетом `Phase 6` расширен сразу по трём соседним
  `auth/runtime/network` boundary:
  `AuthManagementApiControllerWebMvcTest` теперь прикрывает users/roles CRUD
  edge-cases и success-ветки (`duplicate`, `empty update`, `not found`,
  `role in use`, `successful delete/update`);
  `IntegrationNetworkServiceTest` добран direct/profile failover context,
  incomplete proxy profile и direct env contract;
  `BotRuntimeContractServiceTest` добран ветками `vpn/default telegram`,
  `minimal vk`, `unknown platform`, `target-scan warning` и
  `jar-mode missing artifact`.
- в этом же пакете закрыт и реальный runtime defect:
  `BotRuntimeContractService.buildEnvironment()` больше не теряет базовые
  `JAVA_TOOL_OPTIONS` при network route, а merge-ит network-level options
  поверх base UTF-8/runtime flags.
- следующим расширенным пакетом `Phase 6` добран ещё глубже по
  `shared-config/channel-runtime/launcher-state` boundary:
  `SharedConfigServiceTest` теперь покрывает invalid JSON fallback для
  `settings/locations/org_structure/bot_credentials`;
  `ChannelApiControllerWebMvcTest` — create/update/runtime validation
  edge-cases (`missing name`, `telegram without token`,
  `vk without callback config`, `empty update payload`,
  `missing token`, `missing message`);
  `BotProcessServiceTest` — launcher/state ветки
  `status stopped`, `resolveExecutableJar -> null` и explicit `jar`
  launch plan по configured artifact.
- следующим крупным пакетом `Phase 6` расширен на
  `auth/profile/runtime controller` boundary:
  `ProfileApiControllerWebMvcTest` теперь покрывает unauthorized contract
  `ui-preferences` и основной password-flow
  (`unauthorized`, validation errors, missing user, wrong current password,
  successful password update);
  `AuthManagementApiControllerWebMvcTest` добран static-denial веткой
  `/api/users/{id}/password` и photo-upload контрактом
  (`empty file`, `unsupported extension`, `successful upload metadata`);
  `BotProcessApiController` сделан null-safe для `start/stop/status`, а
  `BotProcessApiControllerWebMvcTest` фиксирует `unknown` fallback
  при null-ответе runtime service.
- следующим расширенным пакетом `Phase 6` добран глубже по
  `auth/profile/channel-management` boundary:
  `ProfileApiControllerWebMvcTest` теперь прикрывает `password_hash` branch;
  `AuthManagementApiControllerWebMvcTest` — create-user persistence для
  `password_hash/enabled/registration_date` и denied-ветки
  `role.name/role.description`; `ChannelApiControllerWebMvcTest` —
  empty list, failed Telegram bot-info refresh tolerance, blank credential
  platform normalisation, default `is_active` и safe delete credential без
  лишнего `saveAll()`.
- следующим пакетом `Phase 6` расширен на
  `channel-management/auth-management/shared-config` boundary:
  `ChannelApiControllerWebMvcTest` теперь покрывает failed `saveAll()` после
  Telegram bot-info refresh, reject-сценарий VK platform switch без callback
  configuration, пустой результат Telegram `getMe`, sparse/null allocation
  нового credential id и cleanup нескольких связанных каналов при
  delete credential;
  `AuthManagementApiControllerWebMvcTest` — raw payload contract
  `/api/auth/org-structure`, create/update/delete persistence для optional
  `phones/role_id/role` и reject-ветку blank role name;
  `SharedConfigServiceTest` — nested settings round-trip и empty
  `bot_credentials` round-trip.
- следующим расширенным пакетом `Phase 6` добран и по
  `dialogs/settings controller edge-case` boundary:
  `DialogQuickActionsControllerWebMvcTest` теперь прикрывает domain error
  на `resolve`, null-body `categories`, invalid `snooze` и media failure;
  `DialogReadControllerWebMvcTest` — `public-form-metrics`, details без
  `channelId` и default `offset=0`;
  `DialogMacroControllerWebMvcTest` — alias `text` и variables без
  `ticketId`;
  `DialogTriagePreferencesControllerWebMvcTest` — missing body и fallback
  `updated_at_utc`;
  `DialogWorkspaceTelemetryControllerWebMvcTest` — null body и default
  summary window `days=7`;
  `DialogListControllerWebMvcTest` — empty dialogs payload;
  `SettingsParametersControllerWebMvcTest` и
  `SettingsItEquipmentControllerWebMvcTest` — trailing slash и `PATCH`
  contract.
- следующим ещё более широким пакетом `Phase 6` расширен по
  `ai-ops/public-form/settings-bridge/bot-process` boundary:
  `DialogAiOpsControllerWebMvcTest` теперь прикрывает missing body для
  `ai-control`, validation для `ai-learning-mapping`,
  `ai-solution-memory update`, `rollback history_id`, queue `limit`
  и alias `suggested_reply`;
  `PublicFormApiControllerWebMvcTest` — missing channel config,
  disabled form submit, session not found с `recordSessionLookup(false)`
  и history lookup с `channel` filter;
  `SettingsBridgeControllerWebMvcTest` — `PUT`, `PATCH` и trailing slash
  contract;
  `BotProcessApiControllerWebMvcTest` — blank exception message fallback
  для `runtime-contract`.
- следующим ещё более глубоким пакетом `Phase 6` добран по
  `DialogAiOps/PublicForm` controller boundary:
  `DialogAiOpsControllerWebMvcTest` теперь дополнительно покрывает
  `ai-control state`, `ai-review approve/reject`, alias `message_type`
  у `ai-reclassify`, alias+limit у `ai-retrieve-debug`, camelCase aliases
  у `ai-solution-memory update`, delete/history для solution memory,
  monitoring summary и offline-eval run;
  `PublicFormApiControllerWebMvcTest` — invalid disabled status fallback
  в `config`, validation error code mapping для
  `email/phone/captcha/idempotency` и `X-Real-IP` fallback при создании
  session.

---

## 🔴 Критические проблемы (P0)

### 1. Нарушение принципа разделения ответственности между ботами

**Локация:**
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
- `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java`

**Суть:** Bot runtime contract уже стал заметно лучше, но bot-модули всё ещё
используют core-сервисы напрямую и остаются тесно связаны с
платформ-специфичной реализацией. Полного adapter boundary пока нет.

**Последствия:**
- сложно менять runtime/transport contract для отдельных платформ;
- трудно тестировать платформы изолированно;
- изменения в core продолжают иметь широкий blast radius.

**Решение:** Дожать platform-adapter boundary поверх уже появившегося runtime
contract и перестать держать часть orchestration прямо в platform bot-классах.

### 2. Отсутствие явного слоя DTO между Entity и API

**Локация:**
- `spring-panel/src/main/java/com/example/panel/entity/Ticket.java`
- `spring-panel/src/main/java/com/example/panel/controller/TaskApiController.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/entity/Ticket.java`

**Суть:** DTO/model-слой в проекте уже есть, но используется непоследовательно.
Часть API по-прежнему слишком близка к внутренней модели данных.

**Последствия:**
- нет стабильной типизированной защиты API контракта;
- усложняется версионирование и эволюция API;
- сложнее отделять persistence-изменения от transport-контрактов.

**Решение:** Ввести и закрепить единый DTO/API contract слой с маппингом.

### 3. Частичная централизованная обработка ошибок

**Локация:**
- `spring-panel/src/main/java/com/example/panel/config/RestExceptionHandler.java`
- runtime-границы между `spring-panel` и `java-bot`

**Суть:** Базовый обработчик уже есть, но единый error contract пока не
распространён на весь REST-слой, новый sliced controller layer и runtime
boundary между `spring-panel` и `java-bot`.

**Последствия:**
- разные сценарии всё ещё могут возвращать неодинаковые ошибки;
- сложнее строить стабильный API и мониторинг ошибок;
- интеграционные runtime-ошибки не всегда имеют единый формат.

**Решение:** Довести `@RestControllerAdvice`/error contract до уровня
кросс-доменного стандарта.

---

## 🟠 Высокий приоритет (P1)

### 4. Монолитные сервисы со слишком большой ответственностью

**Актуальный фокус:**
- `DialogService` больше не giant и уже доведён до thin orchestration
  facade примерно на `275` строк после выноса telemetry analytics,
  external KPI rollout logic, rollout assessment / scorecard, rollout
  governance bounded contexts и зачистки мёртвого private legacy/support
  слоя;
- `DialogWorkspaceService` уже заметно сужен и после выноса
  `DialogWorkspaceRequestContractService` и
  `DialogWorkspacePayloadAssemblerService` находится примерно на уровне
  `327` строк, но всё ещё остаётся главным orchestration hotspot рядом с
  notifier/reply consumers;
- часть orchestration в `settings` уже разрезана, но remaining subdomains
  всё ещё требуют контроля.

**Важно:** `DialogApiController` больше не является главным transport-level
hotspot. Крупные controller-сценарии уже вынесены в отдельные controllers и
services, поэтому главный риск сместился в service layer.

**Решение:** `DialogService` уже доведён до thin facade, поэтому следующий
архитектурный выигрыш теперь не в дальнейшей “борьбе с giant class”,
а в том, чтобы не дать orchestration-risk переехать в соседние сервисы.
Ниже фактический каркас текущего split:

```text
DialogService
  ├─ already extracted:
  │   ├─ DialogLookupReadService
  │   ├─ DialogClientContextReadService
  │   ├─ DialogConversationReadService
  │   ├─ DialogDetailsReadService
  │   ├─ DialogResponsibilityService
  │   ├─ DialogTicketLifecycleService
  │   ├─ DialogAuditService
  │   ├─ DialogWorkspaceTelemetryDataService
  │   ├─ DialogWorkspaceTelemetryAnalyticsService
  │   ├─ DialogWorkspaceExternalKpiService
  │   ├─ DialogWorkspaceRolloutAssessmentService
  │   ├─ DialogWorkspaceRolloutGovernanceService
  │   ├─ DialogWorkspaceRequestContractService
  │   ├─ DialogWorkspacePayloadAssemblerService
  │   ├─ DialogMacroGovernanceSupportService
  │   └─ DialogMacroGovernanceAuditService
  ├─ next focus:
  │   ├─ DialogWorkspaceService
  │   ├─ reply/message write-side direct consumers
  │   ├─ AI/notification/escalation flows around notifier/runtime boundaries
  │   └─ remaining mapper / assembly tails around workspace consumers
  └─ DialogService itself is now a thin orchestration facade
```

### 5. Дублирование кода между модулями

**Примеры:**
- `SharedConfigService` реализован отдельно в:
  - `java-bot/bot-core/src/.../service/SharedConfigService.java`
  - `spring-panel/src/.../service/SharedConfigService.java`

**Суть:** После transport/runtime рефакторинга этот риск стал ещё заметнее:
конфигурационный contract должен быть единым, а не “похожим в двух местах”.
Сейчас это уже не просто внутренняя красота кода, а риск divergence между
panel runtime expectations и bot runtime behavior.

**Решение:** Вынести общий config contract/module или хотя бы общий documented
shared config boundary.

### 6. Отсутствие интерфейсов для сервисов как системного правила

**Проблема:** Проект по-прежнему в основном опирается на concrete classes.
Это не самый срочный риск, но он усиливает связанность крупных доменов.

**Решение:** Вводить интерфейсы не механически для всего подряд, а на границах
bounded contexts, orchestration и integration layers.

### 7. Нарушение layered architecture в bot-модулях

**Проблема:** Bot-классы продолжают смешивать роли platform adapter,
transport handler и orchestration entrypoint.

**Решение:** Продолжать `Phase 5` через более явный runtime/platform boundary и
не останавливаться на одном только launcher/env contract.

### 8. Неполное использование Spring Data JPA

**Проблема:** Внутри проекта сосуществуют raw `JdbcTemplate` и JPA/Repository.

**Суть риска:** Это уже не просто stylistic issue, а разные persistence-модели
внутри одного приложения, которые усложняют транзакции, тестирование и
эволюцию схемы.

**Решение:** Не делать большой bang refactor, а постепенно выравнивать
persistence boundaries по доменам.

---

## 🟡 Средний приоритет (P2)

### 9. Непоследовательное именование DTO/Model

DTO/model-слой существует, но naming и ответственность отличаются между
модулями и доменами.

### 10. Частично неформализованные Spring-конфигурации

Конфигурация приложения стала лучше формализована: у bot runtime уже есть
launcher/env/readiness contract и documented production recipe. Но часть
runtime/env ожиданий по проекту всё ещё держится на implicit conventions,
defaults и частично legacy-compatible фасадах.

Отдельный положительный сдвиг последних проходов: `BotAutoStartService`
больше не валит весь autostart cycle из-за `null/exception` на одном канале,
а `IntegrationNetworkService` и `BotRuntimeContractService` уже прикрыты
более широким набором proxy/vpn/vless edge-cases. Это снизило риск для
runtime boundary, но не снимает потребность в дальнейшем integration/e2e
слое поверх panel-bot orchestration.

Дополнительно улучшен и `bot-process/auth-management` boundary:
`BotProcessApiController` теперь не отдаёт сырые 500 без контракта на
`runtime-contract`, а `AuthManagementApiController` уже прикрыт не только
базовыми CRUD ветками, но и multi-column persistence сценариями
(`password_hash`, `enabled`, `phones`, multi-field role update). Это
снижает риск regressions в orchestration/API слое, хотя полноценного
integration-сценария поверх users/settings runtime boundary всё ещё нет.

### 11. Отсутствие сквозного API versioning

Часть маршрутов уже может быть стабилизирована, но единая стратегия
версионирования API по проекту так и не закреплена.

### 12. Ограниченное использование кэширования

Кэширование в проекте используется точечно и полезно, но не оформлено как
явная стратегия для горячих чтений.

### 13. Ограниченная доменная валидация

В части entity/model слоёв валидация всё ещё выражена слабо и часто живёт
не рядом с контрактом данных.

---

## 📊 Таблица соответствия правилам

| Правило | Статус | Комментарий |
|---------|--------|-------------|
| Layered Architecture | ⚠️ Частично | Controller-level split заметно улучшен, service-level split ещё не завершён |
| Dependency Inversion | ⚠️ Частично | На границах доменов стало лучше, но проект в целом всё ещё concrete-class heavy |
| Single Responsibility | ⚠️ Частично | Главный hotspot сместился из `DialogService` в `DialogWorkspaceService`, notifier/reply consumers и remaining settings slices |
| Don't Repeat Yourself | ❌ НЕТ | `SharedConfigService` и часть runtime contract still duplicated |
| SOLID Principles | ⚠️ Частично | Часть transport-layer нарушений снижена, но service boundaries ещё не доведены |
| Spring Best Practices | ⚠️ Частично | Улучшены bootstrap/runtime/test слои, но нужен единый error/API contract |

---

## 📈 Метрики качества

| Метрика | Текущее | Цель |
|---------|---------|------|
| Крупные controller hot spots | Сильно снижены | 0 giant controllers |
| Крупные service hot spots | Главный риск уже не giant-class size, а orchestration tails в `DialogWorkspaceService` (~327 строк), notifier/reply consumers и `settings` | bounded services по доменам |
| Regression safety net | Есть расширенный targeted unit/WebMvc/lifecycle/page-smoke net, включая settings governance, orchestration и ui-preferences, но он ещё не полный | широкий regression net |
| Code Coverage | Не формализована | 60%+ |
| Persistence consistency | Смешанный JDBC/JPA | явные domain boundaries |
| Shared runtime/config contract | Формализован лучше, но ещё не унифицирован между panel и bot | единый documented contract |
| Bot runtime boundary | launcher/env/readiness уже описаны и тестируются точечно | явный adapter/runtime boundary без legacy orchestration в bot-классах |

---

## 🎯 Актуальный план действий

### Фаза 1: Уже частично закрыта
- [x] Зафиксировать roadmap и начать поэтапный рефакторинг
- [x] Централизовать UI bootstrap и ownership UI preferences
- [x] Выполнить основной controller split для `dialogs/settings`

### Фаза 2: Текущий главный фокус
- [x] Дожать remaining bounded contexts и compatibility delegates в `DialogService`
- [ ] Досузить remaining orchestration tails в `DialogWorkspaceService` и consumer-фасады вокруг него
- [ ] Добить remaining `settings` subdomains
- [ ] Расширить и стабилизировать safety net для следующих крупных рефакторингов
- [ ] Закрыть remaining runtime/notifier хвосты, которые ещё держатся на legacy-compatible фасадах
- [ ] Решить, где следующий уровень проверки должен стать integration/e2e, а не только targeted runtime/unit net

### Фаза 3: Следующий архитектурный уровень
- [ ] Унифицировать shared config/runtime contract между `spring-panel` и `java-bot`
- [ ] Довести DTO/API contract до системного правила
- [ ] Закрепить единый error contract и API governance

---

## 📁 Следующие шаги

1. Следующим `Phase 3` пакетом забирать reply/message write-side и связанный escalation/notifier tail уже не из `DialogService`, а из их прямых consumers вокруг `DialogWorkspaceService`
2. Досузить `DialogWorkspaceService` по remaining payload/mapper/write-side хвостам, чтобы остаточный orchestration-risk не просто переехал из одного класса в другой
3. Добить remaining `settings` subdomains и persistence boundaries
4. Продолжить расширять `Phase 6` уже не только targeted unit/WebMvc tests, а integration-сценариями shared config/runtime и panel-bot orchestration boundary
5. После этого возвращаться к shared-config unification, DTO/error contract и общему API governance

### Что ещё заметно улучшилось в текущем проходе

- `DialogAiOpsController` теперь прикрыт уже не только happy-path и парой
  alias-сценариев, а почти полным controller-contract слоем: success/error/
  validation/list/update для `ai-suggestions`, `ai-review`,
  `ai-decision-trace`, `ai-intents`, `ai-knowledge-units`,
  `ai-solution-memory` и `ai-monitoring/offline-eval`.
- `PublicFormApiController` ушёл от чисто smoke/error-покрытия к более
  надёжному boundary: теперь под тестами находятся success payload `config`
  с вопросами и metadata, `recordConfigView`, mapping `required/max/min`
  validation-кодов, а также session fallback при unresolved channel id.
- `PublicFormController` тоже больше не ограничен одной bootstrap-проверкой:
  под safety net уже лежат `dialog` fallback в `initialToken`, `404` для
  unknown channel и configured disabled-status response.
- `PublicForm` boundary стал устойчивее ещё на один слой: добавлены
  remoteAddr fallback без proxy headers, generic validation fallback,
  success payload `session/messages`, precedence `token` над `dialog`,
  model attrs страницы и raw template contract для `public/form.html`.
- `DialogAiOpsController` теперь прикрыт не только по основным happy/error
  flows, но и по alias/null-body/default-path сценариям, что уменьшает риск
  regressions в transport-layer normalisation.
- `AuthManagementApiController` получил более плотный regression net по
  route-compatibility и optional-column flows: trailing slash,
  `PUT/PATCH` compatibility, `photo-upload`, decoded `phones`,
  admin/capability flags и ветки без `role_id`.
- `BotProcessApiController` стал лучше прикрыт по fallback/error contract:
  теперь под тестами и `null` exception message у `runtime-contract`,
  и case-insensitive success-path для `STOPPED`.
- `AuthManagementApiController` стал устойчивее ещё и на list/fallback
  уровне: теперь под тестами invalid `phones` JSON, invalid role
  `permissions` JSON, simple-query mode без `role_id`, parsed role
  permissions payload в `auth state`, а также empty-permissions flows
  для `createRole/updateRole`.
- `PublicFormApiController` больше не прикрыт только config/create
  ветками: у `session` теперь есть отдельный transport net на miss без
  history lookup и на success payload `clientName/clientContact/username/
  createdAt`.
- `BotProcessApiController` дополнительно прикрыт на `status/start`
  теми же runtime fallback сценариями, что раньше были только вокруг
  `stop/runtime-contract`: case-insensitive `STOPPED` и `null` message
  now explicitly locked by tests.
- giant `DialogService` за последние два `Phase 3` прохода перестал держать
  внутри себя и telemetry analytics, и external KPI rollout gate:
  эти bounded contexts теперь живут в
  `DialogWorkspaceTelemetryAnalyticsService` и
  `DialogWorkspaceExternalKpiService`, а сам giant service держит только
  orchestration/compatibility слой;
  дополнительно стабилизированы integration tests по rollout scorecard и
  governance packet, чтобы fresh/stale review logic не зависела от
  устаревающих фиксированных UTC timestamp’ов.
- следующим `Phase 3` пакетом giant `DialogService` потерял ещё и rollout
  decision / scorecard bounded context:
  `DialogWorkspaceRolloutAssessmentService` теперь владеет rollout action
  logic, scorecard assembly и external checkpoint itemization;
  `DialogService` после этого больше не держит constructor dependency на
  `DialogWorkspaceExternalKpiService`, а rollout integration tests
  подтверждают, что UTC scorecard / governance packet contract не сломался;
  текущий остаточный фокус giant service после этого сместился уже не в
  scorecard, а в rollout packet / governance packet / reply-write
  orchestration.
- следующим `Phase 3` пакетом giant `DialogService` потерял и rollout
  packet / governance packet bounded context:
  `DialogWorkspaceRolloutGovernanceService` теперь владеет governance
  packet assembly, parity exit criteria, legacy-only inventory, legacy
  manual-open policy и context-contract rollout packet orchestration;
  `DialogService` после этого держит только thin delegate на новый
  bounded service, а targeted rollout integration tests подтверждают,
  что UTC scorecard / governance packet contract не сломался и после
  этого выноса;
  текущий остаточный фокус giant service после этого сместился уже не
  в rollout packet, а в reply-write / escalation / notifier /
  compatibility delegates.
- следующим `Phase 3` пакетом из `DialogService` удалён уже мёртвый
  private legacy/support слой, который целиком дублировался в
  `DialogLookupReadService` и `DialogResponsibilityService`:
  старые `loadDialogsLegacy/findDialogLegacy`, responsible-profile
  enrichment helper’ы, users-table inspection и legacy responsibility
  private methods больше не живут в самом фасаде;
  после этого `DialogService` сжался уже примерно до `275` строк и
  фактически перестал быть service-level hotspot сам по себе;
  следующий реальный фокус после этого шага смещён уже в
  `DialogWorkspaceService`, notifier/reply consumers и remaining
  settings/runtime tails.
- следующим пакетом уже сам `DialogWorkspaceService` переведён на более
  узкий orchestration baseline: request-contract normalization вынесен в
  `DialogWorkspaceRequestContractService`, final payload assembly — в
  `DialogWorkspacePayloadAssemblerService`, а локальные
  include/limit/cursor/config helper’ы и финальный payload-builder удалены
  из самого workspace-сервиса;
  после этого `DialogWorkspaceService` сжался уже примерно до `327` строк,
  а следующий практический фокус сместился в reply/message write-side,
  notifier/escalation и remaining mapper tails вокруг workspace consumers.

**Автор исходного аудита:** GitHub Copilot  
**Статус:** Документ актуализирован под состояние кода на 30 апреля 2026
