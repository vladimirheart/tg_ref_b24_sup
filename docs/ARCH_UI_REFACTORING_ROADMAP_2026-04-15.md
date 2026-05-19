# Architecture And UI Refactoring Roadmap

Дата старта: `2026-04-15`
Обновлено: `2026-05-19`

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

- `DialogService` уже больше не hotspot: `Phase 3` закрыта, giant dialog split
  доведён до thin facade;
- giant `/settings` transport/update тоже больше не hotspot: `Phase 4`
  закрыта, а remaining риск смещён в consistency/hardening;
- сейчас main architectural weight уже не живёт в одном giant service:
  `PublicFormService` доведён до thin facade, а remaining AI-assistant risk
  тоже не живёт в одном giant flow и локализован в bounded паре
  `DialogAiAssistantMessageFlowService` /
  `DialogAiAssistantMessageOutcomeService`; при этом
  `DialogWorkspaceRolloutGovernanceService` уже переведён в режим
  hardening/compatibility;
- при этом `PublicFormService` уже не untouched hotspot: runtime config,
  metrics, session flow, anti-abuse/request-identity, submit/captcha/
  validation, form-definition/config assembly, ticket/session persistence,
  channel/config/session lookup и теперь ещё submit entry-flow coordinator
  вынесены в отдельные bounded services; remaining weight там смещён уже не
  в giant service split, а в runtime contract и integration hardening;
- Phase 5 всё ещё не доведён до полноценного runtime contract между
  `spring-panel` и `java-bot`;
- Phase 6 уже широкая и системная, но ей всё ещё не хватает следующего слоя
  integration/e2e quality;
- часть inline `style/script` блоков в `settings`, `dashboard`, `dialogs`
  остаётся техническим долгом UI-слоя, но это уже не главный риск.

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
  сначала был переведён на compatibility bridge, а затем уже summary assembly
  ушёл в `DialogWorkspaceTelemetrySummaryAssemblerService`, так что текущий
  telemetry summary слой больше не зависит от `DialogService` и не держится
  на промежуточном bridge как на постоянном решении.
- следующим `Phase 3` пакетом giant service потерял ещё один крупный
  дублирующий bounded context: `macro governance audit` удалён из
  `DialogService`, `buildMacroGovernanceAudit(...)` оставлен только как
  compatibility delegate на `DialogMacroGovernanceAuditService`, а
  `DialogMacroGovernanceSupportService` больше не входит в constructor
  dependency самого giant service.
- следующим более широким `Phase 3` пакетом из giant service вынесен
  ещё один rollout slice: `DialogWorkspaceExternalKpiService` теперь
  владеет `external_kpi_signal`, datamart review/freshness/contract
  orchestration и снимает этот bounded context с `DialogService`;
  параллельно time-sensitive integration tests по rollout scorecard и
  governance packet переведены на fresh UTC timestamps, чтобы phase 3
  progress не зависел от устаревающих фиксированных дат;
  сам `DialogService` после telemetry analytics + external KPI split
  сжался уже примерно до `3199` строк.
- следующим ещё более широким `Phase 3` пакетом giant service потерял и
  rollout decision / scorecard bounded context:
  `DialogWorkspaceRolloutAssessmentService` теперь владеет rollout action
  logic, scorecard assembly и external checkpoint itemization, а
  `DialogService` переключён на новый bounded service и больше не держит
  даже constructor dependency на `DialogWorkspaceExternalKpiService`;
  под это добавлен отдельный `DialogWorkspaceRolloutAssessmentServiceTest`,
  а точечные rollout integration tests подтверждают, что UTC scorecard /
  governance packet contract не сломался после выноса;
  сам `DialogService` после этого прохода сжался уже примерно до
  `2533` строк.
- следующим ещё более широким `Phase 3` пакетом giant service потерял и
  rollout packet / governance packet bounded context:
  `DialogWorkspaceRolloutGovernanceService` теперь владеет governance
  packet assembly, parity exit criteria, legacy-only inventory, legacy
  manual-open policy и context-contract rollout packet orchestration, а
  `DialogService` переключён на thin delegate к новому bounded service;
  под это добавлен отдельный
  `DialogWorkspaceRolloutGovernanceServiceTest`, а targeted rollout
  integration tests подтверждают, что UTC scorecard / governance packet
  contract не сломался и после этого выноса;
  сам `DialogService` после этого прохода сжался уже примерно до
  `902` строк.
