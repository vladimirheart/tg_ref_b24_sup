# 2026-07-07 17:19:51 - DB path hardening and compatibility mirror sync

## Пользовательский промпт

> чекни, что ещё похерено при переносе БД

> делай, но пока не удаляй старые БД - вдруг ещё понадобятся

## Затронутые файлы

- `java-bot/bot-core/src/main/java/com/example/supportbot/config/DataSourceConfig.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/config/DataSourceConfigTest.java`
- `panel_runtime.db`
- `panel_identity.db`
- `monitoring.db`
- `objects.db`
- `settings.db`
- `spring-panel/bot_runtime.db`
- `java-bot/panel_runtime.db`
- `temp-recovery/db-compat-sync-backup-2026-07-07_171907/*`

## Что сделано

- Проверено post-migration состояние SQLite-копий и подтверждено, что:
  - root `panel_runtime.db` отстал от `spring-panel/panel_runtime.db`;
  - root `monitoring.db` и `objects.db` были пустыми при живых module-копиях;
  - root `settings.db` был заметно беднее `spring-panel/settings.db`;
  - `java-bot/panel_runtime.db` содержал урезанную локальную копию и мог случайно выиграть
    в bot-core path resolution.
- Ужесточён bot-core SQLite path resolution:
  - `DataSourceConfig` больше не берёт первую попавшуюся относительную `panel_runtime.db`;
  - для canonical/legacy runtime filenames (`panel_runtime.db`, `tickets.db`) теперь выбирается
    лучший существующий кандидат по правилу “непустой файл предпочтительнее пустого, при равенстве
    выигрывает больший размер”;
  - добавлены regression tests на сценарии с маленькой `java-bot/panel_runtime.db` и более полной
    workspace / `spring-panel` копией.
- Без удаления старых БД синхронизированы compatibility copies из более полных источников:
  - `spring-panel/panel_runtime.db` -> `panel_runtime.db`
  - `spring-panel/panel_identity.db` -> `panel_identity.db`
  - `spring-panel/monitoring.db` -> `monitoring.db`
  - `spring-panel/objects.db` -> `objects.db`
  - `spring-panel/settings.db` -> `settings.db`
  - `bot_runtime.db` -> `spring-panel/bot_runtime.db`
  - `spring-panel/panel_runtime.db` -> `java-bot/panel_runtime.db`

## Проверка

- `java-bot\mvnw.cmd -q -pl bot-core -am "-Dtest=DataSourceConfigTest" test`
- `java-bot\mvnw.cmd -q -pl bot-core -am -DskipTests compile`
- Post-check after mirror sync:
  - `panel_runtime.db`: `tickets=21`, `messages=21`, `chat_history=250`, `notifications=689`
  - `panel_identity.db`: `users=4`, `roles=3`
  - `monitoring.db`: `rms_license_monitors=212`, `ssl_certificate_monitors=29`
  - `objects.db`: `objects=9`, `object_passports=9`
  - `settings.db`: `bot_instances=3`, `database_links=29`, `database_registry=5`
  - `spring-panel/bot_runtime.db`: schema restored, file no longer empty
  - `java-bot/panel_runtime.db`: synced to full runtime copy

## Примечания

- Старые файлы не удалялись; перед sync сохранён backup в
  `temp-recovery/db-compat-sync-backup-2026-07-07_171907/`.
- Внутри `settings.db` остаются historical absolute paths и registry drift от старых машин;
  это не выглядит как текущий runtime blocker, но остаётся отдельным cleanup-кандидатом.
