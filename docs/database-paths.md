# Legacy SQLite source paths после PostgreSQL cutover

Этот документ описывает только archive/import/recovery path hints. Он **не** является картой production runtime datasource.

## Production runtime

Для `spring-panel` canonical database contract:

```text
APP_DB_MODE=postgresql
SPRING_DATASOURCE_URL=jdbc:postgresql://...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
```

Business, identity, monitoring, channels, clients, knowledge и object-passport data живут в canonical PostgreSQL contour. Отдельные panel-side SQLite datasources больше не поддерживаются.

## Legacy archive/import source hints

| Env/path | Historical source | Разрешённое использование |
| --- | --- | --- |
| `APP_DB_PANEL_RUNTIME` / `APP_DB_TICKETS` | `panel_runtime.db` / `tickets.db` | archive/import/recovery input |
| `APP_DB_PANEL_IDENTITY` / `APP_DB_USERS` | `panel_identity.db` / `users.db` | archive/import/recovery input |
| `APP_DB_MONITORING` | `monitoring.db` | archive/import/recovery input |
| `APP_DB_BOT_RUNTIME` / `APP_DB_BOT` | `bot_runtime.db` / `bot_database.db` | archive/import/recovery input |
| `APP_DB_CLIENTS` | `clients.db` | archive/import/recovery input |
| `APP_DB_KNOWLEDGE` | `knowledge_base.db` | archive/import/recovery input |
| `APP_DB_OBJECTS` | `objects.db` | archive/import/recovery input |
| `APP_BOT_DATABASE_DIR` | `bot-<channelId>.db` | legacy shard staging/import/diagnostics |
| `SUPPORT_BOT_DATABASE_PATH` | legacy/test bridge | test/archive compatibility only; not production business storage |

Наличие этих env keys или файлов не включает SQLite runtime для `spring-panel`. Normal production roles не должны монтировать legacy sources как live datasource.

## Explicit import/recovery

Для controlled legacy import используйте dedicated tooling и staging contour, включая `docker-compose.legacy-sqlite-import.yml`, `scripts/stage-legacy-sqlite-import.*` и verification tooling. Повторный import не должен запускаться автоматически только потому, что рядом с checkout обнаружен `*.db`.

Актуальная production ownership-модель: [database_distribution.md](database_distribution.md). Разрешённый residual SQLite perimeter: [SQLITE_BOOTSTRAP_PERIMETER.md](SQLITE_BOOTSTRAP_PERIMETER.md).