- следующим ещё более широким `Phase 3` пакетом из `DialogService`
  удалён уже мёртвый private legacy/support слой, который целиком
  дублировался в `DialogLookupReadService` и `DialogResponsibilityService`:
  старые `loadDialogsLegacy/findDialogLegacy`, responsible-profile
  enrichment helper’ы, users-table inspection и legacy responsibility
  private methods больше не живут в самом фасаде;
  после этого `DialogService` сжался уже примерно до `275` строк и
  стал реально thin orchestration facade.
- следующим пакетом фокус уже смещён в `DialogWorkspaceService`:
  request-contract normalization и final payload assembly вынесены в
  `DialogWorkspaceRequestContractService` и
  `DialogWorkspacePayloadAssemblerService`, а из
  `DialogWorkspaceService` удалены локальные include/limit/cursor/config
  helper'ы и финальный payload-builder; после этого `workspace`-сервис
  сжался примерно до `327` строк и стал ближе к thin orchestration слою.
- следующим ещё более широким пакетом уже не giant `DialogService`, а
  reply/runtime хвосты вокруг него разрезаны на отдельные bounded services:
  `DialogReplyTargetService` теперь владеет target lookup, ticket activity,
  web-form fallback и reply persistence, а `DialogReplyTransportService` —
  Telegram/VK/MAX text/media transport;
  `DialogReplyService` после этого сжат до примерно `194` строк и стал
  thin write-side orchestration слоем поверх target/transport dependencies.
- этим же пакетом убран последний telemetry-summary bridge-tail через giant
  service: raw summary assembly вынесена в
  `DialogWorkspaceTelemetrySummaryAssemblerService`, старый
  `DialogWorkspaceTelemetrySummaryBridgeService` удалён, а
  `DialogWorkspaceTelemetrySummaryService` и `DialogService` теперь
  делегируют напрямую в новый bounded service;
  после этого `DialogService` сжался уже примерно до `140` строк и
  фактически перестал быть самостоятельным архитектурным hotspot.
- по исходной цели roadmap `Phase 3` теперь можно считать выполненной:
  transport split домена `dialogs` завершён, giant `DialogService`
  доведён до thin facade, а remaining работа смещается из “monolith split”
  в post-phase hardening вокруг reply/notifier/telemetry consumers и
  integration-quality dialog boundaries.

Что остаётся:

- `DialogService` уже доведён до thin orchestration facade; оставшийся
  dialog-risk теперь живёт не в giant class, а в adjacent runtime/write-side
  boundaries вокруг reply/notifier/telemetry;
- `DialogWorkspaceService` уже резко сужен, поэтому следующий пакет стоит
  брать не по generic helper'ам, а по оставшимся bounded contexts:
  reply/message write-side, notifier/escalation и remaining mapper/assembly
  tails вокруг workspace consumers;
- главный следующий кандидат теперь reply-write / escalation / notifier
  bounded contexts и их прямые consumers вокруг `DialogWorkspaceService`,
  `DialogWorkspaceTelemetryService` и webhook-notifier слоя;
- продолжить снимать remaining consumer-facades вокруг notifier / telemetry /
  escalation слоёв там, где остались legacy-compatible transport contracts
  или shared runtime helper’ы;
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

- завершено по основным целям roadmap: giant `SettingsBridgeController` /
  `SettingsUpdateService` больше не являются точками концентрации домена, а
  transport/service split для основных `settings` subdomains доведён до
  рабочего baseline.
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
- продолжено: `SettingsApiController` тоже переведён на более узкий
  subdomain baseline — `client statuses` вынесены в
  `SettingsClientStatusService`, `it connection categories` — в
  `SettingsItConnectionCategoryService`, а `integration network probe` — в
  `SettingsIntegrationNetworkProbeService`; поверх этого добавлен отдельный
  `SettingsApiControllerWebMvcTest` и targeted unit tests на новые сервисы.
- продолжено: settings-adjacent governance хвост в `AnalyticsController`
  тоже разрезан — `macro governance review`, `external catalog policy` и
  `deprecation policy` вынесены в
  `AnalyticsMacroGovernancePolicyService`, а `AnalyticsController` оставлен
  thin transport wrapper над этим subdomain service.
- добавлена минимальная test safety net: routing/validation для нового
  `dialog_config` split покрыты unit-тестами, а legacy
  `DialogApiControllerWebMvcTest` синхронизирован с новой controller-разбивкой,
  чтобы не ломать `testCompile`.

Что это значит practically:

- основные самые рискованные giant flows в `settings` уже разрезаны;
- `SettingsBridgeController` и `SettingsUpdateService` больше не являются
  единственными точками концентрации домена;
- `SettingsApiController` больше не держит внутри себя catalog/probe/status
  orchestration и ближе к thin transport wrapper;
