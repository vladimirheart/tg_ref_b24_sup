# Architecture And UI Refactoring Roadmap

Дата старта: `2026-04-15`
Обновлено: `2026-06-05`

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
- `workspace` action-contract больше не ограничен только
  `reply/take/resolve/reopen/reassign/participants_*`: туда добавлены и
  explicit guards для `categories` и `spam`, чтобы operator-facing payload
  меньше расходился с реальными quick-action runtime возможностями.

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
- следующим более широким continuity-пакетом этот же
  `PublicFormFlowSmokeIntegrationTest` добран до cross-session/history
  continuity: `previous history` теперь проверяется на двух `web_form`
  обращениях одного requester, включая `sourceKey/sourceLabel` и resolved
  status предыдущего тикета, а resolve/reopen lifecycle через
  `DialogQuickActionService` закреплён и в `public-form` session history.
- следующим более широким bridge/runtime-пакетом этот же
  `PublicFormFlowSmokeIntegrationTest` добран до live `public-form ->
  dialogs` read bridge: `/api/dialogs/{ticketId}` и
  `/api/dialogs/{ticketId}/history` теперь проверяются на shared history
  для `web_form` тикета, а `DialogReadService` дополнительно закреплён на
  `last_read_at` update для ответственного оператора; рядом тем же smoke
  слоем зафиксирован и `/api/dialogs/public-form-metrics` runtime bridge
  на живые `views/submits/sessionLookups/sessionLookupMisses`.
- следующим более широким lifecycle/triage-пакетом этот же
  `PublicFormFlowSmokeIntegrationTest` добран уже до live
  `public-form -> dialogs` list/details bridge: `/api/dialogs` теперь
  проверяется на `summary`, `statusKey` переходы `new ->
  waiting_operator -> waiting_client`, critical SLA escalation signals и
  operator ownership lifecycle для `web_form` тикета; соседним сценарием
  закреплены `resolved`/categories projection в `/api/dialogs/{ticketId}`
  и continuity этого же resolved dialog через
  `/api/dialogs/{ticketId}/history/previous`.
- следующим runtime-пакетом этот же `PublicFormFlowSmokeIntegrationTest`
  добран до `public-form -> notification routing` continuity: для
  follow-up обращения закреплены operator bell notification creation и
  read-reset через live `NotificationService summary`, а peer-notification
  routing на `resolve/reopen` ветках теперь прикрыт рядом с
  `resolved/categories` projection самого dialog.
- отдельным transport hardening шагом добавлен первый dedicated
  `NotificationApiControllerWebMvcTest`: `UserDetails`, plain-auth-name и
  `all` fallback ветки теперь зафиксированы для `list`, `unread_count` и
  `markAsRead`, так что следующий notification/runtime пакет можно брать уже
  поверх явного controller safety net, а не только через косвенные smoke
  сценарии.
- следующим более широким notification/runtime шагом добавлен live
  `NotificationApiIntegrationTest` на `SpringBootTest + SQLite`: он
  закрепляет реальный `list/unread_count/markAsRead`, identity scope и
  runtime bridge от `NotificationService.notifyUsersExcluding`, а сам
  persistence/read path выровнен через `LenientOffsetDateTimeConverter`
  для `Notification.createdAt`; параллельно снят `JdbcTemplate.query(...)`
  ambiguous-хвост в `NotificationService`, чтобы `public-form` alert
  routing и dialog participant notifications снова были стабильны в smoke
  пакетах.
- следующим более широким service/runtime continuity пакетом notification
  слой расширен в `SupportPanelIntegrationTests`: отдельно закреплены
  recipient merge из `ticket_responsibles + ticket_active`, operator
  fallback при пустом dialog audience и schema-aware filtering для
  operator pool across main/users SQLite. Параллельно снят
  `usersJdbcTemplate.query(...)` ambiguous compile-blocker в
  `DialogLookupReadService`, так что dialog list/details responsible
  profile enrichment снова проходит в live integration наборе. Следующий
  practical focus здесь уже логично смещать не на ещё один recipient
  helper, а на `NotificationRoutingService` / `AlertQueueService`
  continuity и escalation audience hardening.
- этот следующий practical focus теперь закрыт отдельным hardening пакетом:
  добавлены `AlertQueueServiceTest` и `NotificationRoutingServiceTest` с
  живыми SQLite сценариями для `employees_only`, `department_except`,
  `all_operators`, `online_only_fallback_all` и legacy `alertQueue`
  routing. Заодно исправлены readable incoming-client alert text,
  local-timestamp parsing для online filtering и ещё один
  `JdbcTemplate.query(...)` ambiguous compile-blocker в
  `NotificationRoutingService`. Следующий logical focus уже смещается на
  `OperatorNotificationWatcher` / escalation audience continuity в более
  широком runtime orchestration слое.
