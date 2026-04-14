package db.migration.sqlite;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class V18ClientBlacklistHistoryMigrationTest {

    @Test
    void migrateCreatesBlacklistHistoryTableAndIndex() throws Exception {
        Path dbFile = Files.createTempFile("blacklist-history-migration", ".sqlite");
        String jdbcUrl = "jdbc:sqlite:" + dbFile;
        try {
            Flyway.configure()
                    .dataSource(jdbcUrl, null, null)
                    .locations("classpath:db/migration/sqlite")
                    .placeholders(Map.of("autoIncrement", "AUTOINCREMENT"))
                    .load()
                    .migrate();

            try (Connection connection = DriverManager.getConnection(jdbcUrl)) {
                assertThat(tableExists(connection, "client_blacklist_history")).isTrue();
                assertThat(indexExists(connection, "idx_client_blacklist_history_user")).isTrue();
            }
        } finally {
            Files.deleteIfExists(dbFile);
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "'")) {
            return resultSet.next();
        }
    }

    private static boolean indexExists(Connection connection, String indexName) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='index' AND name='" + indexName + "'")) {
            return resultSet.next();
        }
    }
}
