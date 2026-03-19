package db.migration.sqlite;

import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class V17EnsureTicketsCreatedAtTest {

    @Test
    void migrateAddsCreatedAtBackfillsExistingRowsAndPopulatesFutureInserts() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            createLegacySchema(connection);
            seedLegacyData(connection);

            new V17__ensure_tickets_created_at().migrate(new TestContext(connection));

            assertThat(columnExists(connection, "tickets", "created_at")).isTrue();
            assertThat(querySingleValue(connection,
                    "SELECT created_at FROM tickets WHERE ticket_id = 'ticket-from-message'"))
                    .isEqualTo("2026-03-01T10:15:30");
            assertThat(querySingleValue(connection,
                    "SELECT created_at FROM tickets WHERE ticket_id = 'ticket-from-history'"))
                    .isEqualTo("2026-03-02T11:45:00");
            assertThat(querySingleValue(connection,
                    "SELECT created_at FROM tickets WHERE ticket_id = 'ticket-from-resolved'"))
                    .isEqualTo("2026-03-03T12:00:00");
            assertThat(querySingleValue(connection,
                    "SELECT created_at FROM tickets WHERE ticket_id = 'ticket-fallback'"))
                    .isNotBlank();

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO tickets (user_id, ticket_id, status, channel_id) VALUES (5, 'ticket-new', 'pending', 1)");
            }

            assertThat(querySingleValue(connection,
                    "SELECT created_at FROM tickets WHERE ticket_id = 'ticket-new'"))
                    .isNotBlank();
        }
    }

    private static void createLegacySchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE tickets (
                        user_id INTEGER NOT NULL,
                        ticket_id TEXT NOT NULL,
                        status TEXT DEFAULT 'pending',
                        resolved_at TEXT,
                        channel_id INTEGER,
                        PRIMARY KEY (user_id, ticket_id)
                    )
                    """);
            statement.executeUpdate("CREATE TABLE messages (ticket_id TEXT, created_at TEXT)");
            statement.executeUpdate("CREATE TABLE chat_history (ticket_id TEXT, timestamp TEXT)");
        }
    }

    private static void seedLegacyData(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO tickets (user_id, ticket_id, status, channel_id) VALUES (1, 'ticket-from-message', 'pending', 1)");
            statement.executeUpdate("INSERT INTO tickets (user_id, ticket_id, status, channel_id) VALUES (2, 'ticket-from-history', 'pending', 1)");
            statement.executeUpdate("INSERT INTO tickets (user_id, ticket_id, status, resolved_at, channel_id) VALUES (3, 'ticket-from-resolved', 'resolved', '2026-03-03T12:00:00', 1)");
            statement.executeUpdate("INSERT INTO tickets (user_id, ticket_id, status, channel_id) VALUES (4, 'ticket-fallback', 'pending', 1)");
            statement.executeUpdate("INSERT INTO messages (ticket_id, created_at) VALUES ('ticket-from-message', '2026-03-01T10:15:30')");
            statement.executeUpdate("INSERT INTO chat_history (ticket_id, timestamp) VALUES ('ticket-from-history', '2026-03-02T11:45:00')");
        }
    }

    private static boolean columnExists(Connection connection, String tableName, String columnName) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info('" + tableName + "')")) {
            while (resultSet.next()) {
                if (columnName.equals(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String querySingleValue(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }

    private record TestContext(Connection connection) implements Context {
        @Override
        public Configuration getConfiguration() {
            return null;
        }

        @Override
        public Connection getConnection() {
            return connection;
        }
    }
}
