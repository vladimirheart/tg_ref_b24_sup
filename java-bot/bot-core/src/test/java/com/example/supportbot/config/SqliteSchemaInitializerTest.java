package com.example.supportbot.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.supportbot.support.JdbcSchemaInspector;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.sqlite.SQLiteDataSource;

class SqliteSchemaInitializerTest {

    @TempDir
    Path tempDir;

    @Test
    void runInitializesSqliteSchemaWhenSqliteModeIsActive() throws Exception {
        SQLiteDataSource dataSource = sqliteDataSource(tempDir.resolve("sqlite-runtime.db"));
        SqliteSchemaInitializer initializer = new SqliteSchemaInitializer(
                dataSource,
                new BotDatabaseRuntimeMode(new MockEnvironment())
        );

        initializer.run(new DefaultApplicationArguments(new String[0]));

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertTrue(JdbcSchemaInspector.loadColumnNames(jdbcTemplate, "tickets").contains("ticket_id"));
        assertTrue(JdbcSchemaInspector.loadColumnNames(jdbcTemplate, "pending_feedback_requests").contains("expires_at"));
    }

    @Test
    void runSkipsSchemaBootstrapWhenExternalModeIsActive() throws Exception {
        SQLiteDataSource dataSource = sqliteDataSource(tempDir.resolve("external-mode.db"));
        MockEnvironment environment = new MockEnvironment()
                .withProperty("support-bot.database.mode", "postgresql")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/iguana");
        SqliteSchemaInitializer initializer = new SqliteSchemaInitializer(
                dataSource,
                new BotDatabaseRuntimeMode(environment)
        );

        initializer.run(new DefaultApplicationArguments(new String[0]));

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertFalse(JdbcSchemaInspector.loadColumnNames(jdbcTemplate, "tickets").contains("ticket_id"));
    }

    private SQLiteDataSource sqliteDataSource(Path path) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + path.toAbsolutePath().normalize());
        return dataSource;
    }
}
