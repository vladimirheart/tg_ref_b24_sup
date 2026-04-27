# Architecture And UI Refactoring Roadmap

Дата старта: `2026-04-15`
Обновлено: `2026-04-23`

## Цель

Снизить архитектурный риск в `spring-panel` и привести UI runtime к управляемой
модели, где:

- тема и page presets имеют единый источник подключения;
- страницы не дублируют bootstrap-логику;
- крупные домены `dialogs` и `settings` режутся по bounded context, а не
  продолжают расти как `god-service` и `god-controller`.

## Текущее состояние

На `2026-04-20` базовый план уже частично реализован в коде, поэтому roadmap
нужно читать не как wishlist, а как карту оставшихся работ.

Что уже достигнуто:

- UI runtime foundation и ранний head-bootstrap внедрены;
- browser/server-backed UI preferences заведены и частично нормализованы;
- controller/service split для домена `dialogs` выполнен на уровне transport и
  основных orchestration flows;
- `settings` выведен из режима giant controller/update-method и разрезан по
  нескольким поддоменам;
- `dialog_config` больше не обновляется через один giant service;
- runtime boundary для ботов уже начал уходить от жёсткого `spring-boot:run`;
- появилась минимальная test safety net для наиболее рискованных новых слоёв.

Что остаётся главными hotspots:

- `DialogService` всё ещё слишком крупный и остаётся главным кандидатом на
  service-level split по bounded context;
- в `settings` ещё не выделены оставшиеся крупные поддомены уровня
  `catalog/reference/bot-settings/partner-network`;
- Phase 5 начат, но ещё не доведён до полноценного runtime contract между
  `spring-panel` и `java-bot`;
- Phase 6 пока даёт только точечную страховку, а не широкий regression net;
- крупные inline `style/script` блоки в `settings`, `dashboard`, `dialogs`
  остаются техническим долгом UI-слоя.

## Фазы

### Phase 1. UI Runtime Foundation

Цель:

- централизовать раннюю загрузку темы;
- расширить `ui-config` на остальные разделы;
- убрать дублирование head-bootstrap логики по страницам.

Статус:

- завершено.

Что включено:

- общий fragment `fragments/ui-head.html`;
- ранняя загрузка `theme.js` и `ui-config.js` в `<head>`;
- removal позднего подключения `theme.js` из sidebar-fragment;
- расширение `ui-config` на `analytics`, `clients`, `knowledge`, `channels`,
  `users`, `tasks`, `passports`, `public`, `ai-ops`;
- базовые page-width presets в `style.css`.

### Phase 2. UI State Governance

Цель:

- разделить browser-only preferences и серверные настройки;
- перестать смешивать persisted visual settings из JSON и локальные operator
  preferences в одном потоке.

Статус:

- выполнено частично и стабилизировано как foundation-слой.

Что уже сделано:

- введён общий runtime-модуль для browser-only preferences;
- на него переведены `theme` и `sidebar`;
- добавлен server-backed bootstrap/sync слой для operator UI preferences;
- triage preferences диалогов переведены на server-backed storage с
  backward-compatible fallback к legacy shared JSON;
- ownership источников UI preferences зафиксирован в
  `docs/UI_PREFERENCES_OWNERSHIP.md`.

Что остаётся:

- дочистить места, где runtime всё ещё может зависеть от raw visual values,
  а не от tokens/theme semantics;
- при необходимости выделить отдельный documented contract для operator UI
  preferences.

### Phase 3. Dialog Domain Split

Цель:

- разрезать `DialogService` и `DialogApiController` по сценариям.

Предлагаемые срезы:

- `DialogListService` / `DialogListController`;
- `DialogWorkspaceService`;
- `DialogHistoryService`;
- `DialogSlaService`;
- `DialogAiAssistantFacade`;
- `DialogNotificationFacade`.

Принцип:

- сначала выделять read-only slices и DTO mapping;
- затем переносить write-сценарии.

Статус:

