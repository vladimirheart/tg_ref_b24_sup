package com.example.panel.support;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.jdbc.core.JdbcTemplate;

/** Canonical V84 over a minimal owning schema; no broker or working datasource. */
public final class FeedbackPromptOutboxTestSupport {
    private FeedbackPromptOutboxTestSupport() {}

    public static void createSchema(JdbcTemplate jdbc) throws Exception {
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT NOT NULL,ticket_id TEXT NOT NULL,channel_id BIGINT,ticket_public_id VARCHAR(32),business_id BIGINT,is_general BOOLEAN NOT NULL,PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE pending_feedback_requests(id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,channel_id BIGINT,ticket_id TEXT NOT NULL,created_at TIMESTAMPTZ NOT NULL,expires_at TIMESTAMPTZ,sent_at TIMESTAMPTZ)");
        jdbc.execute("""
            CREATE TABLE integration_transport_outbox (
                event_id VARCHAR(120) PRIMARY KEY,transport_source VARCHAR(120) NOT NULL,
                event_kind VARCHAR(120) NOT NULL,exchange_name VARCHAR(255) NOT NULL,
                routing_key VARCHAR(255) NOT NULL,payload_json TEXT NOT NULL,
                channel_id BIGINT,user_id BIGINT,ticket_id TEXT,request_id BIGINT,
                status VARCHAR(32) NOT NULL,attempt_count INTEGER NOT NULL DEFAULT 0,last_error TEXT,
                available_at TIMESTAMPTZ,processing_started_at TIMESTAMPTZ,published_at TIMESTAMPTZ,
                created_at TIMESTAMPTZ NOT NULL,updated_at TIMESTAMPTZ NOT NULL)
            """);
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/postgresql/V84__pending_feedback_context.sql")));
    }

    public static void request(JdbcTemplate jdbc, long id, long user, long channel, String raw) {
        jdbc.update("INSERT INTO pending_feedback_requests(id,user_id,channel_id,ticket_id,created_at,expires_at) VALUES(?,?,?,?,clock_timestamp(),clock_timestamp()+interval '1 hour')",id,user,channel,raw);
    }
}
