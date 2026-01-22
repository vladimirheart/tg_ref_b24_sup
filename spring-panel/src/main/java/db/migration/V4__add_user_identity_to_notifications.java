package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class V4__add_user_identity_to_notifications extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        try (Connection connection = context.getConnection()) {
            if (!tableExists(connection, "notifications")) {
                return;
            }

            boolean hasUserIdentity = columnExists(connection, "notifications", "user_identity");
            boolean hasLegacyUser = columnExists(connection, "notifications", "user");

            if (!hasUserIdentity) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE notifications ADD COLUMN user_identity TEXT");
                }
            }

            if (hasLegacyUser) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(
                        "UPDATE notifications " +
                            "SET user_identity = user " +
                            "WHERE user_identity IS NULL"
                    );
                }
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute(
                    "CREATE INDEX IF NOT EXISTS idx_notifications_user_identity " +
                        "ON notifications(user_identity)"
                );
            }
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        String sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        String sql = "PRAGMA table_info(" + table + ")";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                String name = resultSet.getString("name");
                if (column.equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        }
    }
}
