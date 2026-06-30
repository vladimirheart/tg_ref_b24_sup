# 2026-06-30 16:02:00 — settings runtime wrapper api narrowing

## user prompt

> бери в работу следующие шаги

## what changed

- в нескольких `settings-*runtime.js` файлах сужен наружный namespace API:
  удалены wrapper-forwarders на `window.__settings*Runtime` там, где после
  предыдущих cleanup-пакетов shell и user-action flows уже используют
  `SettingsPageCallbackRegistry` или прямые mounted runtime instances;
- наружу сохранены только `mount` и реально живые bootstrap/public API точки:
  `initClientStatuses`, `initBusinessStylesEditor`, `initLocationWizard`,
  `initChannelsManagement`, `initReporting`, `loadParameters`,
  `buildLocationsTree`, `renderLocationsIikoServerSourcesEditor`,
  `renderLocationsIikoSyncSettings`, `loadLocationsSyncStatus`;
- runtime namespaces для `parameters`, `network profiles`, `partner contacts`,
  `legal entities`, `channel editor/catalog`, `integration network`,
  `it connections/equipment` и части `admin/locations` теперь меньше похожи на
  дублирующий compatibility layer и больше на узкий mount/bootstrap contract;
- в `ai-context/tasks/task-details/01-129.md` обновлён статус: этот wrapper
  narrowing уже выполнен, а remaining scope смещён к финальному решению по
  payload/data contract в `settings/index.html`.

## verification

- `node --check spring-panel/src/main/resources/static/js/settings-appearance-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-parameters-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-network-profiles-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-partner-contacts-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-legal-entities-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-location-wizard-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-admin-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-reporting-manager-bindings.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channels-catalog-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channel-editor-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-integration-network-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-it-connections-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-it-equipment-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-parameters-shell-runtime.js`