- `Phase 4` можно считать выполненной по исходной цели roadmap: giant
  `settings` transport/update слой разрезан, а remaining работа теперь уже
  относится скорее к hardening и соседним governance/integration boundary.

Что остаётся:

- держать под наблюдением remaining `catalog/reference data`,
  `partner/network`, `bot/integration settings`, чтобы они не начали снова
  собираться в общих слоях;
- отдельно дочищать соседние governance/integration endpoints уже как
  post-phase hardening, а не как незавершённый giant split;
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
- следующим маленьким hardening-пакетом тот же `PublicFormApiController`
  дополнительно прикрыт на bean-validation required-path: пустой
  `message` теперь возвращает explicit `VALIDATION_REQUIRED`, а не generic
  `VALIDATION_ERROR`.
- следующим более широким post-split пакетом `PublicFormApiController`
  переведён на отдельный `PublicFormApiResponseService`: success payload
  assembly вынесен из controller, а controller-managed error responses
  нормализованы до общего structured contract с `path/timestamp`.
- следующим bounded API-consistency пакетом из `PublicFormApiController`
  вынесен remaining contract/helper tail в `PublicFormApiContractService`:
  disabled-status fallback, requester-context resolution, error-code mapping
  и token masking больше не сидят прямо в controller, а malformed-body
  transport path зафиксирован отдельным WebMvc сценарием.
- после этого `PublicFormApiController` сжат примерно до `156` строк и
  доведён до уже довольно thin transport boundary поверх
  `PublicFormApiResponseService` и `PublicFormApiContractService`; следующий
  practical focus там смещён уже не в helper split, а в
  integration/e2e/runtime contract coverage.
- следующим integration-пакетом `PublicFormFlowSmokeIntegrationTest`
  расширен уже не только на happy-path submit, но и на живой runtime
  contract: missing channel, disabled form, malformed body и session miss
  теперь зафиксированы в `SpringBootTest` + SQLite с тем же structured
  `errorCode/path/timestamp` payload.
- следующим более широким integration-пакетом тот же
  `PublicFormFlowSmokeIntegrationTest` добран до continuation/session
  lifecycle: platform-specific continuation payload для `telegram` и `max`,
  telegram deep-link generation и rotate-on-read token lifecycle теперь
  тоже зафиксированы в живом runtime contract через `SpringBootTest` +
  SQLite + temp shared config.
- следующим более широким runtime-hardening пакетом этот же
  `PublicFormFlowSmokeIntegrationTest` добран до anti-abuse/expiry
  contract: HTTP idempotency reuse, structured `IDEMPOTENCY_CONFLICT`,
  live `RATE_LIMITED` rejection и `public_form_session_ttl_hours` expiry
  теперь тоже закреплены в живом `SpringBootTest` + SQLite сценарии.
- следующим более широким lifecycle-пакетом этот же
  `PublicFormFlowSmokeIntegrationTest` добран до polling/history contract:
  live `sessionPollingEnabled/sessionPollingIntervalSeconds`, shared
  conversation history после operator reply и system notifications, а также
  `replyPreview` для threaded ответа теперь тоже закреплены в реальном
  `public-form` session runtime.
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
- `auth-management/public-form/bot-process` boundary добран ещё глубже:
  теперь `AuthManagementApiControllerWebMvcTest` прикрывает invalid JSON
  fallbacks, simple-query mode без `role_id` и empty-permissions flows
  для roles; `PublicFormApiControllerWebMvcTest` — success/miss session
  payload contract beyond config/create flows; `BotProcessApiControllerWebMvcTest`
  — case-insensitive `STOPPED` и `null` message fallback уже не только
  в `stop`, но и в `status/start`.

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
- продолжить этим же способом расширять `auth-management` list/update
  contract и `public-form` session/runtime contract, потому что именно
  там теперь уже заметен выигрыш от transport-level regression net;
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
3. `Phase 3` выполнен по исходной цели giant dialog split: `DialogService`
   доведён до thin facade, а remaining dialog-risk смещён в
   `DialogWorkspaceService`, notifier/reply consumers и adjacent runtime
   boundaries.
4. `Phase 4` выполнен по самым рискованным giant flows и требует добивки
   remaining subdomains.
5. Post-`Phase 3` hardening уже сузил telemetry/notifier слой:
   `DialogWorkspaceTelemetryService` вынес control-builder bounded context и
   сжался примерно до `222` строк, `WorkspaceGuardrailWebhookNotifier`
   переведён на command/delivery split и сжался примерно до `43` строк.
6. Macro-governance audit slice тоже уже не giant-helper проблема:
   `DialogMacroGovernanceAuditService` стал thin coordinator поверх
   config/template/checkpoint/payload bounded services, а compatibility
   contract для mixed SQLite timestamps и legacy shared-config fixtures
   закреплён regression/integration tests.