- этот следующий watcher/orchestration focus теперь тоже закрыт отдельным
  пакетом: добавлен `OperatorNotificationWatcherTest` на incoming alert
  routing, `public_form_submit` initial branch и first-response-overdue
  fallback. Заодно снят production-bug с wrong `JdbcTemplate.query(...)`
  overload в `watchChatHistoryMessages` / `watchFeedbacks`, а overdue
  alert path получил fallback на operator audience, если
  `AlertQueueService` не смог доставить notification по route. Следующий
  logical focus теперь уже честно смещается на более широкий
  `dialog/public-form/notification` runtime duplication and escalation
  contract hardening.
- этот следующий duplication focus теперь тоже закрыт отдельным пакетом:
  `PublicFormSubmissionPersistenceService` фиксирует successful/failed
  queue-delivery в `public_form_new_appeal_notification`, а
  `OperatorNotificationWatcher` больше не дублирует initial
  `notifyAllOperators(...)`, если alert уже был доставлен на submit path.
  При этом legacy fallback сохранён для ветки без recipients, где queue
  route вернул `false` и watcher всё ещё остаётся последней страховкой.
- следующим continuity пакетом добран и source-level dialog-link contract:
  `AlertQueueService`, `OperatorNotificationWatcher`,
  `DialogAiAssistantEscalationService`,
  `DialogAiAssistantOperatorFeedbackService` и `DialogQuickActionService`
  переведены на общий `NotificationService.buildDialogUrl(...)`, так что
  operator-facing notifications больше не генерируют legacy `?ticketId`
  URLs и не полагаются на позднюю post-save normalisation. Рядом в корневой
  `.gitignore` добавлен `/logs/`, чтобы локальные sync/runtime прогоны не
  загрязняли рабочее дерево лишним шумом.
- следующим service-level continuity шагом добран уже сам
  `DialogQuickActionService`: добавлен dedicated regression net на
  `sendReply`, `resolveTicket`, `reopenTicket` и `takeTicket`, который
  фиксирует `clearProcessing`, learning handoff в AI assistant,
  resolved/reopened notification side-effects и participant-notification
  continuity. Это снимает ещё один orchestration-risk, который раньше
  держался в основном на controller-contract и public-form smoke.
- следующим расширением этого же regression net `DialogQuickActionService`
  теперь прикрыт и по `sendMediaReply`, `updateCategories`,
  `addParticipant`, `removeParticipant` и `reassignTicket`: service-level
  continuity зафиксировала media reply payload contract, category-update
  notification side-effects и operator collaboration lifecycle вокруг
  participant/reassign веток. После этого следующий practical focus здесь уже
  смещён в integration/runtime continuity для `edit/delete` и смежных dialog
  side-effects, а не в базовые unit orchestration branches.
- следующим bounded follow-up шагом quick-action service-level net добран и
  по `editReply`/`deleteReply`: success/failure ветки теперь отдельно
  фиксируют dialog-route notification side-effects для message mutation
  сценариев. После этого следующий practical focus честно смещён уже в
  integration/runtime continuity вокруг quick-action side-effects на живой
  dialog history, participant audience и соседних transport bridges, а не в
  дальнейшее наращивание unit-level branch coverage.
- следующим transport-boundary пакетом расширен и
  `DialogQuickActionsControllerWebMvcTest`: к уже покрытым
  `reply/resolve/take/media/categories/snooze` добавлены `edit`, `delete`,
  `reopen`, `participants add/remove` и `reassign` contract scenarios с
  проверкой status/payload и action-audit logging. После этого следующий
  practical focus здесь уже смещён из controller/unit слоя в live runtime
  continuity quick-action side-effects.
- следующим live runtime пакетом `SupportPanelIntegrationTests` добрали
  participant/reassign continuity на реальном SQLite слое: add/remove
  participant и reassign теперь закреплены через `DialogQuickActionService`,
  `DialogReadService`, реальные `ticket_participants/ticket_responsibles`
  projection и notification audience side-effects. Заодно cleanup тестового
  слоя синхронизирован с `ticket_participants`, `ticket_active` и
  `ticket_responsibles`, чтобы repeated прогон не тек между сценариями.
