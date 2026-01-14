package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V6__add_auto_action_template_id_to_channels extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws SQLException {
        try (Connection connection = context.getConnection()) {
            if (columnExists(connection)) {
                return;
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE channels ADD COLUMN auto_action_template_id TEXT");
            }
        }
    }

    private boolean columnExists(Connection connection) throws SQLException {
        String sql = "PRAGMA table_info('channels')";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String name = resultSet.getString("name");
                if ("auto_action_template_id".equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