- начато: triage preference сценарий вынесен из `DialogApiController` в
  отдельный `DialogTriagePreferenceService`, что даёт первый read/write slice
  без изменения внешнего API для фронта.
- начато: list endpoint `/api/dialogs` вынесен в отдельные
  `DialogListController` и `DialogListReadService`, а SLA orchestration списка
  больше не собирается внутри монолитного controller.
- продолжено: read-only endpoints `details/history/public-form-metrics`
  вынесены в `DialogReadController` и `DialogReadService`.
- продолжено: endpoint-слой triage preferences вынесен в
  `DialogTriagePreferencesController`.
- продолжено: quick actions (`reply/edit/delete/media/resolve/reopen/categories/take/snooze`)
  вынесены в `DialogQuickActionsController` и `DialogQuickActionService`.
- продолжено: macro API (`/macro/dry-run`, `/macro/variables`) вынесен в
  `DialogMacroController` и `DialogMacroService`.
- продолжено: workspace telemetry API и summary orchestration вынесены в
  `DialogWorkspaceTelemetryController` и `DialogWorkspaceTelemetryService`.
- продолжено: AI ops API (`ai-suggestions`, `ai-control`, `ai-review`,
  `ai-solution-memory`, `ai-monitoring`) вынесен в
  `DialogAiOpsController` и `DialogAiOpsService`.
- поддерживающий слой: dialog permission checks и audit logging вынесены в
  общий `DialogAuthorizationService`, чтобы новые dialog controllers не
  дублировали права и forbidden-audit.
- продолжено: `DialogQuickActionsController` переведён на общий
  `DialogAuthorizationService`, а локальные permission helper'ы удалены.
- завершено на controller/service уровне: оставшийся workspace flow вынесен в
  `DialogWorkspaceController` и `DialogWorkspaceService`, а сам старый
  `DialogApiController` больше не нужен как точка концентрации домена.

Что это значит practically:

- transport-layer split домена `dialogs` в основном завершён;
- следующий значимый этап для `dialogs` уже не про controllers, а про
  разрезание самого `DialogService` на service-level bounded contexts.
- начато service-level сужение giant `DialogWorkspaceService`: из него вынесены
  `DialogWorkspaceExternalProfileService` и `DialogWorkspaceParityService`,
  а legacy macro/request хвост после controller split удалён.
- следующий пакет service-level split внутри `workspace` тоже уже сделан:
  navigation/queue meta и rollout/meta-config вынесены в
  `DialogWorkspaceNavigationService` и `DialogWorkspaceRolloutService`, плюс
  добавлены targeted tests для этих sub-services.
- ещё один пакет service-level split внутри `workspace` закрыт:
  client segments/profile health вынесены в
  `DialogWorkspaceClientProfileService`, а context blocks/blocks health — в
  `DialogWorkspaceContextBlockService`, тоже с targeted unit tests.
- ещё один пакет service-level split внутри `workspace` закрыт:
  client payload support вынесен в `DialogWorkspaceClientPayloadService`, а
  context sources/attribute policies — в `DialogWorkspaceContextSourceService`,
  тоже с targeted unit tests.
- ещё один пакет service-level split внутри `workspace` закрыт:
  context contract вынесен в `DialogWorkspaceContextContractService`, а
  мёртвый дублирующий review-control block удалён из `DialogWorkspaceService`,
  чтобы giant service держал только реальные workspace use-cases.
- дополнительно убрано runtime-дублирование по SLA: общий
  `DialogSlaRuntimeService` теперь обслуживает и `DialogWorkspaceService`, и
  `DialogListReadService`, а из workspace-сервиса удалены оставшиеся мёртвые
  helper-блоки по SLA/source-coverage/export formatting.
- следующим service-level пакетом из giant `DialogService` вынесен client
  context read-layer: `loadClientDialogHistory`, `loadClientProfileEnrichment`,
  `loadDialogProfileMatchCandidates` и `loadRelatedEvents` теперь живут в
  `DialogClientContextReadService`; `DialogWorkspaceService` и
  `DialogMacroService` уже переведены на новый слой напрямую, а в
  `DialogService` оставлены thin compatibility delegates для legacy
  controller/integration tests.
