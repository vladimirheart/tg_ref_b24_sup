# 2026-06-18 16:11:14 - settings integration network profiles runtime split

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- в `spring-panel/src/main/resources/static/js/settings-integration-network-runtime.js` завершён bounded runtime для subdomain `channels / integration network / profiles`: добавлены table rendering, probe-state, profile editor form handlers, save/delete flows и modal reset hooks;
- в runtime добавлены `setProfilesData` и `requestSettingsModalClose`, чтобы модуль сам владел mutable state профилей и закрытием editor modal без возврата к giant inline-обработчикам;
- в `spring-panel/src/main/resources/templates/settings/index.html` удалён дублирующий inline-блок `integration network profiles` и оставлены только компактные alias-обёртки к runtime API;
- в `initChannelsManagement()` события таблицы профилей, bulk probe, submit/delete editor и modal reset переведены на методы `settingsIntegrationNetwork`, без затрагивания `publicform`-ветки.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-integration-network-runtime.js`
- `git diff --check -- spring-panel/src/main/resources/templates/settings/index.html spring-panel/src/main/resources/static/js/settings-integration-network-runtime.js`
- `rg -n "integrationNetworkProfileEditingIndex|integrationNetworkProfileProbeState|function normalizeIntegrationProfileProbeItem|function describeIntegrationProfileProbe|async function probeIntegrationNetworkProfiles|function renderIntegrationNetworkProfilesTable|function fillIntegrationNetworkProfileForm|function prepareIntegrationNetworkProfileEditor|function toggleIntegrationNetworkProfileModeFields|function collectIntegrationNetworkProfilePayload|function cleanupIntegrationNetworkProfileProbeState|async function saveIntegrationNetworkProfiles|function renderIntegrationNetworkProfileSelectors|function describeIntegrationNetworkProfile|function updateNetworkRouteProfilePreview" spring-panel/src/main/resources/templates/settings/index.html`
