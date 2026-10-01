# Historical legacy DB path reference after PostgreSQL-only cutover

This document preserves names that may still appear in old logs, archived task records or test fixtures. It is not a runtime datasource map and it is not an import/recovery procedure.

## Current production runtime

```text
APP_DB_MODE=postgresql
SPRING_DATASOURCE_URL=jdbc:postgresql://...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
```

Business, identity, monitoring, channels, clients, knowledge and object-passport data use the canonical PostgreSQL contour. Separate panel-side or bot-side business SQLite datasources are not supported.

## Historical names

| Legacy env/path | Historical file | Current meaning |
| --- | --- | --- |
| `APP_DB_PANEL_RUNTIME` / `APP_DB_TICKETS` | `panel_runtime.db` / `tickets.db` | historical/test evidence only |
| `APP_DB_PANEL_IDENTITY` / `APP_DB_USERS` | `panel_identity.db` / `users.db` | historical/test evidence only |
| `APP_DB_MONITORING` | `monitoring.db` | historical/test evidence only |
| `APP_DB_BOT_RUNTIME` / `APP_DB_BOT` | `bot_runtime.db` / `bot_database.db` | historical/test evidence only |
| `APP_DB_CLIENTS` | `clients.db` | historical/test evidence only |
| `APP_DB_KNOWLEDGE` | `knowledge_base.db` | historical/test evidence only |
| `APP_DB_OBJECTS` | `objects.db` | historical/test evidence only |
| `APP_BOT_DATABASE_DIR` | `bot-<channelId>.db` | historical shard evidence only |
| `SUPPORT_BOT_DATABASE_PATH` | legacy/test bridge | historical/test evidence only |

These names do not enable a SQLite runtime mode. The current repository does not provide a live legacy SQLite migration/bootstrap path.

Current production ownership: [database_distribution.md](database_distribution.md). Retired SQLite boundary notes: [SQLITE_BOOTSTRAP_PERIMETER.md](SQLITE_BOOTSTRAP_PERIMETER.md).