- следующим связанным пакетом вынесен conversation read-layer:
  `loadHistory`, `loadPreviousDialogHistory` и `loadTicketCategories` теперь
  живут в `DialogConversationReadService`; `DialogReadService`,
  `DialogWorkspaceService` и `PublicFormApiController` переведены на новый
  слой, а `DialogService` использует его для `loadDialogDetails` и legacy
  delegates.
- следующим boundary-пакетом вокруг telemetry/notifier слоя добавлены
  `DialogWorkspaceTelemetrySummaryService` и
  `DialogMacroGovernanceAuditService`: `DialogWorkspaceTelemetryService` и
  `WorkspaceGuardrailWebhookNotifier` больше не завязаны напрямую на
  `DialogService`, а telemetry/macro governance получили отдельную
  dependency-boundary для следующего полноценного выноса логики.
- следующим пакетом снята ещё и техническая nested/static coupling вокруг
  giant service: `DialogDataAccessSupport` вынесен из `DialogService`, а
  `DialogResolveResult` больше не живёт как nested record giant service;
  это позволило перевести lifecycle/read-side use-sites на отдельные доменные
  опоры без прямой привязки к nested API `DialogService`.
- следующим пакетом boundary-слой вокруг telemetry/macro governance получил
  уже реальные data/support зависимости: raw workspace telemetry
  read-model/JDBC/aggregation вынесены в `DialogWorkspaceTelemetryDataService`,
  а helper-слой macro governance/usage/variables — в
  `DialogMacroGovernanceSupportService`; сам `DialogService` переключён на
  эти сервисы и потерял ещё один крупный блок telemetry/macro helper-логики.
- следующим пакетом `DialogMacroGovernanceAuditService` перестал зависеть от
  `DialogService` и стал самостоятельным owner’ом macro governance audit
  orchestration;
- следующим пакетом снят и последний прямой consumer-boundary хвост giant
  service в основном приложении: `DialogWorkspaceTelemetrySummaryService`
  переведён на отдельный `DialogWorkspaceTelemetrySummaryBridgeService`, а
  remaining зависимость на `DialogService` честно локализована в compatibility
  bridge вместо прямой domain/service связи.

Что остаётся:

- service-level split `DialogService` на list/workspace/history/SLA/AI и related
  mapping layers;
- продолжить вытаскивать из `DialogService` remaining read/write bounded
  contexts уже поверх вынесенного `DialogClientContextReadService`;
- довести до конца orchestration split вокруг workspace telemetry summary,
  чтобы после уже завершённого macro-governance audit split убрать временный
  `DialogWorkspaceTelemetrySummaryBridgeService` и схлопнуть remaining
  compatibility delegates giant service;
- продолжить снимать remaining consumer-facades вокруг notifier / telemetry /
  escalation слоёв там, где giant service ещё остаётся техническим посредником;
- продолжить service-level split уже поверх вынесенных `DialogClientContextReadService`
  и `DialogConversationReadService`, чтобы следующий пакет брал либо
  lookup/list-assembly, details-read слой, либо write-side bounded contexts;
- при необходимости вынести DTO mapping и summary assembly из giant service;
- расширить targeted WebMvc/service tests под новую controller/service
  раскладку.

### Phase 4. Settings Domain Split

Цель:

- убрать монолитный `SettingsBridgeController`.

Предлагаемые срезы:

- `SettingsCatalogController`;
- `SettingsParametersController`;
- `DialogSettingsController`;
- `BotSettingsController`;
- `ReferenceDataSettingsController`;
- `PartnerNetworkSettingsController`.

Статус:

- начато: parameters API `/api/settings/parameters` и связанная логика
  location/parameter sync вынесены в `SettingsParametersController` и
  `SettingsParameterService`.
