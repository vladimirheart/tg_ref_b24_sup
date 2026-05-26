# Архитектурный аудит проекта Iguana CRM
**Дата:** 8 апреля 2026  
**Статус:** Актуально, но в активной фазе исправления  
**Актуализация:** 9 апреля 2026 (см. `docs/ARCHITECTURE_AUDIT_VALIDATION_2026-04-09.md`)  
**Последняя актуализация:** 19 мая 2026

---

## 📋 Что уже сделано

✅ Проведён исходный аудит `spring-panel` и `java-bot`  
✅ Проверена и скорректирована часть исходных выводов аудита  
✅ Зафиксирован roadmap рефакторинга в `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`  
✅ Начат и существенно продвинут рефакторинг transport-layer для `dialogs` и `settings`  
✅ Добавлен foundation-слой для UI runtime, preferences и page presets  
✅ Усилен `Phase 6` safety net через targeted unit/WebMvc/lifecycle/smoke tests  
✅ Формализован bot runtime contract через launcher-strategy, explicit artifact contract и runtime diagnostics  
✅ `Phase 3` по giant `dialogs` split завершён: `DialogService` доведён до thin facade  
✅ `Phase 4` по giant `settings` transport/update split завершён  
✅ Post-phase hardening по notifier/runtime сильно продвинут и больше не выглядит как giant-wrapper проблема  
✅ Macro-governance audit slice разрезан на bounded services и стабилизирован по legacy integration contract  

---

## 🧭 Текущее состояние

Этот документ больше нельзя читать как “чистый список проблем на старте”.
К 6 мая 2026 основные giant transport/service hotspots уже разрезаны, а
текущая работа сместилась в post-phase hardening и качество bounded
contracts.

### Короткий статус на сейчас

Что уже выполнено:

- `Phase 3` закрыта: giant `DialogService` больше не является hotspot и
  работает как thin orchestration facade;
- `Phase 4` закрыта: giant `settings` transport/update слой и соседние
  risky subdomains разрезаны;
- `Phase 6` safety net сильно расширен: targeted unit/WebMvc/runtime/page
  smoke coverage больше не точечная, а системная;
- notifier/runtime hardening вокруг SLA routing уже доведён до небольших
  bounded services вместо giant wrappers.
- macro-governance audit больше не является giant-helper проблемой:
  config/template/checkpoint/payload слой уже выделен и дополнительно
  стабилизирован по mixed SQLite timestamp / shared-config compatibility.

Что ещё остаётся:

- не допустить повторного разрастания `DialogWorkspaceService` и соседних
  workspace consumer tails;
- довести notifier/runtime слой уже не через giant split, а через
  integration-quality, shared-config/runtime consistency и локальные bounded
  cleanups;
- унифицировать shared config/runtime contract между `spring-panel` и
  `java-bot`;
- закрепить единый DTO/error/API contract на уровне проекта.

Что сейчас в приоритете:

1. `P1`: giant split вокруг `PublicFormService` практически закрыт: runtime
   config, metrics, session lookup/rotation, anti-abuse,
   submit/captcha/validation, form-definition/config assembly,
   persistence/projection, channel/config/session lookup и теперь ещё submit
   entry-flow coordinator уже вынесены в отдельные bounded services, сам
   `PublicFormService` сжат примерно до `81` строки; следующий practical
   focus там теперь уже не в giant split, а в runtime contract,
   integration-quality и API consistency.
2. `P1`: удержать AI assistant в post-split hardening: `DialogAiAssistantService`
   уже thin facade примерно на `208` строк, `DialogAiAssistantMessageFlowService`
   сжат до `267` строк, а decision/consistency/auto-reply outcome слой
   локализован в bounded `DialogAiAssistantMessageOutcomeService`
   (~`337` строк); следующий шаг там теперь не giant-cut, а только локальная
   compatibility/integration hardening.
3. `P1`: не дать orchestration-risk переехать в `DialogWorkspaceService` и
   смежные workspace/reply/notifier consumers.
4. `P1`: довести shared-config/runtime contract до более явного
   cross-module правила.
5. `P2`: стабилизировать DTO/error contract и persistence/API governance.
   Для `public-form` тут уже сделан ещё один шаг: controller-managed errors
   приведены к structured payload через `PublicFormApiResponseService`, а
   remaining contract/helper tail локализован в
   `PublicFormApiContractService`; следующий разумный фокус там уже не в
   controller helper split, а в integration/e2e/runtime contract coverage.

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
- следующим более широким пакетом сам macro-governance audit slice уже
  разрезан на `DialogMacroGovernanceConfigService`,
  `DialogMacroGovernanceTemplateAuditService`,
  `DialogMacroGovernanceCheckpointService` и
  `DialogMacroGovernanceAuditPayloadService`; вместе с этим возвращён
  compatibility baseline для mixed SQLite timestamps, legacy `deprecated_at`,
  minimum required checkpoints и historical noise heuristic, так что
  integration-сценарий `macroGovernanceAuditHighlightsOwnershipReviewAndUsageGaps`
  снова проходит;
- следующим пакетом и последний прямой consumer-хвост в основном
  service/controller-слое снят с giant service: `DialogWorkspaceTelemetrySummaryService`
  сначала был переведён на compatibility bridge, а затем и сам summary-слой
  ушёл в `DialogWorkspaceTelemetrySummaryAssemblerService`, поэтому текущая
  связность уже не держится на прямой зависимости от `DialogService` и не
  требует промежуточный bridge как постоянное решение;
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

- `DialogService` больше не является giant-service риском сам по себе:
  после выноса rollout, telemetry, macro governance и reply-adjacent tails
  он уже находится примерно на уровне `140` строк и работает как thin
  orchestration facade; основной residual risk сместился с размера класса на
  качество remaining compatibility/runtime boundaries вокруг него.
- `DialogWorkspaceService` всё ещё крупный по сравнению с остальными
  dialog-срезами, хотя уже начал разгружаться через
  выделенные workspace sub-services и уже прикрыт targeted service tests по
  parity, navigation, rollout, client profile, context blocks, client payload,
  context source policy и context contract;
- но сам `workspace` уже заметно сузился: из него дополнительно убраны
  мёртвые helper-блоки по SLA/source-coverage/export formatting;
- `settings` всё ещё содержит remaining subdomains, которые могут снова
  разрастаться в общих слоях; но после выноса
  `SettingsClientStatusService`,
  `SettingsItConnectionCategoryService` и
  `SettingsIntegrationNetworkProbeService` главный риск уже меньше в
  `SettingsApiController` как таковом и больше в `catalog/reference`,
  `partner/network` и части `bot/integration` сценариев вокруг соседних
  governance endpoints;