7. `Phase 5` уже начат в коде.
8. `Phase 6` усилен адресными runtime/UI тестами и уже покрывает основной
   sliced dialog/settings controller layer, shared config/env foundation и
   заметную часть page bootstrap contract; дополнительно туда уже вошли
   `autostart`, `integration network`, `bot process api`, `auth management`
   и panel-bot runtime edge-cases; поверх этого почти до полного
   controller-contract добран `DialogAiOps`, а `public-form` покрыт уже не
   только bootstrap/smoke, но и success/error/validation/page/template
   boundary; рядом с этим также расширен `auth-management` route contract,
   invalid JSON/optional-column fallback и `bot-process` fallback/error
   contract, хотя это всё ещё не полноценная end-to-end safety net.

## Следующий Фокус

Наиболее логичный следующий шаг после текущего состояния:

1. `Phase 3` и `Phase 4` считать закрытыми и не возвращаться к ним как к
   giant-split проблемам.
2. Главный практический фокус теперь держать уже не на giant split
   `PublicFormService`, а на post-split hardening: AI assistant slice после
   нового outcome split удерживать в режиме hardening/compatibility,
   `DialogWorkspaceRolloutGovernanceService` после нового wide split тоже
   держать в этом режиме, а public-form путь продолжать уже через runtime
   contract, integration-quality и API consistency. `DialogWorkspaceService`
   при этом удерживать как thin orchestration layer.
3. Параллельно продолжать `Phase 5/6` уже не по giant wrappers, а по
   shared-config/runtime consistency и integration-quality.
4. В notifier/runtime hardening идти только по локальным bounded services,
   если они снова начинают расти, а не открывать новый monolith refactor.

## Порядок выполнения

Актуальный порядок после уже выполненных этапов:

1. После нового outcome split увести AI assistant slice в режим
   hardening/compatibility и giant split `PublicFormService` считать
   практически закрытым.
2. Довести `Phase 5` от launcher strategy до более явного runtime contract.
3. Расширить `Phase 6`, чтобы новые проходы шли уже под
   integration-quality, а не только под targeted tests.
4. Держать `DialogWorkspaceService` и соседние workspace consumers в
   thin-orchestration диапазоне, не возвращая giant-service риск.
5. Держать `settings` в режиме hardening, а не giant split: `Phase 4` уже
   выполнена, поэтому там приоритет только у regression/integration quality.

## Что не делать сейчас

- не начинать большой перенос всех SQL сценариев на JPA;
- не переписывать одновременно `DialogService`, `settings` subdomains и bot
  runtime contract;
- не смешивать визуальный редизайн с архитектурным рефакторингом сервиса.

- следующим более широким пакетом governance/routing tail тоже вынесен из
  SlaEscalationWebhookNotifier: новый SlaRoutingPolicyService теперь
  держит routing policy snapshot и governance audit, а сам notifier после этого
  сжат уже примерно до 350 строк;
- следующий hotspot в этом периметре теперь уже не notifier-wrapper, а
  SlaRoutingPolicyService (~1786 строк) с review checkpoint / issue
  classification / rule-definition parsing логикой.
- следующим более широким пакетом и этот hotspot сузился: появился
  `SlaRoutingRuleAuditService`, который забрал rule-definition parsing,
  conflict/broad-rule analysis, issue classification и rule-level audit
  payloads, а `SlaRoutingPolicyService` после этого сжат примерно до `639`
  строк и стал thin governance overlay.
- новый remaining hotspot в notifier/runtime hardening теперь уже не notifier и
  не policy wrapper, а `SlaRoutingRuleAuditService` (~`778` строк); следующий
  bounded split там — parser/normalization слой и governance issue matrix
  assembly.
- следующим более широким пакетом этот hotspot тоже сужен:
  `SlaRoutingRuleParserService` вынес rule normalization / candidate-match /
  rule-definition parsing, `SlaRoutingGovernanceIssueService` — governance issue
  matrix и rule-level payload assembly, а `SlaRoutingRuleAuditService` после
  этого стал thin coordinator примерно на `161` строку.
- новый remaining hotspot в notifier/runtime hardening теперь уже не audit
  coordinator, а `SlaRoutingRuleParserService` (~`444` строки); если он
  продолжит расти, следующий bounded split логично делать вокруг shared match
  DTO / normalization helpers.