- продолжено: `it-equipment` API вынесен в `SettingsItEquipmentController` и
  `SettingsItEquipmentService`.
- продолжено: macro template governance и normalization для dialog settings
  вынесены из `SettingsBridgeController` в `SettingsMacroTemplateService`.
- продолжено: основной update-use-case `/settings` вынесен из
  `SettingsBridgeController` в `SettingsUpdateService`, а bridge-controller
  оставлен thin-wrapper'ом над service-слоем.
- продолжено: базовые top-level settings вынесены из `SettingsUpdateService`
  в `SettingsTopLevelUpdateService`.
- продолжено: обработка `locations` и связанный parameter sync вынесены из
  `SettingsUpdateService` в `SettingsLocationsUpdateService`.
- продолжено: giant `dialog_config` update flow вынесен из
  `SettingsUpdateService` в `SettingsDialogConfigUpdateService`, а сам
  `SettingsUpdateService` сокращён до orchestration-слоя.
- продолжено: общие timestamp/datamart validation helper'ы для `dialog_config`
  вынесены в `SettingsDialogConfigSupportService`, чтобы уменьшить внутреннюю
  перегрузку `SettingsDialogConfigUpdateService`.
- продолжено: `SettingsDialogConfigUpdateService` разрезан на поддомены
  `SettingsDialogSlaAiConfigService`, `SettingsDialogWorkspaceConfigService`,
  `SettingsDialogPublicFormConfigService` и coordinator/router слой без giant
  списка `payload.containsKey(...)`.
- завершено для текущего этапа: coordinator `dialog_config` дополнительно
  разгружен через `SettingsDialogTemplateConfigService` и
  `SettingsDialogRuntimeConfigService`, так что template/macro governance и
  базовые runtime-настройки больше не живут в одном giant update-method.
- добавлена минимальная test safety net: routing/validation для нового
  `dialog_config` split покрыты unit-тестами, а legacy
  `DialogApiControllerWebMvcTest` синхронизирован с новой controller-разбивкой,
  чтобы не ломать `testCompile`.

Что это значит practically:

- основные самые рискованные giant flows в `settings` уже разрезаны;
- `SettingsBridgeController` и `SettingsUpdateService` больше не являются
  единственными точками концентрации домена.

Что остаётся:

- выделить remaining subdomains уровня `catalog/reference data`,
  `partner/network`, `bot/integration settings`, если они всё ещё живут в
  слишком общих слоях;
- при необходимости сузить remaining responsibilities
  `SettingsDialogWorkspaceConfigService`, если он снова начнёт разрастаться;
- расширить тестовую страховку вокруг settings update/routing контрактов.

### Phase 5. Process And Runtime Boundary

Цель:

- уменьшить coupling между `spring-panel` и runtime ботов.

Что сделать:

- уйти от `spring-boot:run` в `BotProcessService`;
- запускать собранные артефакты или отдельный launcher;
- формализовать contract между panel и `java-bot`;
- отдельно покрыть readiness/status contract тестами.

Статус:

- начато: `BotProcessService` переведён на launcher strategy с
  `app.bots.launch-mode` и `jar-first` запуском готовых артефактов;
- `spring-boot:run` оставлен как controlled fallback для dev-сценария;
- продолжено: добавлен explicit artifact discovery contract через
  `app.bots.executable-jars`, чтобы panel могла запускать заранее известные
  bot jars без угадывания по `target/*.jar`;
- продолжено: launcher/env/readiness expectations вынесены в
  `BotRuntimeContractService` и задокументированы в `docs/BOT_RUNTIME_CONTRACT.md`;
- продолжено: добавлен диагностический endpoint
  `/api/bots/{channelId}/runtime-contract`, чтобы contract можно было проверить
  без реального старта процесса;
- продолжено: зафиксирован production deployment recipe через
  `preferred-production-launcher`, `recommended-artifact-directory` и
  explicit `jar` contract;
- продолжено: добавлен lifecycle contract test с реальным runnable test jar,
  который проверяет `start -> readiness -> status -> stop`;
