# 2026-06-30 22:24:47 - settings payload contract cleanup

- Файлы:
  `spring-panel/src/main/java/com/example/panel/controller/ManagementController.java`,
  `spring-panel/src/main/java/com/example/panel/controller/SettingsApiController.java`,
  `spring-panel/src/main/java/com/example/panel/service/SettingsPageDataService.java`,
  `spring-panel/src/main/resources/templates/settings/index.html`,
  `spring-panel/src/main/resources/static/js/settings-runtime-access.js`,
  `spring-panel/src/main/resources/static/js/settings-page-shell.js`,
  `spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`,
  `spring-panel/src/main/resources/static/js/settings-network-profiles-runtime.js`,
  `spring-panel/src/main/resources/static/js/settings-reporting-manager-bindings.js`,
  `spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`,
  `spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`,
  `spring-panel/src/main/resources/static/js/settings-integration-network-runtime.js`,
  `spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js`,
  `ai-context/tasks/task-list.md`,
  `ai-context/tasks/task-details/01-135.md`
- Промпт пользователя:
```text
Возбми в работу задачу 01-135
```
- Что сделано:
  `settingsPageInitPayload` в `settings/index.html` сужен до реально нужного
  page-boot контракта: из initial HTML убраны `channels.integrationNetwork`,
  `channels.integrationNetworkProfiles`, `parameters.networkProfiles`,
  `parameters.contractUsageData`, `appearance.statusUsage`,
  `admin.managerLocationBindings`, `locations.tree`,
  `locations.iikoServerSources` и `locations.iikoSyncSettings`.
- Что сделано:
  Добавлен backend section-endpoint `/api/settings/page-data/{section}` и
  `SettingsPageDataService`, чтобы `locations`, `channels`, `parameters` и
  `admin` догружались по требованию, а не вшивались в server-rendered payload.
- Что сделано:
  Settings runtime-модули переведены на lazy section fetch с кэшированием через
  `SettingsRuntimeAccess`; отдельно убран ранний bootstrap модальных секций
  `channels` и `itConnections`, а в `settings-channels-shell-runtime.js`
  поправлена передача `initialIntegrationNetwork`.
- Проверка:
  выполнен `.\mvnw.cmd -q -DskipTests compile` в `spring-panel`, результат —
  без ошибок.
- Проверка:
  выполнена синтаксическая проверка изменённых JS-файлов через `node` +
  `new Function(...)`, все изменённые runtime-файлы успешно распарсились.