- следующим ещё более широким пакетом и этот tail сузился ещё сильнее:
  `SlaRoutingRuleTypes` вынес общие rule DTO/enum типы,
  `SlaRoutingRuleScalarParserService` — scalar/temporal parsing,
  `SlaRoutingRuleMatchNormalizerService` — match/category/state normalization,
  `SlaRoutingGovernanceIssueFactoryService` — issue payload factory, а старый
  `SlaRoutingRuleValueParserService` после этого удалён.
- после этого `SlaRoutingRuleParserService` сжат примерно до `138` строк,
  `SlaRoutingGovernanceIssueService` — до `118`, а
  `SlaRoutingRuleAuditService` остаётся thin coordinator примерно на `145`
  строках.
- параллельно из `SlaRoutingPolicyService` убран локальный UTC/trim helper-tail
  для governance review checkpoint parsing, и он сжат примерно до `586`
  строк.
- новый remaining hotspot в notifier/runtime hardening теперь уже не
  parser/value helper слой, а `SlaRoutingPolicyService` (~`586` строк) с
  governance review / checkpoint summary overlay логикой.
- следующим ещё более широким пакетом и этот hotspot заметно сузился:
  `SlaRoutingPolicyConfigService` забрал shared config/runtime parsing,
  `SlaRoutingGovernanceReviewService` — governance review state, issues,
  requirements и review payload fragments.
- после этого `SlaRoutingPolicyService` сжат примерно до `448` строк и уже
  выполняет роль summary/checkpoint orchestration слоя поверх candidate scan,
  rule audit и governance review bounded services.
- новый remaining hotspot в notifier/runtime hardening теперь уже не
  config/review parsing, а финальный summary/checkpoint tail внутри
  `SlaRoutingPolicyService` (~`448` строк).
- следующим ещё более широким пакетом и этот tail сужен ещё сильнее:
  `SlaRoutingPolicySnapshotService` вынес snapshot preview flow,
  `SlaRoutingGovernanceSummaryService` — audit summary / checkpoint /
  advisory-path assembly.
- после этого `SlaRoutingPolicyService` сжат примерно до `123` строк и стал
  почти чистым facade/coordinator поверх snapshot, candidate scan, rule audit,
  config parsing и governance review слоёв.
- новый remaining hotspot в notifier/runtime hardening теперь уже не policy
  facade, а `SlaRoutingGovernanceSummaryService` (~`223` строки) и вторично
  `SlaRoutingPolicySnapshotService` (~`181` строк).
- это уже post-phase hardening, а не незавершённая фаза giant split:
  `Phase 3` и `Phase 4` остаются выполненными, а следующий фокус смещён в
  локальные notifier/runtime bounded services и integration-quality хвосты.
- следующий логичный bounded split теперь делать в
  `SlaRoutingGovernanceSummaryService`, если там продолжит расти
  checkpoint/advisory-path assembly; вторым приоритетом остаётся
  `SlaRoutingPolicySnapshotService`.
- следующим пакетом и этот summary-tail тоже разрезан:
  `SlaRoutingGovernanceCheckpointService` вынес required/advisory checkpoint
  metrics, `SlaRoutingGovernanceAuditPayloadAssemblerService` — финальную audit
  payload assembly, а `SlaRoutingGovernanceSummaryService` после этого сжат
  примерно до `127` строк и стал coordinator-слоем.
- новый remaining hotspot в notifier/runtime hardening теперь уже не
  `SlaRoutingGovernanceSummaryService`, а `SlaRoutingPolicySnapshotService`
  (~`202` строки) и вторично `SlaRoutingGovernanceCheckpointService`
  (~`189` строк).
- следующим пакетом и этот snapshot-tail тоже разрезан:
  `SlaRoutingPolicyCandidateBuilderService` вынес candidate payload assembly,
  `SlaRoutingPolicyPreviewSummaryService` — preview summary text,
  `SlaRoutingPolicyDecisionService` — critical decision tail.
- после этого `SlaRoutingPolicySnapshotService` сжат примерно до `136` строк,
  а remaining hotspot в notifier/runtime hardening смещён уже в
  `SlaRoutingGovernanceCheckpointService` (~`189` строк) и вторично в
  `SlaRoutingPolicySnapshotService` (~`136` строк).
- следующим более широким пакетом и checkpoint-tail тоже разрезан:
  `SlaRoutingGovernanceReviewPathService` вынес minimum required review path и
  advisory checkpoint builder, `SlaRoutingGovernanceSignalService` — noise,
  churn, lead-time и weekly-review priority signals.
- после этого `SlaRoutingGovernanceCheckpointService` сжат примерно до `127`
  строк, а remaining hotspot в notifier/runtime hardening смещён уже в
  `SlaRoutingPolicySnapshotService` (~`136` строк) и вторично в
  `SlaRoutingGovernanceSignalService` (~`132` строки).
