package db.migration.sqlite;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class V40__add_chat_attachment_availability_status extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!hasColumn(connection, "chat_attachment_metadata", "availability_status")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        ALTER TABLE chat_attachment_metadata
                        ADD COLUMN availability_status TEXT NOT NULL DEFAULT 'unknown'
                        CHECK (availability_status IN ('available', 'missing', 'external', 'unresolved', 'unknown'))
                        """);
            }
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info('" + table + "')")) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
