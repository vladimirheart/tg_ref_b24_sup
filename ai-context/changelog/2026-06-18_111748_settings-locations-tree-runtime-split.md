# 2026-06-18 11:17:48 - settings locations tree runtime split

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- добавлен новый bounded runtime-модуль `spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js` для subdomain `locations / locations tree`;
- в новый модуль вынесены состояние дерева локаций, status/meta helpers, tree rendering, CRUD-операции и `saveLocationsChanges`, а наружу оставлены только runtime API и глобальные entry-point'ы `buildLocationsTree`, `addBusiness`, `saveLocationsChanges`;
- в `spring-panel/src/main/resources/templates/settings/index.html` подключён `settings-locations-tree-runtime.js`, а большой inline-блок `locations tree` заменён на тонкий `window.SettingsLocationsTreeRuntime?.mount(...)`;
- `settings location wizard`, `reporting / manager bindings` и общий `saveSettings` переведены с прямого `locationsState` на `window.SettingsLocationsTreeRuntime?.getState()` и соседние runtime-методы;
- блоки `publicform` и `questions_cfg` не затрагивались.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-location-wizard-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
- `rg -n "locationsState|function buildLocationsTree|function addBusiness|function updateBusiness|function updateType|function updateCity|function updateLocation|function removeLocation|function createBusinessNode|function createTypeNode|function createCityNode|function createLocationLeaf|const LOCATION_STATUS_OPTIONS = \\[|DEFAULT_LOCATION_STATUS" spring-panel/src/main/resources/templates/settings/index.html`
