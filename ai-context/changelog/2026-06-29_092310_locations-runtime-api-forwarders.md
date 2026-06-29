# 2026-06-29 09:23:10 - locations runtime api forwarders

## Промпты пользователя

- `давай следующий более широкий шаг по задаче 01-129`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
  переведён на более явный dependency injection для bounded runtime:
  popup-уведомления теперь могут приходить через `showPopup` в mount options,
  а fallback для `locations iiko` save helpers идёт через
  `SettingsLocationsIikoRuntime`, а не через россыпь legacy globals;
- `spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
  получил расширенный namespace API на `SettingsLocationsIikoRuntime`:
  добавлены forwarder-методы для serialize/render/update/save helpers поверх
  текущего runtime instance;
- `spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
  также получил forwarder-методы `addBusiness()` и `saveLocationsChanges()` на
  `SettingsLocationsTreeRuntime`, чтобы locations subdomain сильнее опирался на
  явный runtime-object API;
- `spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
  теперь прокидывает `showPopup` в `settings-locations-tree-runtime`, замыкая
  locations subdomain на injected page-level bridge вместо прямой зависимости от
  `window.showPopup`;
- `ai-context/tasks/task-details/01-129.md` обновлён: в карточке зафиксирован
  новый слой locations runtime API/notify cleanup.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js ai-context/tasks/task-details/01-129.md`