- следующим bounded follow-up пакетом добран уже и `DialogReadController`
  runtime contract: новый `DialogReadIntegrationTest` плюс расширенный
  `DialogReadControllerWebMvcTest` фиксируют `history`,
  `history/previous`, `participants` и `operators` не только по делегации,
  но и на живом `SpringBootTest + SQLite` слое. Под этим теперь отдельно
  закреплены `replyPreview/originalMessage/editedAt/deletedAt/forwardedFrom`,
  `last_read_at` read receipt и users-directory projection даже при
  optional-column schema drift. После этого следующий practical focus в
  dialog-read зоне уже смещён с basic transport coverage на
  `details/workspace` runtime continuity.
- этот же smoke-слой расширен на detail/subpage contract:
  `ai-ops`, `unblock requests`, `users/detail` и оба passport editor route
  (`/object-passports/new`, `/object-passports/{id}`) теперь тоже прикрыты
  WebMvc smoke tests на ранний bootstrap и explicit `data-ui-page`.
- следующим bounded пакетом закрыт и live `details` continuity:
  добавлен `DialogDetailsIntegrationTest`, а
  `DialogReadControllerWebMvcTest` и `DialogDetailsReadServiceTest`
  расширены на `404`/miss-path ветки. Теперь `/api/dialogs/{ticketId}`
  закреплён на реальном `SpringBootTest + SQLite` contract с
  summary/history/categories, responsible profile projection,
  embedded `replyPreview/originalMessage`, `last_read_at` read receipt и
  explicit not-found payload. После этого practical focus в
  dialog-read/workspace зоне смещён уже с basic details continuity на
  settings-driven context-contract, parity и related projection edge cases.
- следующим пакетом добран и live `workspace` settings-driven contract:
  `DialogWorkspaceIntegrationTest` теперь фиксирует rollout-required
  `billing` scenario с mandatory/source-of-truth/priority-block violations,
  playbook projection и runtime `invalid_utc` source status, а
  `DialogWorkspaceContextContractServiceTest` дополнительно закрепляет
  scoped playbook для `source_of_truth:phone:crm:invalid_utc`.
  После этого practical focus в dialog-read/workspace зоне смещён уже с
  базового context-contract bootstrap на parity/related projections и
  operator-facing workspace context edge cases.
- следующим пакетом добран и live `workspace` degradation/runtime contract:
  `DialogWorkspaceIntegrationTest` теперь покрывает partial
  `include=context,permissions`, settings-driven limits для
  `history/related_events`, disabled inline navigation и parity attention
  path без `messages/sla`, а `DialogWorkspaceNavigationServiceTest`
  закрепляет legacy local-datetime normalization для queue items.
  После этого practical focus в dialog-read/workspace зоне смещён уже с
  базового parity/related bootstrap на более глубокие
  operator-facing composer/permissions parity и read-model projection edges.
- следующим пакетом добран и operator-facing `workspace`
  permissions/composer parity contract: `DialogWorkspaceIntegrationTest`
  теперь фиксирует live scenario с explicit denied permissions для
  `/api/dialogs/{ticketId}/workspace`, а `DialogWorkspaceParityServiceTest`
  закрепляет `composer` disable и distinction между `blocked`
  (missing permission envelope) и `attention` (explicit boolean deny).
  После этого practical focus в dialog-read/workspace зоне смещён уже с
  базовой parity semantics на deeper operator-facing projection drift и
  adjacent read-model/composer edge cases.
- следующим пакетом добран и rich `workspace` timeline payload contract:
  `DialogWorkspaceIntegrationTest` теперь покрывает live mutation/media
  scenario с `replyPreview`, `originalMessage`, `editedAt`, `deletedAt`,
  `forwardedFrom` и attachment URL routing, а
  `DialogWorkspacePayloadAssemblerServiceTest` закрепляет full included
  payload с escalation state и parity/meta envelope. После этого
  practical focus в dialog-read/workspace зоне смещён уже с timeline
  payload continuity на deeper operator workflow parity и adjacent
  projection drift edge cases.
- следующим пакетом добран и `workspace` rollout/governance fallback
  contract: `DialogWorkspaceRolloutServiceTest` теперь покрывает
  `cohort_rollout`, stale review и invalid review timestamp semantics для
  `legacy_manual_open_policy`, а `DialogWorkspaceIntegrationTest`
  закрепляет live `meta.rollout` projection для experiment metadata,
  fallback availability и blocked manual-open policy. После этого
  practical focus в dialog-read/workspace зоне смещён уже с
  rollout/bootstrap semantics на deeper operator workflow parity и
  adjacent projection drift edge cases.
