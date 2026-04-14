package db.migration.sqlite;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class V17__ensure_tickets_created_at extends BaseJavaMigration {

    private static final String TABLE_NAME = "tickets";
    private static final String COLUMN_NAME = "created_at";
    private static final String INSERT_TRIGGER_NAME = "trg_tickets_created_at_insert";

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        ensureColumn(connection, TABLE_NAME, COLUMN_NAME, "TEXT");
        backfillCreatedAt(connection);
        ensureInsertTrigger(connection);
    }

    private void ensureColumn(Connection connection, String tableName, String columnName, String ddlType) throws SQLException {
        if (columnExists(connection, tableName, columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + ddlType);
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        String sql = "SELECT 1 FROM pragma_table_info('" + tableName + "') WHERE name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private void backfillCreatedAt(Connection connection) throws SQLException {
        String sql = """
                UPDATE tickets
                   SET created_at = COALESCE(
                       (
                           SELECT MIN(m.created_at)
                             FROM messages m
                            WHERE m.ticket_id = tickets.ticket_id
                              AND m.created_at IS NOT NULL
                              AND TRIM(m.created_at) <> ''
                       ),
                       (
                           SELECT MIN(ch.timestamp)
                             FROM chat_history ch
                            WHERE ch.ticket_id = tickets.ticket_id
                              AND ch.timestamp IS NOT NULL
                              AND TRIM(ch.timestamp) <> ''
                       ),
                       NULLIF(TRIM(resolved_at), ''),
                       CURRENT_TIMESTAMP
                   )
                 WHERE created_at IS NULL
                    OR TRIM(created_at) = ''
                """;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private void ensureInsertTrigger(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS " + INSERT_TRIGGER_NAME);
            statement.execute("""
                    CREATE TRIGGER %s
                    AFTER INSERT ON tickets
                    FOR EACH ROW
                    WHEN NEW.created_at IS NULL OR TRIM(NEW.created_at) = ''
                    BEGIN
                        UPDATE tickets
                           SET created_at = CURRENT_TIMESTAMP
                         WHERE user_id = NEW.user_id
                           AND ticket_id = NEW.ticket_id;
                    END
                    """.formatted(INSERT_TRIGGER_NAME));
        }
    }
}
