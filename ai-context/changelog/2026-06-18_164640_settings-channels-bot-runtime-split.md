# 2026-06-18 16:46:40 - settings channels bot runtime split

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- добавлен новый bounded runtime `spring-panel/src/main/resources/static/js/settings-channels-bot-runtime.js` для subdomain `channels / bot runtime controls`;
- в новый runtime вынесены status badges, массовое обновление статусов ботов, token visibility/copy, test-message flow, refresh bot info и start/stop команды для channel editor;
- в `spring-panel/src/main/resources/templates/settings/index.html` подключён `settings-channels-bot-runtime.js`, убраны inline-функции `updateTestRecipientState`, `setTestStatus`, `refreshAllBotRuntimeStatuses`, `refreshChannelBotInfo`, `startBotForChannel`, `startBot`, `stopBotForChannel`, `sendChannelTestMessage` и связанные bot-status helpers;
- `settings-channels-catalog-runtime` переведён на lazy callbacks к новому bot runtime, чтобы channels list мог по-прежнему запускать ботов и рефрешить статусы без возврата к giant inline-runtime.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-channels-bot-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-channels-bot-runtime.js`
- `rg -n "function updateTestRecipientState\(|function setTestStatus\(|function normalizeBotRuntimeStatus\(|function getBotRuntimeBadgeClass\(|function updateBotStatusLabel\(|function setChannelRowBotRuntimeStatus\(|async function refreshAllBotRuntimeStatuses\(|function updateBotControls\(|function updateBotRefreshControl\(|function getChannelEditorTokenValue\(|function updateChannelEditorToken\(|async function copyChannelToken\(|async function refreshBotStatus\(|async function refreshChannelBotInfo\(|async function startBotForChannel\(|async function startBot\(|async function stopBotForChannel\(|async function sendChannelTestMessage\(|function maskToken\(" spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-channels-bot-runtime.js`
