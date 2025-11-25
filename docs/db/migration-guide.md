# SQLite migration guide

This project uses Flyway to build the canonical PostgreSQL schema. The `scripts/export_sqlite_to_sql.py` helper extracts
existing SQLite data into a set of `INSERT` statements that can be executed against a PostgreSQL or MySQL database.

## Prerequisites

* Python 3.10+
* Access to the SQLite backups (`tickets.db`, `users.db`, `bot_database.db`)
* A target PostgreSQL or MySQL database that already contains the schema produced by the Flyway migrations
* Existing Java-bot SQLite installs upgraded to the latest Alembic revision (`python -c "from migrations_runner import ensure_schema_is_current; ensure_schema_is_current()"`)

## Export workflow

1. From the repository root run the exporter for the desired dialect:

   ```bash
   ./scripts/export_sqlite_to_sql.py --dialect postgresql --output /tmp/import-postgres.sql
   # or for MySQL
   ./scripts/export_sqlite_to_sql.py --dialect mysql --output /tmp/import-mysql.sql
   ```

   The script preserves primary keys, rewrites renamed columns (for example `notifications.user` → `user_identity`) and
   normalises boolean values so that they match the target database representation. When the PostgreSQL dialect is used
   the SQL file also contains `SELECT setval` statements that adjust the identity sequences after import.

2. Execute the generated script inside the target database:

   ```bash
   # PostgreSQL
   psql "$DATABASE_URL" -f /tmp/import-postgres.sql

   # MySQL
   mysql --user=<user> --password --database=<schema> < /tmp/import-mysql.sql
   ```

   All statements are wrapped in a single transaction so either the whole data set is loaded or nothing is changed.

3. After the import completes validate a few core aggregates to ensure that row counts line up with the source
   SQLite database, for example:

   ```sql
   SELECT COUNT(*) FROM tickets;
   SELECT COUNT(*) FROM messages;
   SELECT COUNT(*) FROM panel_users;
   ```

## Table coverage

The exporter handles every table present in the three SQLite files and maps them to the equivalent Flyway-managed
schema. The exporter is tolerant of legacy column names (for example `app_settings.key` → `app_settings.setting_key`
and `it_equipment_catalog.equipment_type` → `it_equipment_catalog.item_type`) so that older backups remain usable.
If additional tables are added in the future extend the `MIGRATION_PLAN` constant in `scripts/export_sqlite_to_sql.py`
with the source and target column mapping.

## Keeping Java bot deployments in sync

The Java bot uses the same Alembic migrations as the Python stack to keep SQLite deployments aligned with the shared
schema. Run the following snippet on existing installations before attempting exports or connecting the Spring panel:

```bash
python -c "from migrations_runner import ensure_schema_is_current; ensure_schema_is_current()"
```

This will add missing columns such as `setting_key` and `item_type` and create the compatibility indexes expected by
the panel and Java bot code.

## Verification

After importing the data run the Spring Boot application; Flyway will validate the schema on start-up and any
inconsistencies will be reported in the logs. Repository tests (see `spring-panel/src/test/java`) provide an
additional safety net by exercising the most important entity mappings under the migrated schema.
