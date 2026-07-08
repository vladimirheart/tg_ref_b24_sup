# 2026-07-08 09:00:22 - Locations iiko save fix and Telegram route profile migration

## Пользовательский промпт

> на странице настроек в структуре локации пытаюсь добавить источник и сохранить его. система говорит что сохранено, но при обновлении страницы источник пропадает. что примечательно: при добавлении источника и попытки синхронизации, не нажимая сохранить, система говорит что не удалось получить токен. проверь все-ли настройки корректны.

> дополнительно: телеграм бот сейчас ходит через прокси, и как я понимаю, маршрут зашит в код, котя он должен быть отдельным профилем - перенеси в профиль и измени настройку

## Затронутые файлы

- `spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
- `spring-panel/src/main/java/com/example/panel/service/LocationsIikoServerSourceSettingsService.java`
- `spring-panel/src/test/java/com/example/panel/service/LocationsIikoServerSourceSettingsServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/SettingsUpdateSharedConfigIntegrationTest.java`
- `config/shared/settings.json`
- `panel_runtime.db`
- `spring-panel/panel_runtime.db`
- `java-bot/panel_runtime.db`
- `temp-recovery/routing-migration-backup-2026-07-08_085737/*`

## Что сделано

- Исправлен фронтовый баг в `settings-locations-iiko-runtime.js`:
  - runtime больше не считает пустые `[]` / `{}` признаком “данные уже загружены”;
  - после refresh секция `locations` снова читает актуальные данные через `page-data`, поэтому сохранённые iiko-источники не пропадают из UI.
- Исправлена нормализация `base_url` для iiko-источников:
  - backend теперь автоматически срезает legacy-суффикс `/resto`;
  - это убирает ошибку вида `.../resto/resto/api/auth` при запросе access token.
- Добавлены regression tests:
  - на нормализацию `https://host/resto/ -> https://host`;
  - на сквозное сохранение `locations_iiko_server_sources` и `locations_iiko_sync` через `SettingsUpdateService`.
- Исправлены рабочие настройки:
  - в `config/shared/settings.json` для текущего источника `СушиВёсла` `base_url` переведён на `https://gk-sv.iiko.it`;
  - создан профиль маршрутизации `telegram-ftl-dev-ru-mirror` для `telegram.ftl-dev.ru`.
- Перенесён текущий Telegram runtime route из channel `platform_config.base_url` в профиль маршрутизации:
  - канал `id=1` в `panel_runtime.db`, `spring-panel/panel_runtime.db` и `java-bot/panel_runtime.db`
    теперь использует `delivery_settings.network_route.mode=profile`;
  - `profile_id/profile_ids` указывают на `telegram-ftl-dev-ru-mirror`;
  - `platform_config.base_url` очищен, чтобы источник истины был один.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=LocationsIikoServerSourceSettingsServiceTest,SettingsUpdateSharedConfigIntegrationTest,IikoDepartmentLocationCatalogServiceTest" test`
- Post-check runtime data:
  - `config/shared/settings.json` содержит `locations_iiko_server_sources[0].base_url = https://gk-sv.iiko.it`
  - `config/shared/settings.json` содержит профиль `integration_network_profiles[0].id = telegram-ftl-dev-ru-mirror`
  - `panel_runtime.db`, `spring-panel/panel_runtime.db`, `java-bot/panel_runtime.db` содержат для канала `id=1`:
    `network_route.mode=profile`, `profile_id=telegram-ftl-dev-ru-mirror`, `platform_config={}`

## Примечания

- Старые БД не удалялись.
- Перед изменением маршрутизации сохранён backup в
  `temp-recovery/routing-migration-backup-2026-07-08_085737/`.
