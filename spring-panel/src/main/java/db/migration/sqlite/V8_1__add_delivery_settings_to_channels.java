package db.migration.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V8_1__add_delivery_settings_to_channels extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        if (columnExists(context.getConnection(), "channels", "delivery_settings")) {
            return;
        }

        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE channels ADD COLUMN delivery_settings TEXT DEFAULT '{}'");
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
