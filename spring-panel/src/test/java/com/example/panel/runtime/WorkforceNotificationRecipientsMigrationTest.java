package com.example.panel.runtime;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkforceNotificationRecipientsMigrationTest {
    private static final Path MIGRATION = Path.of(
            "src/main/resources/db/migration/postgresql/V50__workforce_notification_recipients.sql"
    );

    @Test
    void migrationPreservesLegacyRouteAndRunsTwiceWithoutDuplicate() throws Exception {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("workforce_recipients_v50");
        jdbc.execute("CREATE TABLE channels (id BIGINT PRIMARY KEY)");
        jdbc.execute("""
                CREATE TABLE workforce_positions (
                    id BIGINT PRIMARY KEY,
                    notification_channel_id BIGINT,
                    notification_target VARCHAR(32),
                    notification_chat_id TEXT
                )
                """);
        jdbc.execute("CREATE TABLE workforce_shift_sessions (id BIGINT PRIMARY KEY)");
        jdbc.update("INSERT INTO channels(id) VALUES (101)");
        jdbc.update("INSERT INTO workforce_positions VALUES (1, 101, 'support_chat', NULL)");
        apply(jdbc);
        apply(jdbc);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM workforce_position_notification_recipients WHERE position_id = 1",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT target FROM workforce_position_notification_recipients WHERE position_id = 1",
                String.class
        )).isEqualTo("support_chat");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'workforce_shift_notification_deliveries'",
                Integer.class
        )).isEqualTo(1);
    }

    private static void apply(JdbcTemplate jdbc) throws Exception {
        String sql = Files.readString(MIGRATION).replace("\r\n", "\n");
        for (String statement : sql.split(";")) {
            if (!statement.isBlank()) jdbc.execute(statement.trim());
        }
    }
}
