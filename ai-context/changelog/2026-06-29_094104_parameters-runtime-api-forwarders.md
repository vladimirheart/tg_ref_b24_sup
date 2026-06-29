# 2026-06-29 09:41:04 - parameters runtime api forwarders

## Промпты пользователя

- `давай следующий более широкий шаг по задаче 01-129`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js`
  получил расширенный namespace API на `SettingsParametersShellRuntime`:
  добавлены forwarder-методы для `getParameterData`, `isParametersLoaded`,
  `loadParameters`, `render*` и `syncParameterData`;
- `spring-panel/src/main/resources/static/js/settings-parameters-runtime.js`
  получил расширенный namespace API на `SettingsParametersRuntime`, а fallback
  для `renderPartnerContacts`, `renderLegalEntities`, `renderItConnectionsTable`
  и `syncParameterData` теперь может идти через `Settings*Runtime` objects;
- `spring-panel/src/main/resources/static/js/settings-partner-contacts-runtime.js`,
  `settings-legal-entities-runtime.js`, `settings-it-connections-runtime.js` и
  `settings-it-equipment-runtime.js` получили явные namespace API на своих
  `Settings*Runtime` объектах вместо опоры только на legacy global wrappers;
- внутри `settings-partner-contacts-runtime.js`,
  `settings-legal-entities-runtime.js` и `settings-it-connections-runtime.js`
  добавлены namespace fallback'и на `SettingsParametersShellRuntime` и соседние
  runtime-объекты для `sync/render/current modal/equipment table` связок;
- `ai-context/tasks/task-details/01-129.md` обновлён: в карточке задачи
  зафиксировано расширение runtime-object API и namespace fallback для
  parameter-cluster.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-parameters-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-legal-entities-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-it-connections-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-it-equipment-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-partner-contacts-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-parameters-runtime.js spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js spring-panel/src/main/resources/static/js/settings-legal-entities-runtime.js spring-panel/src/main/resources/static/js/settings-it-connections-runtime.js spring-panel/src/main/resources/static/js/settings-it-equipment-runtime.js spring-panel/src/main/resources/static/js/settings-partner-contacts-runtime.js ai-context/tasks/task-details/01-129.md`
