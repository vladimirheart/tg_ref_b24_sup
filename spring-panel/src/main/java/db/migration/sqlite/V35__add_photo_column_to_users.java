package db.migration.sqlite;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class V35__add_photo_column_to_users extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        if (columnExists(context, "users", "photo")) {
            return;
        }
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE users ADD COLUMN photo TEXT");
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
