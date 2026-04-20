# Architecture And UI Refactoring Roadmap

Дата старта: `2026-04-15`
Обновлено: `2026-04-20`

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
  backward-compatible fallback к legacy shared JSON.

Что остаётся:

- формально зафиксировать ownership для `settings.json`, `localStorage`,
  `settings_parameters` и server-backed UI prefs;
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

Что остаётся:

- service-level split `DialogService` на list/workspace/history/SLA/AI и related
  mapping layers;
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
- добавлены тесты на выбор launcher plan, runtime contract payload и сохранён
  readiness probe контракт.

Что остаётся:

- формализовать contract между panel и `java-bot` на уровне launcher inputs,
  env contract и status/readiness expectations;
- определить preferred production path: prebuilt jars, launcher scripts или
  отдельный supervisor/service;
- распространить explicit jar contract на целевые окружения и зафиксировать
  рекомендуемые пути/имена артефактов для production/deploy сценариев;
- расширить runtime tests от launcher plan до end-to-end process contract.

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
- smoke-проверка раннего `ui-head` bootstrap на странице диалогов;
- legacy WebMvc test-слой частично синхронизирован с новой controller
  структурой.

Что остаётся:

- smoke tests для `theme/ui bootstrap`;
- отдельные WebMvc tests под новые dialog/settings controllers;
- integration tests для shared config path/env resolution;
- более широкий runtime contract test для launcher strategy и bot lifecycle.

## Где мы сейчас

Если смотреть по смыслу, а не по номерам:

1. `Phase 1` завершён.
2. `Phase 2` стабилизирован и требует скорее нормализации ownership, чем
   срочной архитектурной ломки.
3. `Phase 3` завершён на controller boundary и ждёт service-level split
   `DialogService`.
4. `Phase 4` выполнен по самым рискованным giant flows и требует добивки
   remaining subdomains.
5. `Phase 5` уже начат в коде.
6. `Phase 6` усилен адресными runtime/UI тестами, но пока не превращён в
   полноценную safety net.

## Следующий Фокус

Наиболее логичный следующий шаг после текущего состояния:

1. Дожать `Phase 5` до production-ready deployment recipe и более полного
   lifecycle contract для bot runtime.
2. Параллельно расширять `Phase 6`, чтобы следующие рефакторинги шли под
   лучшей страховкой.
3. После этого вернуться к service-level split `DialogService`, который
   остаётся самым большим архитектурным риском.

## Порядок выполнения

Актуальный порядок после уже выполненных этапов:

1. Довести `Phase 5` от launcher strategy до более явного runtime contract.
2. Расширить `Phase 6`, чтобы новые refactor-проходы не шли почти без тестов.
3. Вернуться к `dialogs` и резать `DialogService` по service-level bounded
   contexts.
4. Добить remaining `settings` subdomains, если они ещё остаются в общих слоях.

## Что не делать сейчас

- не начинать большой перенос всех SQL сценариев на JPA;
- не переписывать одновременно `DialogService`, `settings` subdomains и bot
  runtime contract;
- не смешивать визуальный редизайн с архитектурным рефакторингом сервиса.