- добавлены тесты на выбор launcher plan, runtime contract payload и сохранён
  readiness probe контракт.

Что остаётся:

- формализовать contract между panel и `java-bot` на уровне launcher inputs,
  env contract и status/readiness expectations;
- распространить explicit jar contract на целевые окружения и зафиксировать
  рекомендуемые пути/имена артефактов для production/deploy сценариев;
- при необходимости определить следующий production boundary после prebuilt jars:
  launcher scripts или отдельный supervisor/service.

### Phase 6. Test Safety Net

Цель:

- получить страховку на рефакторинг.

Минимум:

- smoke tests для `theme/ui bootstrap`;
- webmvc tests для основных settings/dialogs endpoints;
- integration tests для shared config и DB path resolution;
- runtime contract tests для bot process orchestration.

Статус:

- начато точечно, но не завершено как системный слой.

Что уже есть:

- unit-тесты на `dialog_config` routing/validation;
- test coverage на readiness/startup contract в `BotProcessService`;
- unit-тесты на `BotRuntimeContractService`;
- WebMvc test на `/api/bots/{channelId}/runtime-contract`;
- lifecycle contract test с runnable test jar для `BotProcessService`;
- smoke-проверка раннего `ui-head` bootstrap на странице диалогов;
- page/runtime smoke tests для `dashboard`, `analytics`, `clients`,
  `knowledge` и `settings`, которые проверяют ранний bootstrap
  `ui-preferences/theme/ui-config` и наличие explicit `data-ui-page`;
- WebMvc tests для sliced controllers `DialogReadController`,
  `DialogTriagePreferencesController`, `SettingsParametersController`,
  `SettingsItEquipmentController`, `DialogMacroController`,
  `DialogWorkspaceController`, `DialogQuickActionsController`,
  `DialogAiOpsController`, `DialogWorkspaceTelemetryController`,
  `DialogListController`;
- targeted tests для shared config/runtime foundation:
  `SharedConfigServiceTest`, `EnvDefaultsInitializerTest`;
- shared config boundary больше не прикрыт только foundation-уровнем:
  теперь есть round-trip test для `locations.json` и integration-сценарии
  `SettingsParameterSharedConfigIntegrationTest` и
  `SettingsUpdateSharedConfigIntegrationTest`, которые проверяют реальный
  sync `settings_parameters <-> locations.json` и persistence-flow
  `SettingsUpdateService`;
- этот же shared config/runtime слой расширен и на соседние контракты:
  `SharedConfigServiceTest` теперь покрывает round-trip для
  `org_structure.json` и `bot_credentials.json`,
  `AuthManagementApiControllerWebMvcTest` прикрывает save-контракт
  `org_structure`, `ChannelApiControllerWebMvcTest` — list/create/delete
  контракты `bot_credentials`, а `BotAutoStartServiceTest` — runtime
  поведение автозапуска при активных/неактивных credentials;
- следующим пакетом этот boundary расширен уже на orchestration/API/runtime:
  `AuthManagementApiControllerWebMvcTest` теперь покрывает base payload
  `/api/auth/state`, `AuthManagementApiController#getAuthState` сделан
  null-safe для nullable `current_user_id/org_structure`,
  `BotProcessApiControllerWebMvcTest` теперь прикрывает `start/stop/status`
  вместе с `runtime-contract`, `BotRuntimeContractServiceTest` добран
  `vk`-contract сценариями, а `BotAutoStartServiceTest` — ветками
  `inactive/already-running/missing-credential`;
- следующим пакетом shared-config/runtime boundary расширен ещё глубже:
  `ChannelApiController#createBotCredential` теперь нормализует пустой
  `platform` в `telegram`, `ChannelApiControllerWebMvcTest` прикрывает
  embedded credential summary в `/api/channels`, validation/normalization
  для `bot_credentials` и `404` при удалении отсутствующего credential;
  `BotProcessApiControllerWebMvcTest` добран stopped-state веткой,
  `BotRuntimeContractServiceTest` — `max`-contract сценарием и optional
  `vk` env keys, а `BotAutoStartServiceTest` —
  веткой `channel without credential binding`;