- `SharedConfigService` дублируется между `spring-panel` и `java-bot`;
- DTO/API contract и error contract всё ещё не унифицированы по проекту;
- persistence-слой по-прежнему смешивает raw JDBC и JPA/Repository подходы;
- boundary-wrapper слой вокруг telemetry/notifier уже подкреплён реальными
  `DialogWorkspaceTelemetryDataService`,
  `DialogMacroGovernanceSupportService` и теперь ещё
  `DialogWorkspaceTelemetrySummaryAssemblerService`; прямой
  consumer-зависимости `DialogWorkspaceTelemetrySummaryService ->
  DialogService` больше нет, а следующий шаг там уже не в bridge-cleanup,
  а в hardening notifier/runtime contracts и соседних orchestration tails;
- `DialogMacroGovernanceAuditService` уже не выглядит remaining hotspot:
  после выноса config/template/checkpoint/payload bounded services он стал
  thin coordinator, а риск сместился с giant audit-builder на
  shared-config/integration compatibility, которая теперь тоже частично
  стабилизирована regression-net’ом;
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
- соседний `SettingsApiController` тоже уже не остаётся серой зоной:
  `client statuses`, `it connection categories` и `integration network probe`
  вынесены в отдельные subdomain services, а поверх них добавлены
  `SettingsApiControllerWebMvcTest` и targeted unit tests, так что
  `settings` boundary прикрыт уже не только вокруг giant `/settings` update,
  но и вокруг catalog/network adjunct API;
- следующий adjacent governance хвост тоже уже вынесен:
  `AnalyticsMacroGovernancePolicyService` теперь владеет
  `macro governance review`, `external catalog policy` и
  `deprecation policy`, а `AnalyticsController` больше не держит эти
  settings-adjacent persistence/audit сценарии внутри себя;
- после этого `Phase 4` уже можно считать выполненной по исходной цели:
  giant settings transport/update слой разрезан, а remaining работа перешла
  в режим hardening и соседних governance/integration boundaries, а не
  незавершённого monolith split.
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

### Фаза 1: Базовый foundation
- [x] Зафиксировать roadmap и начать поэтапный рефакторинг
- [x] Централизовать UI bootstrap и ownership UI preferences
- [x] Выполнить основной controller split для `dialogs/settings`

### Фаза 2: Foundation hardening
- [x] Дожать remaining bounded contexts и compatibility delegates в `DialogService`
- [x] Добить remaining `settings` subdomains
- [x] Расширить и стабилизировать safety net для следующих крупных рефакторингов
- [x] Снять giant notifier/runtime wrappers до bounded services

### Фаза 3: Giant dialogs split
- [x] Довести `DialogService` до thin orchestration facade
- [x] Убрать giant dialog hotspot как главный архитектурный риск

### Фаза 4: Giant settings split
- [x] Разрезать giant `/settings` transport/update слой
- [x] Убрать `SettingsApiController` из списка главных hotspot’ов

### Фаза 5: Runtime contract
- [ ] Унифицировать shared config/runtime contract между `spring-panel` и `java-bot`
- [ ] Довести bot runtime/platform boundary до более явного cross-module правила

### Фаза 6: Quality and governance
- [ ] Досузить remaining orchestration tails в `DialogWorkspaceService` и вокруг workspace consumers
- [x] Досузить remaining message-processing/control tail в `DialogAiAssistantMessageFlowService`
- [x] Завершить thin-coordinator/hardening хвост `PublicFormService` вокруг `createSession`
- [ ] Решить, где следующий уровень проверки должен стать integration/e2e, а не только targeted runtime/unit net
- [ ] Довести DTO/API contract до системного правила
- [ ] Закрепить единый error contract и API governance

---

## 📁 Следующие шаги

1. Следующим крупным пакетом идти уже не в giant split `PublicFormService`:
   runtime config, metrics, session flow, anti-abuse,
   submit/captcha/validation, form-definition/config assembly,
   persistence/projection, channel/config/session lookup и submit
   entry-flow coordinator уже вынесены, а remaining риск теперь сидит в
   runtime contract hardening, integration/e2e coverage и API consistency.
2. Параллельно держать AI assistant уже в post-split hardening:
   `DialogAiAssistantMessageFlowService` и
   `DialogAiAssistantMessageOutcomeService` должны оставаться локальными
   bounded services без повторного роста в giant coordinator.
3. Параллельно удержать под контролем `DialogWorkspaceService` и соседние
   workspace consumers, чтобы orchestration-risk не переехал туда после
   уже закрытого giant `DialogService`.
4. `DialogWorkspaceRolloutGovernanceService` держать уже в режиме hardening и
   compatibility regression, а не как giant-split priority.
5. notifier/runtime hardening продолжать только адресно:
   по integration-quality и compatibility, а не через новый giant split.
6. После этого поднимать уровень cross-module unification:
   `SharedConfigService`, runtime contract, DTO/error contract и API
   governance.

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
- Следующим более широким post-split пакетом `PublicFormApiController`
  переведён на отдельный `PublicFormApiResponseService`: success payload
  assembly вынесен из controller, а controller-managed error responses
  нормализованы до structured contract с `path/timestamp`.
- Следующим bounded API-consistency пакетом `PublicFormApiController`
  досужен ещё на один слой: новый `PublicFormApiContractService` забрал
  disabled-status fallback, requester-context resolution, error-code mapping
  и token masking, а malformed-body transport contract теперь закреплён
  отдельным WebMvc сценарием.
- После этого `PublicFormApiController` сжат примерно до `156` строк и
  превратился в уже довольно thin public-form transport boundary поверх
  `PublicFormApiResponseService` и `PublicFormApiContractService`; следующий
  practical focus смещён в integration/e2e/runtime contract coverage.
- Следующим integration-пакетом `PublicFormFlowSmokeIntegrationTest`
  расширен уже не только на happy-path submit, но и на real-app runtime
  contract: missing channel, disabled form, malformed body и session miss
  теперь проверяются в живом `SpringBootTest` контексте с SQLite и
  structured `errorCode/path/timestamp` payload.
- Следующим более широким integration-пакетом тот же
  `PublicFormFlowSmokeIntegrationTest` добран до continuation/session
  lifecycle: platform-specific continuation payload для `telegram` и `max`,
  telegram deep-link generation и rotate-on-read token lifecycle теперь
  проверяются в живом runtime contract через `SpringBootTest` + SQLite +
  temp shared config.
- Следующим более широким runtime-hardening пакетом тот же
  `PublicFormFlowSmokeIntegrationTest` добран до anti-abuse/expiry
  contract: HTTP idempotency reuse, structured `IDEMPOTENCY_CONFLICT`,
  live `RATE_LIMITED` rejection и `public_form_session_ttl_hours` expiry
  теперь тоже проверяются в живом `SpringBootTest` + SQLite сценарии.
- Следующим более широким lifecycle-пакетом тот же
  `PublicFormFlowSmokeIntegrationTest` добран до polling/history contract:
  live `sessionPollingEnabled/sessionPollingIntervalSeconds`, shared
  conversation history после operator reply и system notifications, а также
  `replyPreview` для threaded ответа теперь тоже закреплены в реальном
  `public-form` session runtime.