- следующим ещё более широким пакетом и эти хвосты тоже разрезаны:
  `SlaRoutingGovernanceLeadTimeService` вынес lead-time/risk evaluation,
  `SlaRoutingGovernancePriorityService` — weekly-review priority и checkpoint
  closure policy, `SlaRoutingPolicySnapshotStateService` — base snapshot
  header и pre-critical state payloads.
- после этого `SlaRoutingGovernanceSignalService` сжат примерно до `96`
  строк, `SlaRoutingPolicySnapshotService` — до `121`, а remaining hotspot в
  notifier/runtime hardening смещён уже в совсем локальные bounded services.
- следующим ещё более широким пакетом и review/config слой тоже разрезан:
  `SlaRoutingGovernanceReviewStateService` вынес governance review state и
  issue evaluation, `SlaRoutingGovernanceReviewPayloadService` — requirements и
  governance review payload builders, `SlaRoutingPolicyTimeService` —
  UTC/minutes-left parsing, `SlaRoutingLifecycleStateService` — lifecycle
  normalization.
- после этого `SlaRoutingGovernanceReviewService` сжат примерно до `86`
  строк, `SlaRoutingPolicyConfigService` — до `89`, а remaining hotspot в
  notifier/runtime hardening смещён уже в `SlaRoutingGovernanceReviewStateService`
  (~`167` строк) и соседние rule normalization/types bounded contexts.
- следующим ещё более широким пакетом review/rule слой тоже разрезан:
  `SlaRoutingGovernanceReviewDecisionService` вынес governance review
  freshness/decision evaluation, `SlaRoutingGovernanceReviewIssueService` —
  issue collection, `SlaRoutingRuleBehaviorService` — matcher/specificity/route
  heuristics из `SlaRoutingRuleTypes`.
- после этого `SlaRoutingGovernanceReviewStateService` сжат примерно до `72`
  строк, `SlaRoutingRuleTypes` — до `47`, `SlaRoutingRuleParserService` — до
  `123`, `SlaRoutingGovernanceIssueService` — до `121`, а
  `SlaRoutingRuleAuditService` остаётся thin coordinator примерно на `148`
  строках.
- следующий локальный post-phase hardening focus теперь уже не в review-state:
  первично это `SlaRoutingRuleBehaviorService` (~`151` строк) и вторично
  `SlaRoutingRuleAuditService` (~`148` строк), если rule-behavior/audit слой
  снова начнёт расти.
- следующим ещё более широким пакетом и этот хвост тоже разрезан:
  появились `SlaRoutingRuleMatchService`, `SlaRoutingRuleDescriptorService`,
  `SlaRoutingRuleUsageAnalysisService`, `SlaRoutingRuleAuditMetricsService` и
  `SlaRoutingPolicyDecisionPayloadService`.
- после этого `SlaRoutingRuleBehaviorService` сжат примерно до `45` строк,
  `SlaRoutingRuleAuditService` — до `103`, `SlaRoutingPolicyDecisionService`
  — до `56`.
- следующий локальный post-phase hardening focus теперь уже смещён в
  `SlaRoutingRuleParserService` (~`123` строки), `SlaRoutingRuleMatchService`
  (~`110`) и `SlaRoutingPolicySnapshotService` (~`107`), если эти bounded
  services снова начнут расти.
- следующим ещё более широким пакетом parser/snapshot слой тоже разрезан:
  появились `SlaRoutingRuleDefinitionFactoryService`,
  `SlaRoutingRuleCandidateContextService`,
  `SlaRoutingPolicySnapshotRuntimeService` и
  `SlaRoutingPolicySnapshotBranchService`.
- после этого `SlaRoutingRuleParserService` сжат примерно до `83` строк,
  `SlaRoutingPolicySnapshotService` — до `56`.
- следующий локальный post-phase hardening focus теперь уже смещён в
  `SlaRoutingRuleMatchService` (~`110` строк), `SlaRoutingRuleAuditService`
  (~`103`) и вторично `SlaRoutingPolicySnapshotRuntimeService` (~`81`), если
  эти bounded services снова начнут расти.
- следующим ещё более широким пакетом и этот хвост тоже разрезан:
  появились `SlaRoutingRuleDimensionMatchService`,
  `SlaRoutingRuleThresholdMatchService`,
  `SlaRoutingRuleRequestMatchService`,
  `SlaRoutingRuleAuditEvaluationService` и
  `SlaRoutingPolicySnapshotDialogStateService`.
- после этого `SlaRoutingRuleMatchService` сжат примерно до `40` строк,
  `SlaRoutingRuleAuditService` — до `76`.