- следующим пакетом `Phase 6` расширен уже на полноценный
  `ChannelApiController` orchestration contract:
  `createChannel` прикрыт нормализацией пустого `platform` в `telegram`
  и генерацией default `public_id/questions_cfg/delivery_settings`,
  `post/put` update-ветки — sync `credential_id/support_chat_id`,
  `network_route/platform_config`, alias-обновлением и invalid
  `questions_cfg` contract, а `deleteChannel` — success/404 сценариями;
- следующим пакетом тот же orchestration boundary расширен уже на
  runtime-операции каналов:
  `test-message` прикрыт `404`, non-telegram guard, missing recipient
  guard и successful send в `group/channel` с deduplication,
  а `refresh bot info` — `404`, non-telegram guard, failure-path через
  `Telegram getMe` и successful persistence `bot_name/bot_username`;
- следующим пакетом `panel-bot orchestration boundary` расширен ещё глубже:
  `BotProcessApiController` теперь имеет явный success/error contract для
  `start/stop/status` и прикрыт failure-ветками;
  `runtime-contract` добран `max` payload-сценарием;
  `BotAutoStartServiceTest` добран ветками `null channel id` и
  `continue after failed start`;
  `ChannelApiControllerWebMvcTest` добран ветками
  `test-message all failed` и `manual recipient only`;
- следующим крупным пакетом `Phase 6` расширен сразу по трём соседним
  boundary-слоям `auth/runtime/network`:
  `AuthManagementApiControllerWebMvcTest` теперь прикрывает users/roles CRUD
  edge-cases и success-сценарии (`duplicate`, `empty update`, `not found`,
  `role in use`, `successful delete/update`);
  `IntegrationNetworkServiceTest` добран direct/profile failover context,
  incomplete proxy profile и direct env contract;
  `BotRuntimeContractServiceTest` добран ветками `vpn/default telegram`,
  `minimal vk`, `unknown platform`, `target-scan warning` и
  `jar-mode missing artifact`;
- в этом же пакете исправлен реальный runtime defect:
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
- targeted unit tests для вынесенных `settings` subdomain services:
  `SettingsDialogRuntimeConfigServiceTest`,
  `SettingsDialogPublicFormConfigServiceTest`,
  `SettingsDialogSlaAiConfigServiceTest`,
  `SettingsDialogTemplateConfigServiceTest`,
  `SettingsDialogWorkspaceConfigServiceTest`;
- targeted unit tests для `settings` governance/sync слоя:
  `SettingsTopLevelUpdateServiceTest`,
  `SettingsLocationsUpdateServiceTest`,
  `SettingsParameterServiceTest`,
  `UiPreferenceServiceTest`;
- targeted unit tests для новых dialog support/data слоёв:
  `DialogWorkspaceTelemetryDataServiceTest` и
  `DialogMacroGovernanceSupportServiceTest`;
- server-backed UI preferences больше не только “есть”, но и прикрыты
  regression net: alias-нормализация `sortMode/pageSize/updatedAtUtc`
  в `UiPreferenceService` исправлена и зафиксирована тестами;
- merge-поведение server-backed UI preferences тоже прикрыто отдельно:
  обновление базовых prefs подтверждено тестом не затирает nested
  `dialogsTriage` payload;
- orchestration/API слой `settings` тоже получил прямую страховку:
  `SettingsUpdateServiceTest`,
  `SettingsDialogConfigUpdateServiceTest`,
  `SettingsBridgeControllerWebMvcTest`,
  `ProfileApiControllerWebMvcTest`;
- `SlaEscalationWebhookNotifier` больше не зависит от `DialogService`
  вообще: notifier переведён на `DialogLookupReadService`,
  `DialogResponsibilityService` и `DialogAuditService`, а legacy unit tests
  синхронизированы под прямые зависимости без fallback-поля на giant service;