- следующим пакетом добран и `workspace` rollout projection drift:
  `DialogWorkspaceService` теперь добавляет в `meta.rollout`
  `external_kpi_signal` и compact governance summary, а
  `DialogWorkspaceIntegrationTest` закрепляет это в live runtime payload
  вместе с experiment metadata, KPI readiness/risk и governance gates.
  После этого practical focus в dialog-read/workspace зоне смещён уже с
  rollout projection plumbing на deeper operator workflow parity и
  adjacent action/projection drift edge cases.
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
- следующим более широким пакетом закрыт `dialog workspace operator workflow
  projection drift`: `/workspace` теперь возвращает отдельный `workflow`
  snapshot с `responsible`, `participants`, `reassign_candidates`,
  `participant_candidates`, `triage_preferences` и collaboration summary,
  так что operator-facing workspace contract меньше зависит от соседних
  read endpoints.
- под это добавлен bounded `DialogWorkspaceWorkflowSnapshotService`,
  расширен `DialogWorkspacePayloadAssemblerService`, а parity-слой получил
  explicit `operator_workflow_projection` check для collaboration/triage
  surface.
- targeted `DialogWorkspaceWorkflowSnapshotServiceTest`,
  `DialogWorkspacePayloadAssemblerServiceTest`,
  `DialogWorkspaceParityServiceTest`, `DialogWorkspaceIntegrationTest` и
  `DialogWorkspaceControllerWebMvcTest` остаются зелёными.
- следующим более широким пакетом закрыт и `dialog workspace action/runtime
  continuity`: `/workspace` теперь возвращает не только collaboration/triage
  snapshot, но и explicit `workflow.actions` contract с
  `enabled/disabled_reason` для `reply/reply_media/take/resolve/reopen/
  reassign/participants_add/participants_remove`.
- `DialogWorkspaceParityService` получил `operator_action_guards`, а
  `DialogWorkspaceIntegrationTest` закрепляет live continuity после
  `DialogQuickActionService.reassignTicket(...)` и `resolveTicket(...)`,
  включая updated responsible, closed status и guard reasons в runtime payload.
- targeted `DialogWorkspaceControllerWebMvcTest`,
  `DialogWorkspaceWorkflowSnapshotServiceTest`,
  `DialogWorkspacePayloadAssemblerServiceTest`,
  `DialogWorkspaceParityServiceTest` и `DialogWorkspaceIntegrationTest`
  остаются зелёными на новом action/runtime contract.
- следующим пакетом добран и обратный `dialog workspace` quick-action
  lifecycle: live `DialogWorkspaceIntegrationTest` теперь фиксирует
  `reassign -> resolve -> reopen -> removeParticipant` continuity для
  `/workspace`, включая runtime-статус `waiting_operator`, refreshed candidate
  pools и `participants_remove.disabled_reason=no_participants`.
- `DialogWorkspaceWorkflowSnapshotServiceTest` дополнительно страхует этот
  `no_participants` guard на unit-уровне, так что action badges и
  participant-management surface прикрыты уже не только на статическом
  snapshot, но и на реальном lifecycle loop.
- targeted `DialogWorkspaceControllerWebMvcTest`,
  `DialogWorkspaceWorkflowSnapshotServiceTest`,
  `DialogWorkspacePayloadAssemblerServiceTest`,
  `DialogWorkspaceParityServiceTest` и `DialogWorkspaceIntegrationTest`
  остаются зелёными на full quick-action lifecycle envelope.
- следующим пакетом quick-action lifecycle parity расширен уже beyond
  `workspace`: `DialogDetailsIntegrationTest` теперь закрепляет
  `reassign -> resolve -> reopen` continuity для `/api/dialogs/{ticketId}`, а
  `DialogReadIntegrationTest` — `/participants` projection после
  `reassign -> removeParticipant`.
- это даёт уже cross-consumer runtime contract для owner/status/participant
  lifecycle: `workspace`, `details` и `read` routes подтверждают один и тот же
  quick-action outcome без явного projection drift.
- targeted `DialogWorkspaceIntegrationTest`, `DialogDetailsIntegrationTest` и
  `DialogReadIntegrationTest` остаются зелёными на общем lifecycle наборе.
- следующим пакетом добрана и `audit/related-events continuity` после
  quick actions: `DialogWorkspaceIntegrationTest` теперь гоняет реальные HTTP
  quick-action endpoints и подтверждает, что `/workspace` проецирует не только
  owner/status outcome, но и audit trail в `context.related_events`.
