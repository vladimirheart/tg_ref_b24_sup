package db.migration.sqlite;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class V19__add_last_portal_activity_to_users extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        if (columnExists(context, "users", "last_portal_activity_at")) {
            return;
        }
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE users ADD COLUMN last_portal_activity_at TEXT");
        }
    }

    private boolean columnExists(Context context, String table, String column) throws Exception {
        try (PreparedStatement statement = context.getConnection()
                .prepareStatement("PRAGMA table_info(" + table + ")")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    if (column.equalsIgnoreCase(resultSet.getString("name"))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}

