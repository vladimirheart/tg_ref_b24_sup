# 2026-07-07 16:05:33 - Locations shared config self-heal after DB restore

## Пользовательский промпт

> с миграцией произошла беда. часть данных я восстановил, но часть всё ещё не работает. например нет в структуре локаций записей.

> в целом проверь задачи начиная с 15 мая, что касается именно баз данных

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/service/SettingsParameterService.java`
- `spring-panel/src/main/java/com/example/panel/service/LocationsSharedConfigRepairService.java`
- `spring-panel/src/test/java/com/example/panel/service/SettingsParameterSharedConfigIntegrationTest.java`
- `config/shared/locations.json`
- `temp-recovery/locations-json-backup-2026-07-07_160507/locations.json`

## Что сделано

- Проверен текущий runtime-срез после восстановления: в `panel_runtime.db` и `spring-panel/panel_runtime.db`
  найдены живые location-related параметры (`business`, `partner_type`, `city`, `department`), но
  `config/shared/locations.json` оказался пустым.
- Добавлен self-heal в `SettingsParameterService`: если `locations.json` отсутствует или содержит
  пустой `tree`, а в `settings_parameters` есть location-related записи, shared config
  автоматически пересобирается из БД.
- Добавлен `LocationsSharedConfigRepairService`, который запускает эту проверку на старте панели,
  чтобы DB-restore и path/migration-инциденты больше не оставляли пустой location fallback.
- Добавлен regression test на сценарий “БД живая, `locations.json` пустой”.
- Выполнен аварийный backfill текущего `config/shared/locations.json` из
  `spring-panel/panel_runtime.db`; прежний пустой файл сохранён в `temp-recovery`.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=SettingsParameterSharedConfigIntegrationTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`
- После backfill:
  - `tree_businesses=3`
  - `city_meta=49`
  - `location_meta=227`

## Примечания

- Инцидент оказался не в “пустой БД по локациям”: location-данные уже были в `settings_parameters`,
  но settings/runtime читает effective fallback через `config/shared/locations.json`, поэтому
  после частичного DB-restore система оставалась в пустом состоянии.
- В восстановленных данных осталась историческая аномалия верхнего уровня:
  одновременно присутствуют `СушиВесла` и `СушиВёсла`; это не вводилось текущим фиксoм, а
  пришло из существующих `settings_parameters`.
