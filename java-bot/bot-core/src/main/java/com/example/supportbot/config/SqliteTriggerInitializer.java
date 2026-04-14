package com.example.supportbot.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "spring.sql.init.platform", havingValue = "sqlite", matchIfMissing = true)
public class SqliteTriggerInitializer implements ApplicationRunner {

    private static final String CREATE_TRIGGER_SQL = """
            CREATE TRIGGER IF NOT EXISTS trg_on_ticket_resolved
            AFTER UPDATE OF status ON tickets
            WHEN NEW.status = 'resolved'
            BEGIN
                INSERT OR IGNORE INTO pending_feedback_requests(
                    user_id, channel_id, ticket_id, source, created_at, expires_at
                )
                VALUES(
                    NEW.user_id,
                    NEW.channel_id,
                    NEW.ticket_id,
                    CASE WHEN NEW.resolved_by = 'Авто-система' THEN 'auto_close' ELSE 'operator_close' END,
                    datetime('now'),
                    datetime('now', '+5 minutes')
                );
            END;
            """;

    private static final String CREATE_FEEDBACKS_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS feedbacks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                rating INTEGER,
                timestamp TEXT,
                ticket_id TEXT,
                channel_id INTEGER REFERENCES channels(id)
            );
            """;

    private final JdbcTemplate jdbcTemplate;

    public SqliteTriggerInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureFeedbacksSchema();
        jdbcTemplate.execute(CREATE_TRIGGER_SQL);
    }

    private void ensureFeedbacksSchema() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("PRAGMA table_info(feedbacks)");
        if (columns.isEmpty()) {
            jdbcTemplate.execute(CREATE_FEEDBACKS_TABLE_SQL);
            return;
        }

        boolean hasId = hasColumn(columns, "id");
        boolean hasTicketId = hasColumn(columns, "ticket_id");
        boolean hasChannelId = hasColumn(columns, "channel_id");

        if (hasId && hasTicketId && hasChannelId) {
            return;
        }

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS feedbacks_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER,
                    rating INTEGER,
                    timestamp TEXT,
                    ticket_id TEXT,
                    channel_id INTEGER REFERENCES channels(id)
                );
                """);
        jdbcTemplate.execute("""
                INSERT INTO feedbacks_new (user_id, rating, timestamp)
                SELECT user_id, rating, timestamp FROM feedbacks;
                """);
        jdbcTemplate.execute("DROP TABLE feedbacks;");
        jdbcTemplate.execute("ALTER TABLE feedbacks_new RENAME TO feedbacks;");
    }

    private boolean hasColumn(List<Map<String, Object>> columns, String name) {
        return columns.stream().anyMatch(column -> name.equalsIgnoreCase(String.valueOf(column.get("name"))));
    }
}
