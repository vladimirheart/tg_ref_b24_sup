# 2026-06-29 17:33:05 - settings cross runtime fallback cleanup

## Промпты пользователя

- `давай следующий шаг по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-parameters-runtime.js`,
  `settings-it-connections-runtime.js`,
  `settings-legal-entities-runtime.js` и
  `settings-partner-contacts-runtime.js` больше не ходят в соседние
  `window.Settings*Runtime` fallback'и для sync/render-сценариев: теперь эти
  leaf runtime используют только injected callbacks от shell;
- `spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
  очищен от прямого fallback'а к `SettingsLocationsIikoRuntime` в save-flow:
  serialization/saved-marking для iiko state теперь идут только через injected
  bootstrap contracts;
- `ai-context/tasks/task-details/01-129.md` обновлён: в задаче зафиксировано,
  что следующий слой cross-runtime fallback'ов уже снят и remaining scope ещё
  сильнее сместился к верхним namespace-level mount contracts и payload cleanup.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-parameters-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-it-connections-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-legal-entities-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-partner-contacts-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-parameters-runtime.js spring-panel/src/main/resources/static/js/settings-it-connections-runtime.js spring-panel/src/main/resources/static/js/settings-legal-entities-runtime.js spring-panel/src/main/resources/static/js/settings-partner-contacts-runtime.js spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js ai-context/tasks/task-details/01-129.md ai-context/changelog/2026-06-29_173305_settings-cross-runtime-fallback-cleanup.md`
