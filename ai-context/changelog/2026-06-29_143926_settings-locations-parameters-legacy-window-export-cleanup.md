# 2026-06-29 14:39:26 - settings locations parameters legacy window export cleanup

## Промпты пользователя

- `давай следующий шаг по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
  очищен от legacy `window.*` compatibility exports для
  `buildLocationsTree`, `addBusiness` и `saveLocationsChanges`; registry теперь
  публикует runtime-методы напрямую;
- `spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
  очищен от legacy `window.*` exports для page-shell/modal callbacks
  (`render*`, `update*`, `loadLocationsSyncStatus`, modal lifecycle,
  add/remove source, run sync), но сохранены save-flow hooks
  `serializeLocationsIikoServerSources`,
  `serializeLocationsIikoSyncSettings` и
  `markLocationsIikoServerSourcesSaved`;
- `spring-panel/src/main/resources/static/js/settings-parameters-runtime.js`,
  `settings-partner-contacts-runtime.js`,
  `settings-legal-entities-runtime.js`,
  `settings-it-connections-runtime.js` и
  `settings-it-equipment-runtime.js` переведены с global wrappers на прямую
  публикацию runtime-методов в `SettingsPageCallbackRegistry`;
- `ai-context/tasks/task-details/01-129.md` обновлён: зафиксирован новый
  cleanup-пакет и уточнён оставшийся фронт работ по задаче.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-parameters-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-partner-contacts-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-legal-entities-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-it-connections-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-it-equipment-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js spring-panel/src/main/resources/static/js/settings-parameters-runtime.js spring-panel/src/main/resources/static/js/settings-partner-contacts-runtime.js spring-panel/src/main/resources/static/js/settings-legal-entities-runtime.js spring-panel/src/main/resources/static/js/settings-it-connections-runtime.js spring-panel/src/main/resources/static/js/settings-it-equipment-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-29_143926_settings-locations-parameters-legacy-window-export-cleanup.md`