- Следующим более широким continuity-пакетом тот же
  `PublicFormFlowSmokeIntegrationTest` добран до cross-session/history
  continuity: `previous history` теперь проверяется на двух `web_form`
  обращениях одного requester, включая `sourceKey/sourceLabel` и resolved
  status предыдущего тикета, а resolve/reopen lifecycle через
  `DialogQuickActionService` закреплён и в `public-form` session history.
- Следующим более широким bridge/runtime-пакетом тот же
  `PublicFormFlowSmokeIntegrationTest` добран до live `public-form ->
  dialogs` read bridge: `/api/dialogs/{ticketId}` и
  `/api/dialogs/{ticketId}/history` теперь проверяются на shared history
  для `web_form` тикета, а `DialogReadService` дополнительно закреплён на
  `last_read_at` update для ответственного оператора; рядом тем же smoke
  слоем зафиксирован и `/api/dialogs/public-form-metrics` runtime bridge
  на живые `views/submits/sessionLookups/sessionLookupMisses`.
- Следующим более широким lifecycle/triage-пакетом тот же
  `PublicFormFlowSmokeIntegrationTest` добран уже до live
  `public-form -> dialogs` list/details bridge: `/api/dialogs` теперь
  проверяется на `summary`, `statusKey` переходы `new ->
  waiting_operator -> waiting_client`, critical SLA escalation signals и
  operator ownership lifecycle для `web_form` тикета; соседним сценарием
  закреплены `resolved`/categories projection в `/api/dialogs/{ticketId}`
  и continuity этого же resolved dialog через
  `/api/dialogs/{ticketId}/history/previous`.
- Следующим runtime-пакетом тот же `PublicFormFlowSmokeIntegrationTest`
  добран до `public-form -> notification routing` continuity: для
  follow-up обращения теперь зафиксированы operator bell notification
  creation и read-reset через live `NotificationService summary`, а для
  dialog participant lifecycle закреплены peer-notifications на
  `resolve/reopen` ветках вместе с `resolved/categories` состоянием самого
  `web_form` dialog.
- `NotificationApiController` получил первый dedicated WebMvc regression
  net по identity resolution boundary: `Authentication -> UserDetails`,
  fallback на `authentication.getName()` и explicit `all`-ветка теперь
  отдельно закреплены для `list`, `unread_count` и `markAsRead`, что
  уменьшает риск тихих regressions после дальнейших security/runtime
  изменений.
- следующим более широким runtime-пакетом notification layer добран уже до
  live `SpringBootTest + SQLite` контракта: отдельный
  `NotificationApiIntegrationTest` теперь фиксирует real
  `list/unread_count/markAsRead`, identity scope и runtime bridge от
  `NotificationService.notifyUsersExcluding`, а по пути закрыт production
  bug в `Notification.createdAt` SQLite read-path через
  `LenientOffsetDateTimeConverter` и снята `JdbcTemplate.query(...)`
  ambiguous-сборка в `NotificationService`.
- следующим service/runtime continuity шагом notification слой добран уже
  до `SupportPanelIntegrationTests`: там теперь отдельно закреплены
  recipient merge из `ticket_responsibles + ticket_active`, operator
  fallback для пустого dialog audience и cross-database filtering для
  operator recipient pool. Параллельно снят соседний compile-blocker в
  `DialogLookupReadService` (`usersJdbcTemplate.query(...)`), чтобы
  dialog list/details enrichment по responsible profiles снова стабильно
  проходил в live SQLite integration сценариях.
- следующим alert/routing hardening пакетом добран уже соседний
  `NotificationRoutingService` / `AlertQueueService` слой: добавлены
  dedicated service tests на `employees_only`, `department_except`,
  `online_only_fallback_all` и legacy `alertQueue` routing, исправлен
  mojibake в incoming-client alert text, а local SQLite timestamps теперь
  корректно участвуют в online-recipient filtering. По пути снят ещё один
  `JdbcTemplate.query(...)` ambiguous compile-blocker в
  `NotificationRoutingService`.
- следующим orchestration continuity пакетом добран уже
  `OperatorNotificationWatcher`: добавлен dedicated test net на
  incoming-message alert routing, initial `public_form_submit` branch и
  first-response-overdue fallback. По пути закрыт production-bug с wrong
  `JdbcTemplate.query(...)` overload в `watchChatHistoryMessages` /
  `watchFeedbacks`, из-за которого watcher мог пропускать строки, и
  добавлен fallback на operator audience для overdue alerts, если
  `AlertQueueService` не смог отдать notification дальше по route.
- следующим runtime consistency пакетом закрыто дублирование initial
  `public-form` alerts между `PublicFormSubmissionPersistenceService`,
  `AlertQueueService` и `OperatorNotificationWatcher`: queue-delivery на
  submit теперь фиксируется отдельным successful audit action
  `public_form_new_appeal_notification`, а watcher уважает этот маркер и
  не шлёт второй `notifyAllOperators(...)` для первого client message. При
  этом legacy fallback сохранён: если queue route не нашёл recipients и
  persistence записал `skipped`, watcher по-прежнему может поднять initial
  notification самостоятельно.
- следующим operator-facing continuity пакетом source-layer notification
  links тоже доведены до актуального dialog route прямо в точках генерации:
  `AlertQueueService`, `OperatorNotificationWatcher`,
  `DialogAiAssistantEscalationService`,
  `DialogAiAssistantOperatorFeedbackService` и `DialogQuickActionService`
  теперь строят URL через общий `NotificationService.buildDialogUrl(...)`,
  а не через legacy `?ticketId` строки. Это убирает скрытую зависимость от
  поздней normalize-магии в `NotificationService` и фиксирует единый
  transport contract уже на источнике событий. Параллельно корневой
  `.gitignore` теперь игнорирует `/logs/`, чтобы локальные Maven/runtime
  прогоны меньше шумели при синхронизации рабочего дерева.
- следующим orchestration follow-up пакетом `DialogQuickActionService`
  получил dedicated service-level regression net на `sendReply`,
  `resolveTicket`, `reopenTicket` и `takeTicket`: теперь quick-action
  lifecycle закреплён не только через controller/WebMvc и
  `PublicFormFlowSmokeIntegrationTest`, но и на самом orchestration слое с
  явной проверкой `clearProcessing`, operator-feedback handoff,
  resolved/reopened notifications и participant notification continuity.
- следующим расширением этого же continuity пакета `DialogQuickActionService`
  добран уже до `sendMediaReply`, `updateCategories`,
  `addParticipant`, `removeParticipant` и `reassignTicket`: service-level net
  теперь фиксирует media attachment payload/result contract,
  category-update notifications и operator-collaboration lifecycle вокруг
  participant/reassign веток, а remaining practical focus смещён уже не в
  базовые orchestration branches, а в integration/runtime continuity для
  `edit/delete` и соседних dialog-side effects.