- это даёт уже не просто cross-consumer parity по lifecycle, а и
  operator-facing continuity по action trail: `reassign`, `quick_close` и
  `participants_remove` остаются видимыми в workspace context после тех же
  controller-level runtime веток, которыми пользуется UI.
- следующим пакетом добран и `notification/read-marker refresh loop`:
  `DialogDetailsIntegrationTest` теперь связывает `/api/dialogs` list unread,
  `/api/dialogs/{ticketId}` read receipt и `/api/notifications` bell badge в
  одном live runtime сценарии.
- это подтверждает ожидаемую UI-семантику: открытие dialog details обнуляет
  только dialog unread projection через `last_read_at`, но не снимает bell
  notification автоматически; отдельный unread badge сбрасывается только через
  explicit notification read endpoint.
- следующим более широким пакетом эта семантика добрана и для соседних
  consumer routes: `DialogReadIntegrationTest` и
  `DialogWorkspaceIntegrationTest` теперь закрепляют тот же live loop для
  `history` и `workspace`, а не только для `details`.
- это выравнивает operator UX на всём основном read surface: `details`,
  `history` и `workspace` одинаково гасят dialog unread projection, но не
  подтверждают bell notification implicitly.
- следующим пакетом добрана и `rearm`-семантика после явного ack:
  `DialogDetailsIntegrationTest` теперь закрепляет, что после
  `POST /api/notifications/{id}/read` следующий client follow-up снова
  поднимает и list unread, и bell unread badge.
- это подтверждает, что UI loop не “залипает” после первого ack:
  подтверждённая notification не мешает следующему follow-up создать новый
  unread state, а `details` route повторно двигает `last_read_at` уже на новом
  клиентском сообщении.
- следующим пакетом этот runtime loop добран и на `queue/my_dialogs`
  projection surface: `DialogDetailsIntegrationTest` теперь фиксирует
  переходы `my_dialogs.unanswered -> in_work -> unanswered` для одного ticket
  через `details` read, explicit bell ack, operator reply и следующий
  client follow-up.
- это выравнивает уже не только unread/bell semantics, но и list bucket UX:
  repeated follow-up после ack возвращает диалог в `unanswered`, а
  operator reply при `unreadCount=0` переводит его в `in_work` без drift
  относительно `statusKey waiting_operator/waiting_client`.
- следующим более широким пакетом добран и `queue/status-owner` lifecycle:
  `DialogDetailsIntegrationTest` теперь закрепляет handoff old-owner/new-owner
  для `my_dialogs` после `reassign`, а также list-level contract для
  `resolve -> reopen` на том же `/api/dialogs` payload.
- это даёт уже не только rearm parity в рамках одного operator loop, но и
  ownership/status continuity на list surface: старый owner теряет dialog из
  `my_dialogs`, новый owner получает его в правильном bucket, а `resolved`
  ticket исчезает из `my_dialogs` до `reopen`, после которого возвращается в
  `in_work`.
- следующим пакетом этот list lifecycle добран и на regression net ниже
  integration-уровня: `DialogLookupReadServiceTest` и
  `DialogListReadServiceTest` теперь отдельно страхуют `auto_processing`
  bucket semantics, owner filtering и `my_dialogs` payload assembly.
- это делает `queue/status-owner` hardening менее хрупким: основная логика
  `unanswered/in_work` закреплена не только live runtime сценариями, но и
  быстрыми service-level тестами, которые ловят drift в grouping/assembly
  слое без полного `SpringBootTest`.
- следующим пакетом добран и dedicated list consumer boundary:
  `DialogListIntegrationTest` теперь отдельно закрепляет `/api/dialogs`
  runtime contract для null-auth envelope и owner-aware bucket transitions,
  а `DialogListControllerWebMvcTest` остаётся focused на transport delegation.
- это убирает зависимость list surface от косвенного coverage через
  `DialogDetailsIntegrationTest`: у `/api/dialogs` теперь есть собственный
  live SQLite contract для `my_dialogs` empty envelope и `reassign ->
  follow-up` list lifecycle.
- quick-action runtime boundary для `dialogs` теперь уже закрыт как единый
  consumer-пакет: live `DialogQuickActionsIntegrationTest` подтверждает
  response contract и downstream continuity для `reassign`,
  `participants add/remove`, `resolve -> reopen`, `take/categories/spam` и
  `snooze` на `/api/dialogs`, `/api/dialogs/{ticketId}`, `/participants` и
  `/workspace`, включая repeated reread, handoff `unanswered -> in_work`,
  owner/category projection и `workspace.context.related_events`.
