# 2026-07-07 16:29:43 - Normalize locations business alias

## Пользовательский промпт

> делай

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/service/SettingsParameterService.java`
- `spring-panel/src/main/java/com/example/panel/service/LocationsSharedConfigRepairService.java`
- `spring-panel/src/test/java/com/example/panel/service/SettingsParameterSharedConfigIntegrationTest.java`
- `spring-panel/panel_runtime.db`
- `panel_runtime.db`
- `config/shared/locations.json`
- `temp-recovery/locations-business-alias-backup-2026-07-07_162903/*`

## Что сделано

- Добавлена канонизация business alias для location-контура:
  `СушиВесла` -> `СушиВёсла`.
- Нормализация применяется:
  - при создании и обновлении `settings_parameters`;
  - при пересборке `locations.json` из параметров;
  - при гидрации параметров из location payload;
  - на старте панели перед восстановлением shared locations config.
- Добавлен startup repair, который умеет:
  - переписать legacy alias в `extra_json.dependencies.business`, если он встретится;
  - погасить дублирующую alias-запись `business`, если каноническая уже существует;
  - пересобрать `config/shared/locations.json` после нормализации.
- В текущих runtime-данных alias `СушиВесла` оказался сиротской `business`-записью без активных
  `city/department` ссылок; она soft-delete'нута в обеих runtime-БД.
- `config/shared/locations.json` пересобран после нормализации и теперь содержит только
  `БлинБери` и `СушиВёсла` как верхнеуровневые бизнесы.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=SettingsParameterSharedConfigIntegrationTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`
- Пост-проверка runtime-данных:
  - в `spring-panel/panel_runtime.db` и `panel_runtime.db` активных `business='СушиВесла'` больше нет;
  - активных `extra_json`-ссылок на `СушиВесла` больше нет;
  - `config/shared/locations.json` больше не содержит top-level `СушиВесла`.

## Примечания

- Для безопасности перед data-fix сохранены аварийные копии root/module runtime-БД и прежнего
  `locations.json` в `temp-recovery/locations-business-alias-backup-2026-07-07_162903/`.
