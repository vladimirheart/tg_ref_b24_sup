# 2026-06-17 22:50:18 - settings locations iiko runtime split

## Промпты пользователя

- `давай следующий шаг`

## Что изменено

- добавлен новый bounded runtime-модуль `spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js` для subdomain `locations iiko server sources / sync status`;
- новый модуль забрал state, render/save/polling flow и глобальные entry-point'ы `prepareLocationsSettingsModal`, `resetLocationsSettingsModal`, `runLocationsIikoSyncNow`, `addLocationsIikoServerSource`, `updateLocationsIikoServerSource`, `removeLocationsIikoServerSource`, а также сериализаторы `serializeLocationsIikoServerSources` и `serializeLocationsIikoSyncSettings`;
- в `spring-panel/src/main/resources/templates/settings/index.html` подключён `settings-locations-iiko-runtime.js` и добавлен тонкий `window.SettingsLocationsIikoRuntime?.mount(...)` с initial payload для iiko-источников и sync-настроек;
- из inline runtime в `settings/index.html` удалён большой блок логики `locations iiko sources / sync`, при этом дерево локаций и location wizard оставлены на месте;
- `saveLocationsChanges` и общий `saveSettings` переведены на явные вызовы `window.serializeLocationsIikoServerSources()`, `window.serializeLocationsIikoSyncSettings()` и `window.markLocationsIikoServerSourcesSaved()`;
- блоки `publicform` и `questions_cfg` не затрагивались.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
- `rg -n "SettingsLocationsIikoRuntime|window\\.serializeLocationsIikoServerSources|window\\.serializeLocationsIikoSyncSettings|window\\.markLocationsIikoServerSourcesSaved" spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js spring-panel/src/main/resources/templates/settings/index.html`
- `rg -n "serializeLocationsIikoServerSources\\(|serializeLocationsIikoSyncSettings\\(|markLocationsIikoServerSourcesSaved\\(|prepareLocationsSettingsModal|resetLocationsSettingsModal|runLocationsIikoSyncNow|addLocationsIikoServerSource|updateLocationsIikoServerSource|removeLocationsIikoServerSource|updateLocationsIikoSyncSetting|renderLocationsIikoServerSourcesEditor|renderLocationsIikoSyncSettings|loadLocationsSyncStatus" spring-panel/src/main/resources/templates/settings/index.html`
- `git diff --check -- spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js spring-panel/src/main/resources/templates/settings/index.html`