- controller/runtime envelope для action endpoints выровнен вокруг explicit
  timing/audit contract: `categories`, `reopen`, `reply`, `edit`, `delete` и
  `reply_media`, а также `snooze` на `404/not_found` boundary, теперь проходят
  через единый quick-action guard, пишут `success/error/not_found` audit
  trail; этот же missing-dialog boundary теперь явно закреплён и для
  `take`, `mark_spam`, `participants_add` и `reassign`; same-owner `take`
  теперь отвечает explicit noop `changed=false` с
  `already_assigned_to_operator` и не плодит bell side-effects, а closed-dialog
  `take` больше не расходится с workspace guard: action выключается с
  `closed_dialog`, runtime отвечает explicit `400/error` и не меняет
  responsible/bell trail; такой же closed-dialog drift снят и у `snooze`:
  UI уже скрывал action на resolved/closed dialog, а теперь это же правило
  закреплено в `workspace.actions.snooze` и runtime `400/error` contract без
  ложного `success` audit; lifecycle state-contract тоже выровнен на живом
  сервисе: `reopen` на открытом dialog больше не проходит как `success/noop`,
  а `resolve` на уже закрытом состоянии не переходит через молчаливый update;
  оба action now возвращают explicit `not_closed/already_closed`, включая
  legacy `status='closed'` ветку; collaboration ветка отдельно прикрыта и на
  `already_present`, `participant_missing` и same-owner `reassign` error
  semantics, invalid-target
  `Пользователь панели не найден`, плюс на `closed_dialog` boundary для
  `participants_add/reassign` без participant/responsible drift и без лишних
  notification side-effects; live runtime net отдельно фиксирует, что
  noop/error collaboration ветки не наращивают bell rows поверх последнего
  успешного action trail и не двигают `unread_count`/notification list после
  explicit read-ack; parity-layer требует
  `categories/spam/snooze` как часть `operator_action_guards`.
- browser runtime тоже подтянут к тем же guard-правилам: после server-side
  hardening для closed-dialog `take` `dialogs.js` больше не оставляет action
  доступным в row menu, details view, workspace header и обходных client-paths
  вроде macro workflow / shortcut-trigger, то есть UI больше не зовёт action,
  который runtime уже считает `closed_dialog`.
- следующим более широким client-side пакетом `dialogs.js` начал опираться не
  только на `permissions/status`, но и на `workspace.workflow.actions`:
  workspace header, details actions, category editor и collaboration modals
  (`reassign`, `participants_add/remove`) теперь учитывают explicit
  `enabled/disabled_reason`, candidate lists и closed/open state из runtime
  контракта, а не пересобирают эти правила локальными эвристиками.
- write-side message mutations собраны в один закрытый runtime block:
  transport и `web_form` ветки для `reply -> edit -> delete` и `reply_media`
  подтверждены на `details/history/workspace/list`, включая
  `originalMessage/editedAt/deletedAt`, attachment projection,
  `my_dialogs.in_work`, repeated reread и local synthetic `tg_message_id`
  fallback для `web_form edit/delete`.
- bell-consumer contract тоже закрыт как единый блок: `/api/notifications`
  теперь live-прикрыт и для message-side mutations, и для non-message
  lifecycle/collaboration actions (`take -> categories -> resolve -> reopen`,
  `reassign -> participants_add -> participants_remove`) и shared moderation
  action `mark_spam`, включая
  `unread_count`, `POST /api/notifications/{id}/read`, repeated list reread с
  `read=true` и AI-muted сценарии через
  `ticket_ai_agent_dialog_control.ai_disabled=1`, чтобы не смешивать
  quick-action parity с escalation noise.
- следующий practical focus в `dialog workspace/read/details` зоне смещён уже
  с cross-consumer lifecycle parity, basic audit trail, full notification
  refresh loop, `queue/my_dialogs` rearm parity и `queue/status-owner`
  lifecycle на более тонкие consumer refresh loops после repeated follow-up
  refresh и оставшийся drift вокруг соседних operator action surfaces.

## Audit Checkpoint 2026-06-05

Что подтверждено повторным срезом по текущему дереву:

- исходные giant-hotspots из roadmap действительно удержаны под контролем:
  `DialogAiAssistantService` уже около `242` строк, `PublicFormService` —
  около `102`, `DialogWorkspaceService` — около `204`, то есть возвращения к
  прежнему monolith shape сейчас не видно;
- remaining AI/runtime риск теперь живёт не в одном giant coordinator, а в
  bounded паре `DialogAiAssistantMessageFlowService` /
  `DialogAiAssistantMessageOutcomeService` (`~288` / `~357` строк), что уже
  больше похоже на локальный hardening-контур, а не на повторный giant split;