- следующим follow-up шагом этот remaining хвост тоже закрыт на service-level:
  `DialogQuickActionServiceTest` теперь дополнительно прикрывает
  `editReply` и `deleteReply` success/failure ветки, так что quick-action
  orchestration net охватывает уже и message mutation side-effects через
  dialog-route notifications. После этого следующий practical focus здесь
  смещён уже не в расширение unit coverage, а в более живой
  integration/runtime continuity для quick-action side-effects на реальном
  dialog history и participant audience.
- следующим transport-level follow-up пакетом расширен и
  `DialogQuickActionsControllerWebMvcTest`: кроме уже закрытых
  `reply/resolve/take/media/categories/snooze` веток он теперь прикрывает
  `edit`, `delete`, `reopen`, `participants add/remove` и `reassign`
  payload/status/audit contract. Это снимает ещё один controller-boundary
  risk вокруг quick actions; remaining practical focus здесь теперь уже
  честно смещён в live runtime continuity, а не в дальнейшее наращивание
  WebMvc/unit surface.
- следующим runtime continuity шагом `SupportPanelIntegrationTests`
  закрепили уже живой SQLite lifecycle для `DialogQuickActionService`
  participant/reassign веток: add/remove participant и reassign теперь
  проверяются не только на service/WebMvc контракте, но и на реальных
  `ticket_participants`, `ticket_responsibles`, `DialogReadService`
  projection и notification audience side-effects. Параллельно test cleanup
  синхронизирован с `ticket_participants`, `ticket_active` и
  `ticket_responsibles`, чтобы этот слой оставался повторяемым.
- следующим dialog-read continuity пакетом добавлен dedicated
  `DialogReadIntegrationTest`, а `DialogReadControllerWebMvcTest`
  расширен на remaining read endpoints. Теперь `history`,
  `history/previous`, `participants` и `operators` закреплены не только на
  unit/WebMvc делегации, но и на живом `SpringBootTest + SQLite` contract:
  `replyPreview`, `originalMessage`, `editedAt`, `deletedAt`,
  `forwardedFrom`, `last_read_at` read receipt и users-directory projection
  проходят через реальный runtime слой даже при schema-drift по optional
  колонкам. После этого remaining practical focus в dialog-read ветке
  смещён уже не в transport gaps, а в `details/workspace` runtime
  continuity и соседние operator-facing projections.
- следующим bounded пакетом закрыт уже и live `/workspace` runtime gap:
  добавлен `DialogWorkspaceIntegrationTest`, а
  `DialogWorkspaceControllerWebMvcTest` расширен на default envelope path.
  Теперь `workspace` закреплён не только на service-level assembly и
  controller delegation, но и на реальном `SpringBootTest + SQLite`
  contract: `messages` pagination, `replyPreview`, `last_read_at`,
  `permissions/composer` parity, inline navigation и default
  `context/history/related_events` projection проходят через настоящий
  route `/api/dialogs/{ticketId}/workspace`. После этого remaining
  practical focus вокруг dialog-read/workspace смещён уже не в базовый
  transport/runtime bootstrap, а в более глубокие `details` continuity и
  context-contract/settings-driven edge cases.
- следующим bounded пакетом закрыт и live `/api/dialogs/{ticketId}`
  details gap: добавлен `DialogDetailsIntegrationTest`,
  `DialogReadControllerWebMvcTest` расширен на `404`-ветку, а
  `DialogDetailsReadServiceTest` добран miss-path short-circuit сценарием.
  Теперь `details` route закреплён не только через старые service calls, но
  и на реальном `SpringBootTest + SQLite` contract: summary/history/categories,
  responsible profile projection, embedded `replyPreview/originalMessage`,
  `last_read_at` read receipt и explicit not-found payload проходят через
  настоящий runtime слой. После этого remaining practical focus в
  dialog-read/workspace зоне смещён уже с basic details continuity на
  settings-driven context-contract, parity и related projection edge cases.
- следующим bounded пакетом закрыт и settings-driven `workspace`
  context-contract runtime edge case: `DialogWorkspaceIntegrationTest`
  расширен на live contract scenario с rollout-required `billing` config,
  mandatory/source-of-truth/priority-block violations и playbook projection,
  а `DialogWorkspaceContextContractServiceTest` добран `invalid_utc`
  source-of-truth веткой со scoped playbook. Теперь `dialog-read/workspace`
  зона прикрыта не только по basic runtime continuity, но и по живому
  settings-driven contract drift вокруг `customer_profile/context_sources`.
  Следующий practical focus здесь уже смещён дальше в parity/related
  projections и operator-facing workspace context edges.
- следующим bounded пакетом закрыт и live `workspace` degradation/runtime
  слой: `DialogWorkspaceIntegrationTest` теперь фиксирует partial
  `include=context,permissions`, settings-driven limits для
  `history/related_events`, disabled inline navigation и parity attention
  path без `messages/sla`, а `DialogWorkspaceNavigationServiceTest`
  дополнительно закрепляет legacy local-datetime normalization для queue
  items. После этого practical focus в dialog-read/workspace зоне
  смещён уже глубже в operator-facing composer/permissions parity и
  adjacent read-model projection edges, а не в базовые related/navigation
  contracts.
- следующим bounded пакетом закрыт и operator-facing `workspace`
  permissions/composer parity edge: `DialogWorkspaceIntegrationTest`
  теперь фиксирует live `/api/dialogs/{ticketId}/workspace` scenario с
  explicit denied permissions, а `DialogWorkspaceParityServiceTest`
  добран ветками на composer disable и distinction между `attention` и
  `blocked`. Это закрепляет реальный runtime contract: incomplete
  permission envelope остаётся `blocked`, а explicit boolean deny даёт
  `attention` с missing `reply_threading/media_reply`, но без деградации
  `operator_actions`. После этого practical focus в dialog-read/workspace
  зоне смещён уже не в базовое parity semantics, а в deeper
  operator-facing projection drift и соседние read-model/composer edges.
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
- В том же `PublicFormApiController` зафиксирован ещё один маленький
  contract-hardening сценарий: bean-validation required-path для пустого
  `message` теперь тоже возвращает explicit `VALIDATION_REQUIRED`, а не
  generic `VALIDATION_ERROR`.
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
- следующим более широким пакетом эти reply/runtime хвосты тоже начали
  резаться в коде:
  `DialogReplyTargetService` теперь владеет target lookup, ticket activity,
  web-form fallback и reply persistence, а
  `DialogReplyTransportService` — Telegram/VK/MAX text/media transport;
  `DialogReplyService` после этого сжат уже примерно до `194` строк и стал
  thin write-side orchestration слоем поверх bounded dependencies.
