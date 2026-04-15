# Architecture And UI Refactoring Roadmap

Дата: `2026-04-15`

## Цель

Снизить архитектурный риск в `spring-panel` и привести UI runtime к управляемой
модели, где:

- тема и page presets имеют единый источник подключения;
- страницы не дублируют bootstrap-логику;
- крупные домены `dialogs` и `settings` режутся по bounded context, а не
  продолжают расти как `god-service` и `god-controller`.

## Текущее состояние

Основные проблемы, зафиксированные в аудите:

- слишком большие классы и контроллеры: `DialogService`, `DialogApiController`,
  `SettingsBridgeController`;
- смешение `JPA`, raw `JdbcTemplate`, JSON-конфигов и `localStorage` без единой
  модели источников правды;
- позднее применение темы через sidebar-fragment;
- ограниченный охват `ui-config` только частью страниц;
- крупные inline `style/script` блоки в `settings`, `dashboard`, `dialogs`;
- слабое тестовое покрытие для безопасного рефакторинга.

## Фазы

### Phase 1. UI Runtime Foundation

Цель:

- централизовать раннюю загрузку темы;
- расширить `ui-config` на остальные разделы;
- убрать дублирование head-bootstrap логики по страницам.

Статус:

- выполнено в рамках текущего этапа.

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

Что сделать:

- ввести `UiPreferenceService`/`UiPreferenceController` для операторских UI
  предпочтений;
- зафиксировать перечень допустимых browser-prefs:
  `theme`, `palette`, `density`, `sidebar pin/order`, `dialogs view state`;
- убрать raw color-значения из runtime-конфигов там, где они должны зависеть от
  design tokens;
- описать ownership для `settings.json`, `localStorage`, DB-параметров.

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

### Phase 5. Process And Runtime Boundary

Цель:

- уменьшить coupling между `spring-panel` и runtime ботов.

Что сделать:

- уйти от `spring-boot:run` в `BotProcessService`;
- запускать собранные артефакты или отдельный launcher;
- формализовать contract между panel и `java-bot`;
- отдельно покрыть readiness/status contract тестами.

### Phase 6. Test Safety Net

Цель:

- получить страховку на рефакторинг.

Минимум:

- smoke tests для `theme/ui bootstrap`;
- webmvc tests для основных settings/dialogs endpoints;
- integration tests для shared config и DB path resolution;
- runtime contract tests для bot process orchestration.

## Порядок выполнения

1. Закончить foundation и убедиться, что страницы стабильно применяют тему и
   page presets.
2. Вынести UI preferences в отдельный поток хранения.
3. Резать `dialogs` по read-only сценариям.
4. Резать `settings`.
5. Только после этого трогать bot runtime boundary.

## Что не делать сейчас

- не начинать большой перенос всех SQL сценариев на JPA;
- не переписывать одновременно `dialogs`, `settings` и `dashboard`;
- не смешивать визуальный редизайн с архитектурным рефакторингом сервиса.
