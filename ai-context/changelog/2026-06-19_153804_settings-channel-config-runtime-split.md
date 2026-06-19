# 2026-06-19 15:38:04 - settings channel config runtime split

## Промпты пользователя

- `давай следующий шаг по задаче`

## Что изменено

- добавлен новый bounded runtime `spring-panel/src/main/resources/static/js/settings-channel-config-runtime.js` для subdomain `channels / config parsing helpers`;
- в новый runtime вынесены общие helper'ы `defaultChannelPanelNotifications`, `parseChannelNotificationList`, `serializeChannelNotificationList`, `parseDeliverySettings`, `parsePlatformConfig` и `parseQuestionsCfg`;
- в `spring-panel/src/main/resources/templates/settings/index.html` удалены дублирующие inline parsing/config helpers и заменены на alias к `settingsChannelConfig`;
- `spring-panel/src/main/resources/static/js/settings-channels-catalog-runtime.js` переведён на injected `parsePlatformConfig`, чтобы catalog/runtime нормализация больше не тащила локальный duplicate parser;
- в `ai-context/tasks/task-details/01-129.md` обновлён прогресс задачи: добавлен выполненный пакет `settings-channel-config-runtime.js`.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-channel-config-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-channels-catalog-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-channel-config-runtime.js spring-panel/src/main/resources/static/js/settings-channels-catalog-runtime.js ai-context/tasks/task-details/01-129.md`
- `rg -n "function parseDeliverySettings|function defaultChannelPanelNotifications|function parseChannelNotificationList|function serializeChannelNotificationList|function parseQuestionsCfg|function parsePlatformConfig" spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-channel-config-runtime.js spring-panel/src/main/resources/static/js/settings-channels-catalog-runtime.js`
