# 2026-06-29 14:41:42 - settings locations iiko saveflow global cleanup

## Промпты пользователя

- `давай следующий шаг по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
  очищен от последних plain-global save-flow hooks:
  `serializeLocationsIikoServerSources`,
  `serializeLocationsIikoSyncSettings` и
  `markLocationsIikoServerSourcesSaved` больше не публикуются в `window.*`;
- save-flow orchestration уже использует injected hooks из
  `settings-page-bootstrap-runtime.js` и fallback на
  `SettingsLocationsIikoRuntime`, поэтому plain-global мост для этих методов
  больше не нужен;
- отдельной проверкой подтверждено, что в settings runtime после этого пакета
  из non-namespace plain globals остаются уже в основном только
  `BOT_SETTINGS_INITIAL`, `BOT_PRESET_DEFINITIONS` и
  `requestSettingsModalClose`, а не старые page-shell/save callback exports;
- `ai-context/tasks/task-details/01-129.md` обновлён: зафиксирован переход к
  финальной стадии legacy-global cleanup.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-29_144142_settings-locations-iiko-saveflow-global-cleanup.md`
