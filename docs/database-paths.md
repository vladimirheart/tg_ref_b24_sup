📁 Пути к базам данных

Этот документ фиксирует transitional-модель: какие physical files проект использует сейчас и как они соотносятся с целевыми logical contours.

Target-state topology и причины итогового разбиения описаны в `docs/db/sqlite-target-topology.md`.

## Current physical mapping

| Текущий env/path | Текущий файл | Текущий смысл | Целевой logical contour |
| --- | --- | --- | --- |
| `APP_DB_TICKETS` | `tickets.db` | Главный runtime панели, но с legacy-именем | `panel-runtime` |
| `APP_DB_USERS` | `users.db` | Пользователи, роли, доступ | `panel-identity` |
| `APP_DB_MONITORING` | `monitoring.db` | Monitoring runtime | `monitoring` |
| `APP_DB_BOT` | `bot_database.db` | Shared bot runtime legacy-контура | `bot-runtime` |
| `APP_DB_SETTINGS` | `settings.db` | Registry/bootstrap split-контура | transitional only, без отдельного target DB |
| `APP_DB_CLIENTS` | `clients.db` | Подготовленный secondary clients-файл | transitional only, должен быть поглощён `panel-runtime` |
| `APP_DB_KNOWLEDGE` | `knowledge_base.db` | Подготовленный secondary knowledge-файл | transitional only, должен быть поглощён `panel-runtime` |
| `APP_DB_OBJECTS` | `objects.db` | Secondary файл паспортов объектов | transitional only, должен быть поглощён `panel-runtime` |
| `APP_BOT_DATABASE_DIR` | `bot-<channelId>.db` | Channel-local bot файлы | optional shard-layer внутри `bot-runtime`, не отдельные bounded contexts |

## Что считается каноническим уже сейчас

- `APP_DB_TICKETS` остаётся главным runtime-контуром панели до завершения rename/migration.
- `APP_DB_USERS` остаётся отдельным identity-контуром.
- `APP_DB_MONITORING` остаётся отдельным monitoring-контуром и должен стать единственным домом для raw monitoring history.
- `APP_DB_BOT` остаётся shared bot-runtime контуром до явного решения по shard-слою.

## Что считается transitional legacy

- `settings.db` не должен развиваться как отдельный business/runtime контур.
- `clients.db`, `knowledge_base.db`, `objects.db` нельзя считать долгосрочными canonical DBs только потому, что файлы уже существуют.
- новые technical history данные не должны по умолчанию падать в `tickets.db`; для них целевые контуры — `monitoring` или будущий `panel-telemetry`.

## Важное ограничение

Текущее physical имя файла ещё не равно архитектурному смыслу:

- `tickets.db` по роли уже является `panel-runtime`, а не узкой DB только про тикеты;
- `users.db` по роли является `panel-identity`;
- `bot_database.db` по роли является `bot-runtime`.

Для новых задач решения нужно принимать по logical contour, а не по историческому filename.

## Текущий пример env

```bash
export APP_DB_TICKETS="/srv/iguana/tickets.db"
export APP_DB_USERS="/srv/iguana/users.db"
export APP_DB_MONITORING="/srv/iguana/monitoring.db"
export APP_DB_CLIENTS="/srv/iguana/clients.db"
export APP_DB_KNOWLEDGE="/srv/iguana/knowledge_base.db"
export APP_DB_OBJECTS="/srv/iguana/objects.db"
export APP_DB_SETTINGS="/srv/iguana/settings.db"
export APP_DB_BOT="/srv/iguana/bot_database.db"
export APP_BOT_DATABASE_DIR="/srv/iguana/bots"
```

После migration на target-state эти пути должны постепенно уступить место logical naming вроде `panel_runtime.db`, `panel_identity.db`, `panel_telemetry.db` и `bot_runtime.db`.
