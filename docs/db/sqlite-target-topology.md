# Historical SQLite topology — superseded

> **Status: historical document.** The former multi-SQLite target model is superseded. Canonical relational business/runtime storage is PostgreSQL only.

This path remains as a navigation anchor for old task/changelog references. Do not use it to design or operate current runtime.

## Current contract

- `spring-panel`, `java-bot` and dynamic bot children use PostgreSQL for relational business/runtime data.
- `APP_DB_MODE=sqlite` and other non-PostgreSQL runtime selections are rejected.
- No panel secondary SQLite datasources and no bot-worker technical SQLite datasource remain in the live graph.
- Legacy `*.db` files and per-channel shards, if retained, are historical/test evidence only; they are not startup inputs or a supported first-party import path.

## Current sources of truth

- [../database_distribution.md](../database_distribution.md) — production ownership;
- [../configuration.md](../configuration.md) — runtime configuration;
- [../SQLITE_BOOTSTRAP_PERIMETER.md](../SQLITE_BOOTSTRAP_PERIMETER.md) — retired SQLite boundary and allowed historical/test evidence;
- [../../ai-context/rules/backend/04-sqlite-topology.md](../../ai-context/rules/backend/04-sqlite-topology.md) — project rule.

Historical decisions about multiple SQLite contours belong only to dated task/changelog snapshots.