- backend pressure сместился в соседние orchestration/controller слои,
  которые не были исходным P1 roadmap, но уже выглядят как следующие
  кандидаты на локальный bounded split:
  `DialogWorkspaceTelemetryAnalyticsService` (`~1171` строк),
  `ChannelApiController` (`~1168`),
  `AnalyticsController` (`~1124`),
  `BotRuntimeContractService` (`~628`) и
  `NotificationRoutingService` (`~501`);
- главный UI debt теперь виден уже не как "несколько inline-блоков", а как
  два крупных surface-monoliths:
  `templates/settings/index.html` (`~24659` строк, много inline
  `onclick/onchange` и встроенных `<style>/<script>`) и
  `static/js/dialogs.js` (`~10426` строк);
- это не отменяет того, что `Phase 3` и `Phase 4` закрыты, но меняет
  practical focus: следующая архитектурная работа уже не про возврат к
  `DialogService` / `SettingsUpdateService`, а про controlled decomposition
  вторичных controller/runtime/UI boundaries.

Обновлённый practical focus после этого checkpoint:

1. `Phase 3` и `Phase 4` продолжать считать закрытыми и не открывать заново
   giant-split программу вокруг уже суженных `dialogs/settings` фасадов.
2. Отдельным UI-треком брать decomposition `settings/index.html` и
   `dialogs.js`: выносить inline handlers, fragment-ветки и page-runtime
   orchestration в статические модули и более узкие template fragments.
3. `Phase 5/6` вести дальше уже как runtime/integration-quality трек:
   усиливать explicit contract между panel и bot runtime, notification/read
   loops и соседние end-to-end consumer refresh сценарии.
4. В backend идти только по локальным bounded split для вторичных тяжёлых
   слоёв (`ChannelApiController`, `AnalyticsController`,
   `DialogWorkspaceTelemetryAnalyticsService`, `NotificationRoutingService`),
   если они продолжают расти, а не запускать новый общий monolith refactor.

Следующий проход уже можно декомпозировать не абстрактно, а по трём отдельным
трекам:

### Track A. Settings Page Shell Decomposition

Наблюдение:

- `settings/index.html` сейчас держит около `24659` строк;
- внутри него остаются как минимум `37` inline `onclick`, `2` inline
  `onchange`, один большой inline `<style>` и около `10` `<script>`-включений;
- practical risk там уже не в одном giant Spring controller, а в том, что
  page shell, fragment markup, initial bootstrap data и UI event wiring всё ещё
  смешаны в одном template surface.

Что логично делать:

1. Сначала вынести inline action-handlers в `data-*` + delegated listeners,
   не меняя визуальный контракт страницы.
2. Затем разрезать template на bounded fragments минимум вокруг:
   `locations`, `client statuses`, `dialog templates`, `channels/bots`,
   `auth/users` и соседних modal/sheet surfaces.
3. После этого увести page bootstrap из больших inline `<script th:inline>`
   блоков в отдельные runtime-модули с явным входным payload.

Стоп-условие:

- `settings/index.html` перестаёт быть основной точкой UI-coupling, даже если
  backend `settings` уже давно разрезан.

Что показал более глубокий срез:

- проблема там уже не только в markup-объёме: значимая часть runtime живёт
  прямо в самом template как giant inline script, а не во внешних модулях;
- внешние JS-файлы (`bot-settings.js` `~2493`, `auth-management.js` `~3478`,
  `input-settings.js` `~415`) не являются главным hotspot'ом сами по себе:
  основной coupling остаётся во встроенном script-блоке внутри
  `settings/index.html`;
- inline entry-points тоже подтверждают page-shell характер проблемы:
  `saveSettings()` вызывается из нескольких мест, `toggleDialogTemplateEditor()`
  используется как повторяющийся template runtime-hook, а `addChannel()`,
  `openAddLocationWizard()`, `runLocationsIikoSyncNow()`,
  `saveClientStatuses()` и template-add/remove handlers всё ещё приходят в
  DOM через inline `onclick`.

Внутренние bounded кластеры для следующего split:

1. `parameters / legal entities / partner contacts / IT catalog`
   как отдельный большой runtime-контур вокруг `/api/settings/parameters`,
   IT connections и equipment.
2. `client statuses / business styles`
   как отдельный visual/reference-data editor.
3. `locations tree / sync / wizard`
   как самостоятельный locations-runtime с edit tree, sync status и wizard flow.
4. `dialog templates / auto-close / SLA / workspace governance`
   как отдельный dialog-settings runtime cluster.
