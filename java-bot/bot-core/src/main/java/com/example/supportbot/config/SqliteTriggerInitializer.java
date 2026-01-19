package com.example.supportbot.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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

    private final JdbcTemplate jdbcTemplate;

    public SqliteTriggerInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        jdbcTemplate.execute(CREATE_TRIGGER_SQL);
    }
}
