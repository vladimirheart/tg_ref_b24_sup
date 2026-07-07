# 2026-07-07 15:12:42 - Restore telegram mirror scheme

## Затронутые файлы

- `spring-panel/panel_runtime.db`
- `panel_runtime.db`
- `temp-recovery/telegram-scheme-backup-2026-07-07_151156/*`

## Пользовательский промт

Основной:

> посмотри что делалось и восстанови такую схему

Контекстный запрос, на который опиралось восстановление:

> поищи по запросам, где я пытался завести бота телеграм. я потерял как именно и на какой именно адрес его запускал

## Что сделано

- Проверена текущая запись Telegram-канала `id=1` в `channels` и подтверждено, что она снова хранила legacy-схему из `01-108`: `network_route.proxy = https://telegram.ftl-dev.ru:443`, а `platform_config.base_url` был пустым.
- Для канала `id=1` восстановлена целевая схема из `01-108`:
  - `platform_config.base_url = "https://telegram.ftl-dev.ru"`
  - `delivery_settings.network_route.mode = "direct"`
  - proxy-поля внутри `network_route` очищены от mirror-host, чтобы Telegram Bot API mirror больше не трактовался как forward proxy.
- Та же правка синхронно внесена и в root `panel_runtime.db` как compatibility-копию runtime БД.
- Перед изменением создан бэкап обеих копий runtime-БД в `temp-recovery/telegram-scheme-backup-2026-07-07_151156/`.

## Проверка

- `spring-panel/panel_runtime.db`:
  - `json_extract(platform_config, '$.base_url') = https://telegram.ftl-dev.ru`
  - `json_extract(delivery_settings, '$.network_route.mode') = direct`
  - `json_extract(delivery_settings, '$.network_route.proxy.host') = ''`
- `panel_runtime.db` синхронизирован с теми же значениями.
- `json_valid(platform_config) = 1`
- `json_valid(delivery_settings) = 1`
- `PRAGMA integrity_check = ok`

## Ограничения

- Изменение восстановило схему хранения конфигурации канала, но не выполняло restart `spring-panel` или Telegram bot runtime.
- Если панель или бот уже были запущены до правки, для подхвата нового `base_url` потребуется новый запуск процесса.