5. `channels / network routes / runtime status / editor`
   как отдельный channel-management cluster поверх `ChannelApiController`.
6. `reporting / manager bindings / modal shell`
   как отдельный administrative shell cluster.

Рекомендуемый порядок Track A уже внутри самого трека:

1. Сначала вынести inline handlers и page-level bootstrap в отдельный
   `settings-page-shell` runtime.
2. Затем разрезать giant inline script по шести bounded кластерам выше.
3. Только после этого выносить крупные modal sections в template fragments,
   чтобы fragment split не оставался завязанным на общий global-script хвост.

### Track B. Dialogs Runtime Module Split

Наблюдение:

- `dialogs.js` сейчас держит около `10426` строк;
- по содержанию там смешаны list/filter/runtime polling, details/history,
  workspace contract, quick actions, macro workflow, AI assistant/review,
  notifications refresh loop и media/reply surface;
- это уже не giant backend service, а giant browser runtime orchestrator.

Что логично делать:

1. Сначала выделить shared client/state boundary:
   API helpers, polling timers, route/open-state, refresh bus.
2. Затем разрезать `dialogs.js` минимум на bounded модули:
   `list+filters`, `details+history`, `workspace shell`,
   `quick actions`, `macro templates/workflow`, `AI ops/review`,
   `notifications refresh`.
3. Только после этого отдельно дочищать remaining consumer drift между
   `/api/dialogs`, `details`, `history`, `workspace` и bell loop.

Текущий зафиксированный runtime-focus внутри Track B:

- live regression corridor для `take -> categories -> reply -> follow-up ->
  details/workspace reread -> bell ack -> next follow-up` уже нужен как
  обязательный контракт, потому что именно там расходятся row `unreadCount`,
  `my_dialogs` bucket placement и panel bell unread semantics;
- backend-часть этой нормализации уже зафиксирована:
  `my_dialogs.unanswered` больше не держится на `waiting_operator` overlay и
  теперь соответствует только `unreadCount > 0`, а reread с
  `unreadCount = 0` стабильно переводит assigned dialog в `in_work`;
- bell boundary тоже уже расширен до mass-ack semantics:
  `POST /api/notifications/read-all` теперь live-прикрыт как отдельный
  consumer, который чистит только bell unread summary и не должен скрывать
  unread dialog из list/my_dialogs до реального reread dialog consumer'а;
- значит следующий точечный диалоговый пакет должен чистить уже не сам
  bucket split, а оставшуюся refresh-bus/bell coordination между
  list/details/workspace/notifications перед большим client-side split.

Стоп-условие:

- browser runtime по диалогам перестаёт зависеть от одного page-script файла
  как от фактического client-side monolith.

### Track C. Secondary Transport/Runtime Bounded Split

Наблюдение:

- следующая backend pressure-зона живёт уже не в `DialogService`, а во
  вторичных transport/orchestration слоях:
  `ChannelApiController` (`~1168` строк),
  `AnalyticsController` (`~1124`),
  `DialogWorkspaceTelemetryAnalyticsService` (`~1171`),
  `BotRuntimeContractService` (`~628`) и
  `NotificationRoutingService` (`~501`);
- особенно заметно, что `ChannelApiController` смешивает channel CRUD,
  credential CRUD, Telegram test/runtime diagnostics и channel notifications,
  а `AnalyticsController` держит и page rendering, и export, и rollout/context,
  и SLA/macro governance mutations.

Что логично делать:

1. `ChannelApiController` резать по transport responsibility:
   `channels CRUD`, `bot credentials`, `Telegram diagnostics/test-message`,
   `channel notifications`.
2. `AnalyticsController` резать по bounded use-case:
   `analytics page/export`, `workspace rollout governance`,
   `workspace context standardization`, `SLA governance`,
   `macro governance`.
3. `DialogWorkspaceTelemetryAnalyticsService` и соседний notifier/runtime слой
   резать только если они продолжают расти после UI/runtime hardening, а не
   открывать там большой рефакторинг заранее.

Стоп-условие:

- controller-слой снова становится thin transport wrapper'ом, а не новой
  точкой накопления post-refactor логики.

Рекомендуемый порядок следующего реального прохода:

1. Сначала `Track A`, потому что `settings/index.html` сейчас самый явный
   UI-coupling hotspot.
2. Затем `Track B`, потому что `dialogs.js` уже является client-side аналогом
   giant orchestration file.
3. Только потом `Track C`, если после UI split эти backend boundaries всё ещё
   будут оставаться самыми тяжёлыми практическими точками роста.
