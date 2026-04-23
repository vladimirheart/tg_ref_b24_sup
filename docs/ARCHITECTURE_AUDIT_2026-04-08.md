# Архитектурный аудит проекта Iguana CRM
**Дата:** 8 апреля 2026  
**Статус:** Актуально, но в активной фазе исправления  
**Актуализация:** 9 апреля 2026 (см. `docs/ARCHITECTURE_AUDIT_VALIDATION_2026-04-09.md`)  
**Последняя актуализация:** 23 апреля 2026

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
  а теперь и lifecycle/audit/details slices, но remaining bounded contexts и
  legacy helper blocks всё ещё не закрыты полностью; при этом сам класс уже
  заметно уменьшился и сейчас находится примерно на уровне `5520` строк, то
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
- `DialogService` остаётся главным giant service и основным SRP-риском;
- `DialogWorkspaceService` уже заметно сужен, но всё ещё остаётся крупным
  сервисом-оркестратором;
- часть orchestration в `settings` уже разрезана, но remaining subdomains
  всё ещё требуют контроля.

**Важно:** `DialogApiController` больше не является главным transport-level
hotspot. Крупные controller-сценарии уже вынесены в отдельные controllers и
services, поэтому главный риск сместился в service layer.

**Решение:** Продолжать разрезать `DialogService` по bounded contexts. Важно,
что значимая часть работы уже сделана, поэтому ниже не просто wishlist, а
фактический каркас текущего split:

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
  │   ├─ DialogMacroGovernanceSupportService
  │   └─ DialogMacroGovernanceAuditService
  ├─ still to narrow:
  │   ├─ DialogWorkspaceService
  │   ├─ telemetry summary orchestration + compatibility bridge
  │   ├─ reply/message write-side flows
  │   ├─ AI/notification/escalation flows
  │   └─ mapper / assembly layer
  └─ then shrink compatibility delegates in DialogService itself
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
| Single Responsibility | ❌ НЕТ | Главный hotspot теперь `DialogService` и remaining giant services в `settings` |
| Don't Repeat Yourself | ❌ НЕТ | `SharedConfigService` и часть runtime contract still duplicated |
| SOLID Principles | ⚠️ Частично | Часть transport-layer нарушений снижена, но service boundaries ещё не доведены |
| Spring Best Practices | ⚠️ Частично | Улучшены bootstrap/runtime/test слои, но нужен единый error/API contract |

---

## 📈 Метрики качества

| Метрика | Текущее | Цель |
|---------|---------|------|
| Крупные controller hot spots | Сильно снижены | 0 giant controllers |
| Крупные service hot spots | Всё ещё есть, но главный риск сужен до `DialogService` и remaining settings/workspace slices | bounded services по доменам |
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
- [ ] Разрезать `DialogService` по bounded contexts
- [ ] Досузить `DialogWorkspaceService` и consumer-фасады вокруг него
- [ ] Добить remaining `settings` subdomains
- [ ] Расширить и стабилизировать safety net для следующих крупных рефакторингов
- [ ] Закрыть remaining runtime/notifier хвосты, которые ещё держатся на legacy-compatible фасадах

### Фаза 3: Следующий архитектурный уровень
- [ ] Унифицировать shared config/runtime contract между `spring-panel` и `java-bot`
- [ ] Довести DTO/API contract до системного правила
- [ ] Закрепить единый error contract и API governance

---

## 📁 Следующие шаги

1. Дожать service-level split `DialogService` и сузить remaining workspace/orchestration consumers
2. Добить remaining `settings` subdomains и persistence boundaries
3. Продолжить расширять `Phase 6` от targeted service/WebMvc tests к более широким integration-сценариям shared config/runtime и panel-bot orchestration boundary
4. После этого возвращаться к shared-config unification и DTO/error contract

**Автор исходного аудита:** GitHub Copilot  
**Статус:** Документ актуализирован под состояние кода на 23 апреля 2026
