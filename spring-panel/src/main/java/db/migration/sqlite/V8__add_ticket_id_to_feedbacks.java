package db.migration.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V8__add_ticket_id_to_feedbacks extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        ensureColumn(context.getConnection(),
                "feedbacks",
                "ticket_id",
                "ALTER TABLE feedbacks ADD COLUMN ticket_id TEXT");

        ensureColumn(context.getConnection(),
                "feedbacks",
                "channel_id",
                "ALTER TABLE feedbacks ADD COLUMN channel_id BIGINT");
    }

    private void ensureColumn(Connection connection, String tableName, String columnName, String ddl) throws SQLException {
        if (columnExists(connection, tableName, columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
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
