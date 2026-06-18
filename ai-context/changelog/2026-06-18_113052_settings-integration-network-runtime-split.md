# 2026-06-18 11:30:52 - settings integration network runtime split

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- добавлен новый bounded runtime-модуль `spring-panel/src/main/resources/static/js/settings-integration-network-runtime.js` для subdomain `channels / integration network / route helpers`;
- в новый модуль вынесены route-normalization helpers, project/bots/channel network-route state, route summary/overview logic и сохранение сетевых маршрутов;
- в `spring-panel/src/main/resources/templates/settings/index.html` подключён `settings-integration-network-runtime.js`, а inline network-core блок заменён на `mount` и компактные alias-обёртки для оставшегося channel editor;
- локальные channel-editor helper’ы (`public form` field types, status/summary/support chat helpers, panel notification routing, delivery settings parser) оставлены в шаблоне и возвращены отдельно, чтобы текущий этап не затрагивал `publicform`;
- редактор профилей маршрутизации и probe-таблица пока остались inline, но уже переведены на runtime-helpers вместо старого shared блока.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-integration-network-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-integration-network-runtime.js`
- `rg -n "const integrationNetworkState =|function pluralizeRu\\(|function setChannelsManageText\\(|function describeChannelsManageRoute\\(|function updateChannelsManageOverview\\(|function normalizeChannelWorkingHours\\(|function slugifyIntegrationNetworkProfileName\\(|function normalizeNetworkProxyConfig\\(|function normalizeNetworkVpnConfig\\(|function getIntegrationNetworkProfileOptions\\(|function findIntegrationNetworkProfile\\(|function normalizeNetworkRoute\\(|function normalizeIntegrationNetworkConfig\\(|function toggleProxyCredentialFields\\(|function applyRouteToInputs\\(|function collectRouteFromInputs\\(|function describeRouteSummary\\(|function updateNetworkRouteSummary\\(|function toggleNetworkRouteFields\\(|function renderIntegrationNetworkSettings\\(|async function saveIntegrationNetworkSettings\\(" spring-panel/src/main/resources/templates/settings/index.html`
