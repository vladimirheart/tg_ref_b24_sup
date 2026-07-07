📁 Пути к базам данных

Этот документ фиксирует transitional-модель: какие physical files проект использует сейчас и как они соотносятся с целевыми logical contours.

Target-state topology и причины итогового разбиения описаны в `docs/db/sqlite-target-topology.md`.

## Current physical mapping

| Текущий env/path | Текущий файл | Текущий смысл | Целевой logical contour |
| --- | --- | --- | --- |
| `APP_DB_PANEL_RUNTIME` | `panel_runtime.db` | Главный runtime панели | `panel-runtime` |
| `APP_DB_PANEL_IDENTITY` | `panel_identity.db` | Пользователи, роли, доступ | `panel-identity` |
| `APP_DB_MONITORING` | `monitoring.db` | Monitoring runtime | `monitoring` |
| `APP_DB_BOT_RUNTIME` | `bot_runtime.db` | Shared bot runtime | `bot-runtime` |
| `APP_DB_TICKETS` | `panel_runtime.db` | legacy alias для primary runtime | `panel-runtime` |
| `APP_DB_USERS` | `panel_identity.db` | legacy alias для identity runtime | `panel-identity` |
| `APP_DB_BOT` | `bot_runtime.db` | legacy alias для bot runtime | `bot-runtime` |
| `APP_DB_SETTINGS` | `settings.db` | Registry/bootstrap split-контура | transitional only, без отдельного target DB |
| `APP_DB_CLIENTS` | `clients.db` | Подготовленный secondary clients-файл | transitional only, должен быть поглощён `panel-runtime` |
| `APP_DB_KNOWLEDGE` | `knowledge_base.db` | Подготовленный secondary knowledge-файл | transitional only, должен быть поглощён `panel-runtime` |
| `APP_DB_OBJECTS` | `objects.db` | Secondary файл паспортов объектов | transitional only, должен быть поглощён `panel-runtime` |
| `APP_BOT_DATABASE_DIR` | `bot-<channelId>.db` | Channel-local bot файлы | optional shard-layer внутри `bot-runtime`, не отдельные bounded contexts |

## Что считается каноническим уже сейчас

- `APP_DB_PANEL_RUNTIME` остаётся главным runtime-контуром панели.
- `APP_DB_PANEL_IDENTITY` остаётся отдельным identity-контуром.
- `APP_DB_MONITORING` остаётся отдельным monitoring-контуром и должен стать единственным домом для raw monitoring history.
- `APP_DB_BOT_RUNTIME` остаётся shared bot-runtime контуром до явного решения по shard-слою.
- legacy aliases `APP_DB_TICKETS`, `APP_DB_USERS`, `APP_DB_BOT` остаются поддержаны как fallback.

## Что считается transitional legacy

- `settings.db` не должен развиваться как отдельный business/runtime контур.
- `clients.db`, `knowledge_base.db`, `objects.db` нельзя считать долгосрочными canonical DBs только потому, что файлы уже существуют.
- новые technical history данные не должны по умолчанию падать в `panel_runtime.db`; для них целевые контуры — `monitoring` или будущий `panel-telemetry`.

## Важное ограничение

Physical имя файла теперь должно совпадать с архитектурным смыслом:

- `panel_runtime.db` по роли является `panel-runtime`;
- `panel_identity.db` по роли является `panel-identity`;
- `bot_runtime.db` по роли является `bot-runtime`.

Legacy filenames допустимы только как compatibility fallback. Для новых задач решения нужно принимать по logical contour и canonical filename.

## Текущий пример env

```bash
export APP_DB_PANEL_RUNTIME="/srv/iguana/panel_runtime.db"
export APP_DB_PANEL_IDENTITY="/srv/iguana/panel_identity.db"
export APP_DB_MONITORING="/srv/iguana/monitoring.db"
export APP_DB_CLIENTS="/srv/iguana/clients.db"
export APP_DB_KNOWLEDGE="/srv/iguana/knowledge_base.db"
export APP_DB_OBJECTS="/srv/iguana/objects.db"
export APP_DB_SETTINGS="/srv/iguana/settings.db"
export APP_DB_BOT_RUNTIME="/srv/iguana/bot_runtime.db"
export APP_BOT_DATABASE_DIR="/srv/iguana/bots"
```

Legacy aliases `APP_DB_TICKETS`, `APP_DB_USERS` и `APP_DB_BOT` допустимы как compatibility fallback, но в новых конфигурациях нужно использовать canonical env keys.