- следующий локальный post-phase hardening focus теперь уже смещён в
  `SlaRoutingPolicySnapshotRuntimeService` (~`83` строки), `SlaRoutingRuleParserService`
  (~`83`) и `SlaRoutingRuleAuditEvaluationService` (~`69`), если эти bounded
  services снова начнут расти.
- следующим ещё более широким пакетом и этот слой тоже дополнительно
  разрезан: появились `SlaRoutingPolicySnapshotSettingsService`,
  `SlaRoutingPolicySnapshotContextService`,
  `SlaRoutingRuleDefinitionMatchService`,
  `SlaRoutingRuleWinnerSelectionService` и
  `SlaRoutingRuleEvaluationContextService`.
- после этого `SlaRoutingPolicySnapshotRuntimeService` удерживается около
  `86` строк, `SlaRoutingRuleParserService` — около `67`,
  `SlaRoutingRuleUsageAnalysisService` — около `88`, а
  `SlaRoutingRuleAuditEvaluationService` — около `71`.
- следующий локальный post-phase hardening focus теперь уже смещён в
  `SlaRoutingRuleUsageAnalysisService`, `SlaRoutingPolicySnapshotRuntimeService`
  и вторично `SlaRoutingRuleEvaluationContextService`, если эти компактные
  bounded services снова начнут расти.
- следующим более широким пакетом по новому приоритету аудита дополнительно
  сужен и workspace orchestration layer: появились
  `DialogWorkspaceHistorySliceService`,
  `DialogWorkspaceSlaViewService` и
  `DialogWorkspaceClientContextAssemblerService`.
- после этого `DialogWorkspaceService` сжат примерно до `164` строк и уже не
  выглядит как основной orchestration hotspot сам по себе.
- следующий локальный focus в `dialogs` теперь уже не в самом
  `DialogWorkspaceService`, а в `DialogWorkspaceClientContextAssemblerService`
  и соседних context-heavy bounded services, если они снова начнут расти.
- следующим широким пакетом по новому главному приоритету аудита дополнительно
  сужен `DialogAiAssistantService`: review-flow вынесен в
  `DialogAiAssistantReviewService`, solution-memory lifecycle — в
  `DialogAiSolutionMemoryService`, а общий persistence/support слой — в
  `DialogAiAssistantPersistenceService`.
- после этого `DialogAiAssistantService` сжат примерно с `1932` до `1256`
  строк и уже не смешивает review/memory bounded contexts с остальным AI
  orchestration path.
- следующим широким пакетом тот же AI assistant slice дополнительно сужен:
  появились `DialogAiAssistantStateService`,
  `DialogAiAssistantConfigService` и
  `DialogAiAssistantOperatorFeedbackService`.
- после этого `DialogAiAssistantService` сжат примерно с `1256` до `882`
  строк и уже не держит dialog control/state updates, processing flags,
  auto-reply guard/config parsing и operator feedback/correction lifecycle.
- следующим широким пакетом remaining AI assistant tail дополнительно
  разрезан: появились `DialogAiAssistantPolicyService`,
  `DialogAiAssistantSuggestionService`,
  `DialogAiAssistantEventService` и
  общий `DialogAiAssistantSuggestionCandidate`.
- после этого `DialogAiAssistantService` сжат примерно с `882` до `482`
  строк; message-processing/control slice уже не giant, а следующий
  practical focus сужен до final orchestration/escalation хвоста.
- следующим широким пакетом remaining AI assistant orchestration tail
  дополнительно разрезан: появились `DialogAiAssistantEscalationService` и
  `DialogAiAssistantMessageFlowService`.
- после этого `DialogAiAssistantService` сжат примерно с `482` до `208`
  строк и уже работает как facade; основной bounded risk там теперь
  локализован в `DialogAiAssistantMessageFlowService` (~`369` строк), а не
  в самом сервисе-координаторе.
- следующий локальный focus теперь смещён в remaining
  message-processing/control tail внутри `DialogAiAssistantMessageFlowService`.
- следующим широким пакетом и этот AI tail дополнительно досужен:
  появился `DialogAiAssistantMessageOutcomeService`, который забрал
  decision outcome, consistency block и auto-reply/send lifecycle orchestration
  из `DialogAiAssistantMessageFlowService`.
- после этого `DialogAiAssistantMessageFlowService` сжат примерно с `369`
  до `267` строк, а новый bounded outcome-слой удерживается примерно на
  `337` строках; remaining AI risk теперь уже не в одном message-flow
  coordinator, а в локальной паре flow/outcome services без giant-facade
  симптомов.
