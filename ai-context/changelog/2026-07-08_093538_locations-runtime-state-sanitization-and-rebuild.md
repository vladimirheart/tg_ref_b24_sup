# 2026-07-08 09:35:38 - Locations runtime state sanitization and rebuild

## Пользовательский промпт

> в телеграм боте не вижу выбранный профиль, хотя в списке ботов отображается что профиль выбран. и самих профилей тоже не наблюдаю.

> в списке локаций не вижу ни одной на вкладке "структура"

## Затронутые файлы

- `spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js`
- `spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
- `spring-panel/src/main/resources/static/js/settings-save-runtime.js`
- `spring-panel/src/main/resources/static/js/settings-page-bootstrap-runtime.js`
- `spring-panel/src/main/java/com/example/panel/service/SettingsLocationsUpdateService.java`
- `spring-panel/src/test/java/com/example/panel/service/SettingsUpdateSharedConfigIntegrationTest.java`
- `config/shared/locations.json`
- `temp-recovery/locations-rebuild-backup-2026-07-08_093243/*`

## Что сделано

- Исправлен lazy-load сетевых профилей в настройках каналов:
  - `settings-channels-shell-runtime.js` больше не считает пустой `integrationNetwork` / пустой массив профилей валидным initial payload;
  - при отсутствии реальных стартовых данных секция корректно дотягивает `channels` page-data с backend.
- Исправлен save-path структуры локаций:
  - `settings-locations-tree-runtime.js` теперь сериализует только канонические поля `tree`, `statuses`, `city_meta`, `location_meta`;
  - runtime-флаги `locationsLoaded` и `locationsLoadingPromise` больше не попадают в `/settings`.
- Исправлен глобальный save settings flow:
  - `settings-save-runtime.js` больше не отправляет `locations` вообще, если дерево ещё не было загружено;
  - `settings-page-bootstrap-runtime.js` прокидывает явный признак `areLocationsLoaded`.
- Добавлена backend-защита:
  - `SettingsLocationsUpdateService` принимает только каноническую форму `locations`-payload;
  - runtime/технические поля отбрасываются перед записью в `config/shared/locations.json`.
- Восстановлен `config/shared/locations.json` из живых `settings_parameters`:
  - rebuilt tree содержит `2` бизнеса;
  - rebuilt meta содержит `49` city-meta и `227` location-meta записей.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=SettingsUpdateSharedConfigIntegrationTest,IntegrationNetworkServiceTest,LocationsIikoServerSourceSettingsServiceTest,IikoDepartmentLocationCatalogServiceTest" test`
- Post-check:
  - `config/shared/locations.json` содержит только ключи `tree`, `statuses`, `city_meta`, `location_meta`
  - `config/shared/locations.json` больше не содержит `locationsLoaded` / `locationsLoadingPromise`
  - rebuilt payload содержит бизнесы `БлинБери` и `СушиВёсла`

## Примечания

- Перед rebuild сохранён backup сломанного `locations.json` в
  `temp-recovery/locations-rebuild-backup-2026-07-08_093243/`.
