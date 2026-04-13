package db.migration.sqlite;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Statement;

public class V20__create_ssl_certificate_monitors extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS ssl_certificate_monitors (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    site_name TEXT NOT NULL,
                    endpoint_url TEXT NOT NULL,
                    host TEXT NOT NULL,
                    port INTEGER NOT NULL DEFAULT 443,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    monitor_status TEXT,
                    error_message TEXT,
                    days_left INTEGER,
                    expires_at TEXT,
                    last_checked_at TEXT,
                    last_notified_at TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """);
            statement.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_ssl_certificate_monitors_endpoint
                ON ssl_certificate_monitors(endpoint_url)
                """);
            statement.execute("""
                CREATE INDEX IF NOT EXISTS idx_ssl_certificate_monitors_enabled
                ON ssl_certificate_monitors(enabled)
                """);
        }
    }
}