- legacy notifier safety net больше не красный: route naming и strict
  review-path expectations в `SlaEscalationWebhookNotifierTest`
  синхронизированы с текущим notifier contract и полный notifier test suite
  снова проходит;
- page/runtime smoke tests расширены beyond базового пакета:
  теперь explicit `data-ui-page` и ранний `ui-head` bootstrap проверяются ещё
  для `channels`, `tasks`, `users`, `object-passports`, `public forms` и
  аналитических subpages `certificates/rms-control`.
- `Phase 6` дополнительно расширен на `auth/profile/runtime controller`
  boundary: `ProfileApiControllerWebMvcTest` покрывает unauthorized contract
  `ui-preferences` и основной password-flow; `AuthManagementApiControllerWebMvcTest`
  прикрывает `/api/users/{id}/password` и `photo-upload`; `BotProcessApiController`
  сделан null-safe для `start/stop/status`, а WebMvc tests фиксируют `unknown`
  fallback при null-ответе runtime service.
- следующим расширенным пакетом `Phase 6` добран глубже по
  `auth/profile/channel-management` boundary:
  `ProfileApiControllerWebMvcTest` теперь прикрывает `password_hash` branch;
  `AuthManagementApiControllerWebMvcTest` — create-user persistence для
  `password_hash/enabled/registration_date` и denied-ветки
  `role.name/role.description`; `ChannelApiControllerWebMvcTest` —
  empty list, failed Telegram bot-info refresh tolerance, blank credential
  platform normalisation, default `is_active` и safe delete credential без
  лишнего `saveAll()`.
- следующим пакетом `Phase 6` расширен уже по комбинированному
  `channel-management/auth-management/shared-config` boundary:
  `ChannelApiControllerWebMvcTest` теперь покрывает failed `saveAll()` после
  Telegram bot-info refresh, reject-сценарий VK platform switch без
  callback configuration, пустой результат Telegram `getMe`, sparse/null
  allocation нового credential id и cleanup нескольких связанных каналов
  при delete credential;
  `AuthManagementApiControllerWebMvcTest` — raw payload contract
  `/api/auth/org-structure`, create/update/delete persistence для optional
  `phones/role_id/role` и reject-ветку blank role name;
  `SharedConfigServiceTest` — nested settings round-trip и empty
  `bot_credentials` round-trip.
- следующим расширенным пакетом `Phase 6` добран по
  `dialogs/settings controller edge-case` boundary:
  `DialogQuickActionsControllerWebMvcTest` теперь покрывает domain error
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
- следующим ещё более широким пакетом `Phase 6` добран по
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
- следующим ещё более глубоким пакетом `Phase 6` добран именно по
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
- этот же smoke-слой расширен на detail/subpage contract:
  `ai-ops`, `unblock requests`, `users/detail` и оба passport editor route
  (`/object-passports/new`, `/object-passports/{id}`) теперь тоже прикрыты
  WebMvc smoke tests на ранний bootstrap и explicit `data-ui-page`.
- следующим пакетом добран public-shell bootstrap contract:
  `login`, `403`, `404` и `500` получили explicit `data-ui-page="public"`,
  `403/500` приведены к общему `fragments/ui-head`, а для public shell
  добавлен lightweight template-contract test вместо тяжёлого runtime WebMvc
  сценария.
- legacy WebMvc test-слой частично синхронизирован с новой controller
  структурой.

Что остаётся:

- продолжить расширять smoke tests для `theme/ui bootstrap` на remaining
  страницы beyond `dashboard/analytics/clients/knowledge/settings/dialogs`
  и уже покрытых `channels/tasks/users/passports/public`, `ai-ops`,
  `unblock-requests`, `users/detail`, `passport editor` и public shell
  `login/403/404/500`;
- добрать WebMvc tests под оставшиеся новые dialog/settings controllers,
  уже после закрытия основного alias/default/error слоя для quick-actions,
  read, macro, triage, telemetry, parameters и it-equipment;