- этим же пакетом убран и последний telemetry-summary bridge tail вокруг
  `DialogService`: появился
  `DialogWorkspaceTelemetrySummaryAssemblerService`, старый
  `DialogWorkspaceTelemetrySummaryBridgeService` удалён, а
  `DialogWorkspaceTelemetrySummaryService` и `DialogService` делегируют
  напрямую в новый bounded service;
  после этого `DialogService` сжался уже примерно до `140` строк и больше
  не выглядит как самостоятельный giant-service hotspot.
- по исходной архитектурной цели `Phase 3` теперь можно считать выполненной:
  основной dialog monolith split завершён, а remaining риск смещён не в
  `DialogService`, а в adjacent notifier/reply/telemetry/runtime boundaries
  и качество integration contracts вокруг уже вынесенных services.
- следующим hardening-пакетом эти adjacent boundaries тоже сузились:
  `DialogWorkspaceTelemetryService` переведён на
  `DialogWorkspaceTelemetryControlService` и сжался примерно до `222` строк;
  `WorkspaceGuardrailWebhookNotifier` после выноса
  `WorkspaceGuardrailWebhookCommandService` и
  `WorkspaceGuardrailWebhookDeliveryService` сжался примерно до `43` строк;
  `SlaEscalationWebhookNotifier` потерял собственный webhook
  endpoint/delivery хвост через `SlaEscalationWebhookDeliveryService`.
- после этого главный оставшийся notifier/runtime hotspot уже локализован
  точнее: это не весь telemetry/notifier perimeter, а прежде всего giant
  `SlaEscalationWebhookNotifier` (~`2078` строк) с его governance-audit и
  auto-assign orchestration слоями. Именно туда теперь логично направлять
  следующий большой refactor-пакет.
- следующим широким пакетом этот hotspot уже начал реально разрезаться:
  `SlaEscalationCandidateService` вынес candidate scan и SLA breach/risk
  filtering, `SlaEscalationAutoAssignService` — rule matching, assignee pools,
  round-robin/least-loaded routing и operator load guards; сам
  `SlaEscalationWebhookNotifier` после перевода на новые bounded services
  сжался примерно до `1967` строк. После этого главным remaining notifier-risk уже успел стать не auto-assign orchestration, а governance-audit / routing-policy audit tail.

**Автор исходного аудита:** GitHub Copilot  
**Статус:** Документ актуализирован под состояние кода на 1 мая 2026

- следующим более широким пакетом этот tail тоже вынесен из
  SlaEscalationWebhookNotifier: новый SlaRoutingPolicyService теперь
  держит uildRoutingPolicySnapshot(...), uildRoutingGovernanceAudit(...)
  и связанный rule-definition/governance helper слой; сам notifier после этого
  сжат уже примерно до 350 строк и перестал быть главным runtime hot spot.
- после этого основной remaining риск в notifier/runtime boundary смещён в
  SlaRoutingPolicyService (~1786 строк), а не в transport/delivery wrapper;
  следующий логичный split там — governance review packet, issue
  classification и rule-definition parsing bounded contexts.
- следующим большим пакетом этот слой тоже разрезан: `SlaRoutingRuleAuditService`
  вынес из `SlaRoutingPolicyService` rule-definition parsing, conflict/broad-rule
  analysis, issue classification и rule-level audit payload assembly.
- после этого `SlaRoutingPolicyService` уже не выглядит как giant runtime
  hotspot сам по себе: он сжат примерно до `639` строк и выполняет роль
  governance overlay / итогового audit summary слоя.
- новый remaining hotspot в этом периметре теперь точнее локализован в
  `SlaRoutingRuleAuditService` (~`778` строк), а не в notifier wrapper или policy
  facade; следующий логичный split там — parser/normalization bounded context и
  отдельно governance issue matrix assembly.
- следующим ещё более широким пакетом этот bounded context тоже разрезан:
  `SlaRoutingRuleParserService` забрал rule normalization / candidate-match /
  rule-definition parsing, `SlaRoutingGovernanceIssueService` — rule-level
  governance issue matrix и payload assembly.
- после этого `SlaRoutingRuleAuditService` уже не выглядит как hotspot сам по
  себе: он сжат примерно до `161` строки и выполняет роль coordinator-слоя.
- новый remaining notifier/runtime hotspot теперь локализован ещё точнее в
  `SlaRoutingRuleParserService` (~`444` строки), а не в notifier, policy facade
  или audit coordinator.
- следующим ещё более широким пакетом и этот parser-boundary тоже добит:
  `SlaRoutingRuleTypes` вынес общие rule DTO/enum типы,
  `SlaRoutingRuleScalarParserService` — scalar/temporal parsing,
  `SlaRoutingRuleMatchNormalizerService` — match/category/state normalization,
  `SlaRoutingGovernanceIssueFactoryService` — issue payload factory; старый
  `SlaRoutingRuleValueParserService` после этого удалён.
- после этого `SlaRoutingRuleParserService` уже не выглядит как hotspot сам по
  себе: он сжат примерно до `138` строк, `SlaRoutingGovernanceIssueService` —
  до `118`, `SlaRoutingRuleAuditService` — до `145`.
- параллельно из `SlaRoutingPolicyService` убран локальный UTC/trim helper-tail
  для governance review checkpoint parsing, и сам policy layer сжат примерно
  до `586` строк.
- новый remaining notifier/runtime hotspot теперь снова локализован в
  `SlaRoutingPolicyService` (~`586` строк), прежде всего в governance review /
  checkpoint summary / audit overlay, а не в parser/value helper bounded
  context.
- следующим ещё более широким пакетом и этот tail заметно сузился:
  `SlaRoutingPolicyConfigService` вынес shared config/runtime parsing,
  `SlaRoutingGovernanceReviewService` — governance review state, issues,
  requirements и review payload fragments.
- после этого `SlaRoutingPolicyService` уже не смешивает parsing/review state с
  orchestration: он сжат примерно до `448` строк и выполняет роль summary /
  checkpoint coordinator поверх candidate scan и rule audit.
- новый remaining notifier/runtime hotspot теперь локализован ещё уже в
  `SlaRoutingPolicyService` (~`448` строк), а не в config/review parsing слоях;
  следующий логичный bounded split там — финальный summary/checkpoint assembly
  и advisory-path metrics.
- следующим ещё более широким пакетом и этот facade-tail тоже разрезан:
  `SlaRoutingPolicySnapshotService` вынес полный snapshot preview flow,
  `SlaRoutingGovernanceSummaryService` — финальный audit summary /
  checkpoint / advisory-path assembly.
- после этого `SlaRoutingPolicyService` уже не выглядит как hotspot сам по
  себе: он сжат примерно до `123` строк и выполняет роль thin coordinator.
- новый remaining notifier/runtime hotspot теперь локализован уже не в policy
  facade, а в `SlaRoutingGovernanceSummaryService` (~`223` строки) и вторично
  в `SlaRoutingPolicySnapshotService` (~`181` строк), то есть риск стал гораздо
  уже и локальнее.
