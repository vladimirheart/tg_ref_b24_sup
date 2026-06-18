# 2026-06-18 11:03:06 - settings location wizard runtime split

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- добавлен новый bounded runtime-модуль `spring-panel/src/main/resources/static/js/settings-location-wizard-runtime.js` для subdomain `locations / location wizard`;
- новый модуль забрал state, step-navigation, form hydration и глобальные entry-point'ы `initLocationWizard`, `resetLocationWizardSettingsModal`, `prepareLocationWizardSettingsTrigger`, которые нужны `settings-page-shell`;
- в `spring-panel/src/main/resources/templates/settings/index.html` подключён `settings-location-wizard-runtime.js` и добавлен тонкий `window.SettingsLocationWizardRuntime?.mount(...)` с передачей только нужных зависимостей: `parameterData`, `locationsState`, `CITY_OPTIONS`, `loadParameters`, `buildLocationsTree`, `setStatus`, `writeNodeMeta`, `makeCityMetaKey`, `makeLocationMetaKey`, `sortArrayAlphabetically`, `DEFAULT_LOCATION_STATUS`;
- из inline runtime в `settings/index.html` удалён большой блок логики `location wizard`, при этом само дерево локаций и locations iiko runtime остались на месте;
- блоки `publicform` и `questions_cfg` не затрагивались.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-location-wizard-runtime.js`
- `rg -n "SettingsLocationWizardRuntime|window\\.initLocationWizard|window\\.resetLocationWizardSettingsModal|window\\.prepareLocationWizardSettingsTrigger|mount\\(" spring-panel/src/main/resources/static/js/settings-location-wizard-runtime.js spring-panel/src/main/resources/templates/settings/index.html`
- `rg -n "const TYPE_OPTIONS|locationWizardModalEl|locationWizardInitialised|locationWizardStep|locationWizardState|wizardSteps|wizardSummary|wizardBusinessSelect|wizardTypeSelect|wizardCountrySelect|wizardPartnerTypeSelect|wizardCitySelect|wizardDepartmentSelect|wizardPrevBtn|wizardNextBtn|wizardFinishBtn|function uniqueSorted|function getBusinessOptions|function getCityOptions|function getCountryOptions|function getPartnerTypeOptions|function getDepartmentOptions|function clearInvalid|function markInvalid|function fillSelect|function populateWizardOptions|function resetLocationWizardState|function updateWizardSummary|function showLocationWizardStep|function handleLocationWizardPrev|function handleLocationWizardNext|function addLocationEntryFromWizard|function handleLocationWizardFinish|function initLocationWizard|window\\.resetLocationWizardSettingsModal|window\\.prepareLocationWizardSettingsTrigger" spring-panel/src/main/resources/templates/settings/index.html`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-location-wizard-runtime.js spring-panel/src/main/resources/templates/settings/index.html`
