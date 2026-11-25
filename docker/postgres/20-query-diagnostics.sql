-- Infrastructure diagnostics, independent of application Flyway migrations.
-- Runs automatically on a fresh POSTGRES_DB; apply explicitly to existing databases.
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