- это уже post-phase hardening, а не незавершённый giant split:
  `Phase 3` и `Phase 4` остаются выполненными, а основной архитектурный риск
  смещён в локальные notifier/runtime bounded services и integration-quality
  хвосты вокруг них.
- следующий логичный пакет теперь уже не про `SlaRoutingPolicyService`, а про
  дочистку `SlaRoutingGovernanceSummaryService` на более мелкие bounded части:
  отдельно `checkpoint/advisory-path metrics`, отдельно финальный governance
  summary payload assembler, если этот слой продолжит расти.
- вторым приоритетом после этого остаётся `SlaRoutingPolicySnapshotService`:
  если snapshot preview начнёт снова разрастаться, его безопаснее резать по
  границе `candidate payload assembly` против `summary/advisory preview`.
- следующим пакетом и этот tail уже реально сузился:
  `SlaRoutingGovernanceCheckpointService` вынес required/advisory checkpoint
  metrics, `SlaRoutingGovernanceAuditPayloadAssemblerService` — финальную audit
  payload assembly, а `SlaRoutingGovernanceSummaryService` после этого сжат
  примерно до `127` строк и стал coordinator-слоем.
- новый remaining notifier/runtime hotspot теперь локализован уже не в summary
  service, а в `SlaRoutingPolicySnapshotService` (~`202` строки) и вторично в
  `SlaRoutingGovernanceCheckpointService` (~`189` строк); риск снова сузился до
  двух локальных bounded services, а не orchestration wrapper.
- следующим пакетом и snapshot-tail тоже разрезан:
  `SlaRoutingPolicyCandidateBuilderService` вынес candidate payload assembly,
  `SlaRoutingPolicyPreviewSummaryService` — preview summary text,
  `SlaRoutingPolicyDecisionService` — critical decision tail; сам
  `SlaRoutingPolicySnapshotService` после этого сжат примерно до `136` строк.
- после этого remaining notifier/runtime hotspot уже не в mixed snapshot
  helper-слое: он сместился в `SlaRoutingGovernanceCheckpointService`
  (~`189` строк) и вторично в `SlaRoutingPolicySnapshotService` (~`136`
  строк), а decision/candidate preview уже живут в отдельных bounded services.
- следующим более широким пакетом и checkpoint-tail тоже сузился:
  `SlaRoutingGovernanceReviewPathService` вынес minimum required review path и
  advisory checkpoint builder, `SlaRoutingGovernanceSignalService` — noise,
  churn, lead-time и weekly-review priority signals; сам
  `SlaRoutingGovernanceCheckpointService` после этого сжат примерно до `127`
  строк и стал coordinator-слоем.
- после этого remaining notifier/runtime hotspot локализован уже ещё уже:
  первично это `SlaRoutingPolicySnapshotService` (~`136` строк) и вторично
  `SlaRoutingGovernanceSignalService` (~`132` строки), а path/checkpoint
  orchestration уже живёт в отдельных bounded services.
- следующим ещё более широким пакетом и эти два хвоста тоже сузились:
  `SlaRoutingGovernanceLeadTimeService` вынес lead-time/risk evaluation,
  `SlaRoutingGovernancePriorityService` — weekly-review priority и checkpoint
  closure policy, `SlaRoutingPolicySnapshotStateService` — base snapshot
  header и pre-critical state payloads.
- после этого `SlaRoutingGovernanceSignalService` сжат примерно до `96`
  строк, `SlaRoutingPolicySnapshotService` — до `121`, а remaining
  notifier/runtime hotspot локализован уже в совсем малых bounded services,
  а не в общем routing hardening wrapper.
- следующим ещё более широким пакетом и review/config слой тоже сузился:
  `SlaRoutingGovernanceReviewStateService` вынес governance review state и
  issue evaluation, `SlaRoutingGovernanceReviewPayloadService` — requirements и
  governance review payload builders, `SlaRoutingPolicyTimeService` —
  UTC/minutes-left parsing, `SlaRoutingLifecycleStateService` — lifecycle
  normalization.
- после этого `SlaRoutingGovernanceReviewService` сжат примерно до `86`
  строк, `SlaRoutingPolicyConfigService` — до `89`, а remaining
  notifier/runtime hotspot смещён уже в `SlaRoutingGovernanceReviewStateService`
  (~`167` строк) и рядом с ним в `SlaRoutingRuleTypes` / rule normalization
  bounded contexts, а не в mixed orchestration services.
- следующим ещё более широким пакетом и этот tail тоже сузился:
  `SlaRoutingGovernanceReviewDecisionService` вынес freshness/decision/timestamp
  evaluation, `SlaRoutingGovernanceReviewIssueService` — issue collection для
  governance review, а `SlaRoutingRuleBehaviorService` забрал matcher,
  specificity, route и assignee-target heuristics из `SlaRoutingRuleTypes`.
- после этого `SlaRoutingGovernanceReviewStateService` сжат примерно до `72`
  строк, `SlaRoutingRuleTypes` — до `47`, `SlaRoutingRuleParserService` — до
  `123`, `SlaRoutingGovernanceIssueService` — до `121`, а
  `SlaRoutingRuleAuditService` остаётся thin coordinator примерно на `148`
  строках.
- новый remaining notifier/runtime hotspot теперь уже не в governance review
  state и не в rule DTO: он смещён в `SlaRoutingRuleBehaviorService`
  (~`151` строк) и вторично в `SlaRoutingRuleAuditService` (~`148` строк),
  то есть риск локализован в компактных rule-behavior / audit bounded
  services, а не в orchestration facade.
- следующим ещё более широким пакетом и этот слой тоже сузился:
  `SlaRoutingRuleMatchService` вынес match/empty-rule логику,
  `SlaRoutingRuleDescriptorService` — specificity/route/assignee-target,
  `SlaRoutingRuleUsageAnalysisService` — usage/conflict analysis,
  `SlaRoutingRuleAuditMetricsService` — decisions/issues aggregates,
  `SlaRoutingPolicyDecisionPayloadService` — ready/manual-review/webhook-only
  payload assembly.
- после этого `SlaRoutingRuleBehaviorService` сжат примерно до `45` строк,
  `SlaRoutingRuleAuditService` — до `103`, `SlaRoutingPolicyDecisionService`
  — до `56`, а новый remaining notifier/runtime tail локализован уже в
  `SlaRoutingRuleParserService` (~`123` строки), `SlaRoutingRuleMatchService`
  (~`110`) и `SlaRoutingPolicySnapshotService` (~`107`), то есть в реально
  компактных bounded services без giant-wrapper симптомов.
