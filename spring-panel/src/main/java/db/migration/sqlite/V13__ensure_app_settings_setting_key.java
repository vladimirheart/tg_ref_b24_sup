package db.migration.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V13__ensure_app_settings_setting_key extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        if (!tableExists(connection, "app_settings")) {
            return;
        }

        boolean hasSettingKey = columnExists(connection, "app_settings", "setting_key");
        boolean hasKey = columnExists(connection, "app_settings", "key");

        if (!hasSettingKey) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE app_settings ADD COLUMN setting_key VARCHAR(128)");
            }
            hasSettingKey = true;
        }

        if (hasSettingKey && hasKey) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("UPDATE app_settings SET setting_key = \"key\" WHERE setting_key IS NULL");
            }
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        String sql = "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        String sql = String.format("SELECT 1 FROM pragma_table_info('%s') WHERE name = ?", tableName);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