- под новый split добавлен `DialogAiAssistantMessageOutcomeServiceTest`;
  compile и targeted AI assistant regression net остаются зелёными.
- следующим широким пакетом стартовал и соседний bounded split
  `PublicFormService`: `PublicFormRuntimeConfigService` забрал dialog config
  readers и locale/polling/disabled-status rules, `PublicFormMetricsService`
  — config/submit/session metrics, `PublicFormSessionService` — session
  lookup/token rotation lifecycle.
- после этого `PublicFormService` сжат примерно с `1327` до `1020` строк, а
  следующий practical focus там уже уже: submit/config/idempotency/rate-limit
  orchestration tail.
- следующим пакетом из `PublicFormService` вынесен anti-abuse/request identity
  bounded slice: новый `PublicFormAntiAbuseService` теперь владеет
  requester fingerprint key, requestId normalization, payload hash,
  idempotency cache и rate-limit policy.
- после этого `PublicFormService` сжат примерно с `1020` до `889` строк, а
  remaining practical focus там смещён уже не в mixed anti-abuse tail, а в
  submit/captcha/validation orchestration и соседний public-form config parser.
- под новый split добавлен `PublicFormAntiAbuseServiceTest`; targeted
  public-form WebMvc/unit/integration net остаётся зелёным.
- следующим пакетом из `PublicFormService` вынесен submit/captcha/validation
  bounded slice: новый `PublicFormSubmissionPolicyService` теперь владеет
  payload sanitization, form summary assembly, captcha enforcement,
  field/type/location validation и client-name resolution.
- после этого `PublicFormService` сжат примерно с `889` до `534` строк, а
  remaining practical focus там смещён уже в public-form config parser,
  location preset enrichment и ticket/session projection orchestration.
- под новый split добавлен `PublicFormSubmissionPolicyServiceTest`; targeted
  `PublicFormApiControllerWebMvcTest`, `PublicFormControllerWebMvcTest`,
  `PublicFormLocationIntegrationTest`, `PublicFormAntiAbuseServiceTest` и
  новый submission-policy unit net остаются зелёными.
- следующим пакетом из `PublicFormService` вынесен form-definition/config
  bounded slice: новый `PublicFormDefinitionService` теперь владеет demo
  config assembly, `questions_cfg` parsing, schema/disabled-status
  normalization, location preset enrichment и question ordering.
- по пути устранён parser bug для textual `JsonNode`: `successInstruction`
  теперь нормализуется без лишних JSON-кавычек.
- после этого `PublicFormService` сжат примерно с `534` до `343` строк, а
  remaining practical focus там смещён уже в ticket/session projection
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
- после этого `PublicFormService` сжат примерно с `343` до `198` строк, а
  remaining practical focus там смещён уже в continuation/runtime helper
  tail и thin entry-flow coordinator logic.
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
- после этого `PublicFormService` сжат примерно с `198` до `115` строк, а
  remaining practical focus там смещён уже не в runtime plumbing, а в
  thin `createSession` entry-flow coordinator и final runtime contract
  hardening.
- под новый split добавлен `PublicFormChannelServiceTest`; targeted
  `PublicFormApiControllerWebMvcTest`, `PublicFormControllerWebMvcTest`,
  `PublicFormLocationIntegrationTest`, `PublicFormFlowSmokeIntegrationTest`,
  `PublicFormAntiAbuseServiceTest`, `PublicFormSubmissionPolicyServiceTest`,
  `PublicFormDefinitionServiceTest`,
  `PublicFormSubmissionPersistenceServiceTest` и новый channel-service unit
  net остаются зелёными.
- следующим пакетом из `PublicFormService` вынесен submit entry-flow
  coordinator bounded slice: новый `PublicFormSubmissionFlowService`
  теперь владеет channel/config gating, submission preparation, anti-abuse
  idempotency/rate-limit orchestration и persistence handoff.
- после этого `PublicFormService` сжат примерно с `115` до `81` строки и
  превращён в thin facade; giant public-form split можно считать
  практически закрытым, а следующий practical focus смещён уже на runtime
  contract, integration-quality и API consistency.
- под новый split добавлен `PublicFormSubmissionFlowServiceTest`; targeted
  `PublicFormApiControllerWebMvcTest`, `PublicFormControllerWebMvcTest`,
  `PublicFormLocationIntegrationTest`, `PublicFormFlowSmokeIntegrationTest`,
  `PublicFormAntiAbuseServiceTest`, `PublicFormSubmissionPolicyServiceTest`,
  `PublicFormDefinitionServiceTest`,
  `PublicFormSubmissionPersistenceServiceTest`,
  `PublicFormChannelServiceTest` и новый flow-service unit net остаются
  зелёными.
