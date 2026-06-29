# 2026-06-29 15:55:48 - settings runtime helper fallback cleanup

## Промпты пользователя

- `давай следующий более широкий шаг по задаче`

## Что изменено

- `spring-panel/src/main/resources/static/js/settings-appearance-runtime.js`,
  `settings-locations-iiko-runtime.js` и
  `settings-location-wizard-runtime.js` переведены с прямых fallback'ов к
  `window.showPopup`/`window.escapeHtml` на injected helpers из bootstrap и
  локальные safe fallback'и;
- `spring-panel/src/main/resources/static/js/settings-integration-network-runtime.js`,
  `settings-channels-bot-runtime.js` и
  `settings-channels-shell-runtime.js` очищены от прямой зависимости на
  `window.getCookieValue`/`window.showPopup`: XSRF и popup helpers теперь
  проходят через shell/runtime contracts;
- `spring-panel/src/main/resources/static/js/settings-channels-catalog-runtime.js`,
  `settings-reporting-manager-bindings.js`,
  `settings-locations-tree-runtime.js` и `bot-settings.js` больше не используют
  прямые helper-fallback'и к `window.escapeHtml`/`window.showPopup` внутри
  settings-сценариев;
- `spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
  расширен недостающими helper-injections для `appearance`, `locations-iiko` и
  `location wizard`;
- `ai-context/tasks/task-details/01-129.md` обновлён: в задаче зафиксировано,
  что helper-fallback cleanup внутри settings runtime уже добит, а remaining
  scope сместился к `settings-page-init-runtime.js`, cross-runtime contracts и
  возможному финальному payload cleanup.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-appearance-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channels-catalog-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-integration-network-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-reporting-manager-bindings.js`
- `node --check spring-panel/src/main/resources/static/js/settings-location-wizard-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channels-bot-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/bot-settings.js`