- следующим ещё более широким пакетом и parser/snapshot tail тоже сузился:
  `SlaRoutingRuleDefinitionFactoryService` вынес rule-definition assembly,
  `SlaRoutingRuleCandidateContextService` — normalized candidate context и
  ticket-id extraction, `SlaRoutingPolicySnapshotRuntimeService` — runtime
  context assembly, `SlaRoutingPolicySnapshotBranchService` — early-exit branch
  resolution.
- после этого `SlaRoutingRuleParserService` сжат примерно до `83` строк,
  `SlaRoutingPolicySnapshotService` — до `56`, а remaining notifier/runtime
  хвост уже локализован в `SlaRoutingRuleMatchService` (~`110` строк),
  `SlaRoutingRuleAuditService` (~`103`) и вторично в
  `SlaRoutingPolicySnapshotRuntimeService` (~`81`), то есть giant-tail по
  сути уже снят даже на post-phase hardening уровне.
- следующим ещё более широким пакетом и этот слой тоже сузился:
  `SlaRoutingRuleDimensionMatchService`, `SlaRoutingRuleThresholdMatchService`
  и `SlaRoutingRuleRequestMatchService` вынесли отдельные match families,
  `SlaRoutingRuleAuditEvaluationService` — per-rule audit evaluation pass,
  `SlaRoutingPolicySnapshotDialogStateService` — dialog-state enrichment.
- после этого `SlaRoutingRuleMatchService` сжат примерно до `40` строк,
  `SlaRoutingRuleAuditService` — до `76`, `SlaRoutingPolicySnapshotRuntimeService`
  удерживается около `83`, а remaining notifier/runtime risk смещён уже в
  набор очень компактных bounded services без какого-либо giant-wrapper
  признака.
- следующим ещё более широким пакетом этот хвост дополнительно сузился:
  появились `SlaRoutingPolicySnapshotSettingsService`,
  `SlaRoutingPolicySnapshotContextService`,
  `SlaRoutingRuleDefinitionMatchService`,
  `SlaRoutingRuleWinnerSelectionService` и
  `SlaRoutingRuleEvaluationContextService`.
- после этого `SlaRoutingPolicySnapshotRuntimeService` удерживается на уровне
  примерно `86` строк, `SlaRoutingRuleParserService` — около `67`,
  `SlaRoutingRuleUsageAnalysisService` — около `88`, а
  `SlaRoutingRuleAuditEvaluationService` — около `71`; remaining
  notifier/runtime risk теперь уже не в parser/evaluation wrappers, а в
  очень компактном наборе `snapshot runtime/settings/context` и
  `usage-analysis/evaluation-context` bounded services, если они снова начнут
  расти.
- параллельно по новому приоритету аудита дополнительно сузился и
  `DialogWorkspaceService`: history pagination вынесен в
  `DialogWorkspaceHistorySliceService`, SLA/runtime envelope — в
  `DialogWorkspaceSlaViewService`, а client/context assembly — в
  `DialogWorkspaceClientContextAssemblerService`.
- после этого `DialogWorkspaceService` сжат примерно до `164` строк и уже
  работает как orchestration façade над history/context/SLA bounded
  services; remaining workspace-risk смещён уже не в giant method, а в
  `DialogWorkspaceClientContextAssemblerService` (~`211` строк) и соседние
  context-centric bounded services, если они снова начнут разрастаться.
- следующим широким пакетом по новому `P1` приоритету аудита дополнительно
  сужен `DialogAiAssistantService`: review workflow вынесен в
  `DialogAiAssistantReviewService`, solution-memory lifecycle — в
  `DialogAiSolutionMemoryService`, а shared persistence/support helpers — в
  `DialogAiAssistantPersistenceService`.
- после этого `DialogAiAssistantService` сжат примерно с `1932` до `1256`
  строк; главный remaining tail там уже не в review/memory slices, а в
  message-processing/control и смежном AI orchestration path.
- под новый split добавлены `DialogAiAssistantReviewServiceTest` и
  `DialogAiSolutionMemoryServiceTest`; targeted AI assistant regression net,
  `DialogAiOpsControllerWebMvcTest` и compile-проверка остаются зелёными.
- следующим широким пакетом этот AI assistant slice дополнительно сужен:
  появились `DialogAiAssistantStateService`,
  `DialogAiAssistantConfigService` и
  `DialogAiAssistantOperatorFeedbackService`.
- после этого `DialogAiAssistantService` сжат примерно с `1256` до `882`
  строк; из coordinator убраны dialog control/state updates, processing
  flags, auto-reply guard/config parsing и operator feedback/correction
  lifecycle.
- под новый split добавлены `DialogAiAssistantStateServiceTest`,
  `DialogAiAssistantConfigServiceTest` и
  `DialogAiAssistantOperatorFeedbackServiceTest`; compile, targeted AI
  assistant tests и `DialogAiOpsControllerWebMvcTest` остаются зелёными.
- следующим широким пакетом этот же AI assistant slice дополнительно
  разрезан по remaining orchestration tail: появились
  `DialogAiAssistantPolicyService`,
  `DialogAiAssistantSuggestionService`,
  `DialogAiAssistantEventService` и
  общий `DialogAiAssistantSuggestionCandidate`.
- после этого `DialogAiAssistantService` сжат примерно с `882` до `482`
  строк; из coordinator убраны pre-routing policy evaluation, suggestion
  composition, retrieval payload/event logging и значительная часть
  message-processing helper-логики.
- под новый split добавлены `DialogAiAssistantPolicyServiceTest`,
  `DialogAiAssistantSuggestionServiceTest` и
  `DialogAiAssistantEventServiceTest`; compile, targeted AI assistant
  regression net и `DialogAiOpsControllerWebMvcTest` остаются зелёными.
- следующим широким пакетом remaining AI assistant orchestration tail
  разрезан ещё на transport/flow слой: появились
  `DialogAiAssistantEscalationService` и
  `DialogAiAssistantMessageFlowService`.
- после этого `DialogAiAssistantService` сжат примерно с `482` до `208`
  строк и уже работает как facade над bounded AI assistant slices; основной
  remaining flow-tail локализован в `DialogAiAssistantMessageFlowService`
  (~`369` строк), а не в самом facade.
- под новый split добавлены `DialogAiAssistantEscalationServiceTest` и
  `DialogAiAssistantMessageFlowServiceTest`; compile, targeted AI assistant
  regression net и `DialogAiOpsControllerWebMvcTest` остаются зелёными.
- следующим широким пакетом и этот remaining AI tail дополнительно досужен:
  появился `DialogAiAssistantMessageOutcomeService`, который забрал
  decision outcome, consistency block и auto-reply/send lifecycle orchestration
  из `DialogAiAssistantMessageFlowService`.
- после этого `DialogAiAssistantMessageFlowService` сжат примерно с `369`
  до `267` строк, а новый bounded `DialogAiAssistantMessageOutcomeService`
  удерживается примерно на `337` строках; remaining AI risk теперь уже не
  в одном message-flow coordinator, а в локальной flow/outcome паре без
  giant-facade симптомов.
