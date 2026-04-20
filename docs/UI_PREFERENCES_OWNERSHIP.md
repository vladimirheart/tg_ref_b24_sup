# UI Preferences Ownership

Документ фиксирует ownership разных источников UI-состояния в `spring-panel`.

## Источники правды

### `settings.json`

Используется для shared project-level и integration-level настроек.

Что сюда относится:

- интеграции;
- глобальные настройки каналов и dialog runtime;
- каталоги и reference data;
- project-wide visual settings, если они реально общие для всех.

Что сюда не относится:

- персональные operator preferences;
- временное browser-only UI состояние;
- triage/view preferences конкретного оператора.

### `settings_parameters`

Используется для server-backed operator preferences и параметров, которые должны
жить в БД и переживать браузер/устройство.

Что сюда относится:

- `ui_preferences.v1`;
- `dialogsTriage` и похожие operator-scoped preferences;
- настраиваемые параметры каталога, живущие в settings domain.

### `localStorage`

Используется только как browser cache/runtime convenience слой.

Что сюда относится:

- временные client-side toggles, которые можно безопасно восстановить;
- краткоживущие browser-only состояния до server sync;
- fallback для unauthenticated или раннего bootstrap сценария.

Что сюда не относится:

- финальный источник правды для operator preferences;
- shared project settings;
- данные, от которых зависит серверная бизнес-логика.

## Нормальное направление данных

1. Для authenticated operator основным источником считается server-backed
   preference payload.
2. Browser runtime гидрируется из server bootstrap в `<head>`.
3. Изменения UI сначала нормализуются в общем runtime слое.
4. Затем синхронизируются обратно в server-backed storage.
5. `localStorage` остаётся только кэшем и fallback-слоем.

## Практические правила

- если preference влияет только на одного оператора и должна переживать
  устройство или сессию, она должна жить в `settings_parameters`;
- если state нужен только текущему браузеру и не критичен, допускается
  `localStorage`;
- если настройка меняет продукт глобально, она не должна храниться как
  operator preference;
- новые UI preferences должны проходить через общий runtime слой, а не через
  прямые raw обращения к `localStorage`.

## Текущий статус

- `theme`, `themePalette`, `sidebarPinned`, `uiDensityMode`,
  `sidebarNavOrder` уже нормализованы через общий UI preferences runtime;
- `dialogsTriage` уже переведён в server-backed storage с legacy fallback;
- remaining cleanup нужен только для точечных raw visual values, если они ещё
  всплывут вне tokens/theme semantics.
