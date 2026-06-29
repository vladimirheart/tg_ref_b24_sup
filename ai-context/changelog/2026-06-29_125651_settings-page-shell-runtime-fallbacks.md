# 2026-06-29 12:56:51 - settings page shell runtime fallbacks

## Промпты пользователя

- `давай следующий шаг по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-page-shell.js`
  получил runtime-namespace fallback map для callback resolution: после
  `SettingsPageCallbackRegistry`, но до legacy `window[...]`, shell теперь умеет
  резолвить часть callback-имён через явные `Settings*Runtime` objects;
- в этот слой включены callback'и для `locations` и `parameter-cluster`,
  включая `buildLocationsTree`, `addBusiness`, `loadParameters`,
  `renderPartnerContactsSettingsModal`, `renderLegalEntitiesSettingsModal`,
  `prepareParameterSettingsTrigger`, `preparePartnerContact*`,
  `prepareItConnectionAddSettingsModal`, `prepareItEquipmentAddSettingsModal`,
  `renderLocationsIiko*`, `updateLocationsIiko*`, `runLocationsIikoSyncNow` и
  связанные reset/remove callbacks;
- `ai-context/tasks/task-details/01-129.md` обновлён: в карточке зафиксирован
  новый слой `settings-page-shell` fallback'а на `Settings*Runtime` namespace
  contracts.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-page-shell.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-page-shell.js ai-context/tasks/task-details/01-129.md`
