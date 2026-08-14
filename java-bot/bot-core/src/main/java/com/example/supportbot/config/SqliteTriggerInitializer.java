package com.example.supportbot.config;

import com.example.supportbot.support.JdbcSchemaInspector;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Order(10)
public class SqliteTriggerInitializer implements ApplicationRunner {
    // Local SQLite trigger/bootstrap layer. Must stay gated away from external PostgreSQL runtime.

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
                    CASE
                        WHEN lower(COALESCE(NEW.resolved_by, '')) IN ('auto_close', 'авто-система') THEN 'auto_close'
                        ELSE 'operator_close'
                    END,
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
    private final BotDatabaseRuntimeMode databaseRuntimeMode;

    public SqliteTriggerInitializer(JdbcTemplate jdbcTemplate,
                                    BotDatabaseRuntimeMode databaseRuntimeMode) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseRuntimeMode = databaseRuntimeMode;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!databaseRuntimeMode.isSqliteMode()) {
            return;
        }
        ensureFeedbacksSchema();
        jdbcTemplate.execute(CREATE_TRIGGER_SQL);
    }

    private void ensureFeedbacksSchema() {
        Set<String> columns = JdbcSchemaInspector.loadColumnNames(jdbcTemplate, "feedbacks");
        if (columns.isEmpty()) {
            jdbcTemplate.execute(CREATE_FEEDBACKS_TABLE_SQL);
            return;
        }

        boolean hasId = columns.contains("id");
        boolean hasTicketId = columns.contains("ticket_id");
        boolean hasChannelId = columns.contains("channel_id");

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
}