- под новый split добавлен `DialogAiAssistantMessageOutcomeServiceTest`;
  compile, targeted AI assistant regression net и
  `DialogAiOpsControllerWebMvcTest` остаются зелёными.
- следующим широким пакетом уже по следующему P1-candidate начат bounded
  split `PublicFormService`: выделены `PublicFormRuntimeConfigService`,
  `PublicFormMetricsService` и `PublicFormSessionService`.
- после этого `PublicFormService` сжат примерно с `1327` до `1020` строк;
  из coordinator убраны runtime dialog config readers, in-memory metrics
  slice и session lookup/token rotation helper-блок.
- под новый split добавлены `PublicFormRuntimeConfigServiceTest`,
  `PublicFormMetricsServiceTest` и `PublicFormSessionServiceTest`; compile,
  `PublicFormApiControllerWebMvcTest`, `PublicFormControllerWebMvcTest` и
  targeted AI assistant regression net остаются зелёными.
- следующим пакетом из `PublicFormService` вынесен anti-abuse/request identity
  bounded slice: новый `PublicFormAntiAbuseService` теперь владеет
  requester fingerprint key, requestId normalization, payload hash,
  idempotency cache и rate-limit policy.
- после этого `PublicFormService` сжат примерно с `1020` до `889` строк;
  из coordinator убраны mixed idempotency/rate-limit/requester-key helper’ы,
  а remaining practical focus смещён уже в submit/captcha/validation
  orchestration и соседний config-parser tail.
- под новый split добавлен `PublicFormAntiAbuseServiceTest`; targeted
  `PublicFormApiControllerWebMvcTest`, `PublicFormControllerWebMvcTest`,
  `PublicFormLocationIntegrationTest` и новый anti-abuse unit net остаются
  зелёными.
- следующим пакетом из `PublicFormService` вынесен submit/captcha/validation
  bounded slice: новый `PublicFormSubmissionPolicyService` теперь владеет
  payload sanitization, form summary assembly, captcha enforcement,
  field/type/location validation и client-name resolution.
- после этого `PublicFormService` сжат примерно с `889` до `534` строк;
  из coordinator убраны submit helper-блоки, а remaining practical focus
  смещён уже в config parser, location preset enrichment и
  ticket/session projection orchestration.
- под новый split добавлен `PublicFormSubmissionPolicyServiceTest`;
  targeted `PublicFormApiControllerWebMvcTest`,
  `PublicFormControllerWebMvcTest`, `PublicFormLocationIntegrationTest`,
  `PublicFormAntiAbuseServiceTest` и новый submission-policy unit net
  остаются зелёными.
- следующим пакетом из `PublicFormService` вынесен form-definition/config
  bounded slice: новый `PublicFormDefinitionService` теперь владеет demo
  config assembly, `questions_cfg` parsing, schema/disabled-status
  normalization, location preset enrichment и question ordering.
- по пути устранён parser bug для textual `JsonNode`: `successInstruction`
  теперь нормализуется без лишних JSON-кавычек.
- после этого `PublicFormService` сжат примерно с `534` до `343` строк;
  из coordinator убраны config parser и location assembly helper-блоки, а
  remaining practical focus смещён уже в ticket/session projection
  orchestration и continuation/runtime helper tail.
- под новый split добавлен `PublicFormDefinitionServiceTest`; targeted
  `PublicFormApiControllerWebMvcTest`, `PublicFormControllerWebMvcTest`,
  `PublicFormLocationIntegrationTest`, `PublicFormFlowSmokeIntegrationTest`,
  `PublicFormAntiAbuseServiceTest`, `PublicFormSubmissionPolicyServiceTest`
  и новый definition-service unit net остаются зелёными.
- следующим пакетом из `PublicFormService` вынесен persistence/projection
  bounded slice: новый `PublicFormSubmissionPersistenceService` теперь
  владеет session creation, ticket/message/chat-history projection, dialog
  audit и new-appeal alert side-effects.
- после этого `PublicFormService` сжат примерно с `343` до `198` строк;
  из coordinator убраны ticket/session persistence helper-блоки, а
  remaining practical focus смещён уже в continuation/runtime helper tail
  и thin entry-flow coordinator logic.
- под новый split добавлен `PublicFormSubmissionPersistenceServiceTest`;
  targeted `PublicFormApiControllerWebMvcTest`,
  `PublicFormControllerWebMvcTest`, `PublicFormLocationIntegrationTest`,
  `PublicFormFlowSmokeIntegrationTest`, `PublicFormAntiAbuseServiceTest`,
  `PublicFormSubmissionPolicyServiceTest`, `PublicFormDefinitionServiceTest`
  и новый persistence-service unit net остаются зелёными.
- следующим пакетом из `PublicFormService` вынесен channel/config/session
  lookup bounded slice: новый `PublicFormChannelService` теперь владеет
  `loadConfig`, `loadConfigRaw`, `findSession`, channel resolution и
  continuation-option assembly.
- после этого `PublicFormService` сжат примерно с `198` до `115` строк;
  из coordinator убраны continuation/runtime helper-блоки, а remaining
  practical focus смещён уже в thin `createSession` entry-flow coordinator
  и final runtime contract hardening.
- под новый split добавлен `PublicFormChannelServiceTest`; targeted
  `PublicFormApiControllerWebMvcTest`,
  `PublicFormControllerWebMvcTest`, `PublicFormLocationIntegrationTest`,
  `PublicFormFlowSmokeIntegrationTest`, `PublicFormAntiAbuseServiceTest`,
  `PublicFormSubmissionPolicyServiceTest`, `PublicFormDefinitionServiceTest`,
  `PublicFormSubmissionPersistenceServiceTest` и новый channel-service unit
  net остаются зелёными.
- следующим пакетом из `PublicFormService` вынесен submit entry-flow
  coordinator bounded slice: новый `PublicFormSubmissionFlowService`
  теперь владеет channel/config gating, submission preparation, anti-abuse
  idempotency/rate-limit orchestration и persistence handoff.
- после этого `PublicFormService` сжат примерно с `115` до `81` строки;
  giant public-form split можно считать практически закрытым, а remaining
  practical focus смещён уже в runtime contract, integration-quality и API
  consistency.
- под новый split добавлен `PublicFormSubmissionFlowServiceTest`; targeted
  `PublicFormApiControllerWebMvcTest`,
  `PublicFormControllerWebMvcTest`, `PublicFormLocationIntegrationTest`,
  `PublicFormFlowSmokeIntegrationTest`, `PublicFormAntiAbuseServiceTest`,
  `PublicFormSubmissionPolicyServiceTest`, `PublicFormDefinitionServiceTest`,
  `PublicFormSubmissionPersistenceServiceTest`,
  `PublicFormChannelServiceTest` и новый flow-service unit net остаются
  зелёными.
  и новый persistence-service unit net остаются зелёными.