- продолжить тем же подходом расширять controller edge-case net уже beyond
  `dialogs/settings` на `ai-ops/public forms/settings bridge/bot process`,
  чтобы отлавливать alias/validation/fallback contracts до следующего
  крупного рефакторинга;
- расширить shared config/env resolution tests и settings regression net от
  targeted-слоя к более полным integration сценариям;
- расширить `settings` regression net дальше от уже появившихся
  integration-сценариев к нескольким более широким update/sync цепочкам
  поверх shared config boundary;
- продолжить расширять shared config/runtime boundary уже после
  `locations/org_structure/bot_credentials` и базового
  `auth/bot-process` orchestration-контракта на более широкий panel-bot
  contract и cross-module unification;
- отдельно удерживать `autostart` и `integration network` runtime boundary
  под regression net, потому что теперь там уже есть явная null-safe
  обработка `status/start` и более широкий proxy/vpn/vless contract;
- удерживать `bot process api` и `auth management` orchestration boundary
  под тем же regression net, потому что там уже есть явный error contract,
  launcher/runtime parsing и multi-column persistence scenarios;
- при необходимости дотянуть orchestration-tests дальше до shared-config
  integration сценариев c реальным persistence/config boundary;
- более широкий runtime contract test для launcher strategy и bot lifecycle.
- продолжить держать notifier/runtime regression net синхронизированным с
  evolving routing governance contract, чтобы новые refactor-проходы не
  возвращали красный legacy test suite.

## Где мы сейчас

Если смотреть по смыслу, а не по номерам:

1. `Phase 1` завершён.
2. `Phase 2` стабилизирован и требует скорее нормализации ownership, чем
   срочной архитектурной ломки.
3. `Phase 3` завершён на controller boundary и уже движется в service-level
   split `DialogWorkspaceService`, но всё ещё упирается в giant `DialogService`.
4. `Phase 4` выполнен по самым рискованным giant flows и требует добивки
   remaining subdomains.
5. `Phase 5` уже начат в коде.
6. `Phase 6` усилен адресными runtime/UI тестами и уже покрывает основной
   sliced dialog/settings controller layer, shared config/env foundation и
   заметную часть page bootstrap contract; дополнительно туда уже вошли
   `autostart`, `integration network`, `bot process api`, `auth management`
   и panel-bot runtime edge-cases; поверх этого почти до полного
   controller-contract добран `DialogAiOps`, а `public-form` покрыт уже не
   только bootstrap/smoke, но и success/error/validation/page/template
   boundary, хотя это всё ещё не полноценная
   end-to-end safety net.

## Следующий Фокус

Наиболее логичный следующий шаг после текущего состояния:

1. Дожать `Phase 5` через распространение explicit `jar` contract на реальные
   окружения и финальное решение по supervisor/service boundary.
2. Параллельно расширять `Phase 6`, чтобы следующие рефакторинги шли под
   лучшей страховкой.
3. После этого продолжать service-level split `dialogs`: сначала дожать
   `DialogWorkspaceService`, затем резать сам `DialogService`, который остаётся
   самым большим архитектурным риском.

## Порядок выполнения

Актуальный порядок после уже выполненных этапов:

1. Довести `Phase 5` от launcher strategy до более явного runtime contract.
2. Расширить `Phase 6`, чтобы новые refactor-проходы не шли почти без тестов.
3. Вернуться к `dialogs` и резать `DialogService` по service-level bounded
   contexts.
4. Добить remaining `settings` subdomains, если они ещё остаются в общих слоях.
5. После этого уже решать, где нужен следующий integration/e2e слой, а где
   достаточно targeted runtime tests.

## Что не делать сейчас

- не начинать большой перенос всех SQL сценариев на JPA;
- не переписывать одновременно `DialogService`, `settings` subdomains и bot
  runtime contract;
- не смешивать визуальный редизайн с архитектурным рефакторингом сервиса.
