package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class IntegrationInboundEventInboxServiceTest {

    @Test
    void beginProcessingReclaimsFailedEventAndIncrementsAttempts() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createSchema(jdbcTemplate);
        IntegrationInboundEventInboxService service = new IntegrationInboundEventInboxService(jdbcTemplate, new ObjectMapper());

        boolean first = service.beginProcessing("evt-1", "kind", "telegram", 1L, "T-1", "routing.1", java.util.Map.of("ok", true), null);
        service.markFailed("evt-1", new IllegalStateException("boom"));
        boolean reclaimed = service.beginProcessing("evt-1", "kind", "telegram", 1L, "T-1", "routing.1", java.util.Map.of("retry", true), null);

        assertThat(first).isTrue();
        assertThat(reclaimed).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT attempt_count FROM integration_inbound_event_inbox WHERE event_id = ?",
            Integer.class,
            "evt-1"
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM integration_inbound_event_inbox WHERE event_id = ?",
            String.class,
            "evt-1"
        )).isEqualTo("processing");
    }

    @Test
    void beginProcessingSkipsProcessedEvent() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createSchema(jdbcTemplate);
        IntegrationInboundEventInboxService service = new IntegrationInboundEventInboxService(jdbcTemplate, new ObjectMapper());

        assertThat(service.beginProcessing("evt-2", "kind", "telegram", 1L, "T-2", "routing.2", java.util.Map.of(), null)).isTrue();
        service.markProcessed("evt-2");

        assertThat(service.beginProcessing("evt-2", "kind", "telegram", 1L, "T-2", "routing.2", java.util.Map.of("retry", true), null)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT attempt_count FROM integration_inbound_event_inbox WHERE event_id = ?",
            Integer.class,
            "evt-2"
        )).isEqualTo(1);
    }

    @Test
    void beginProcessingReclaimsStaleProcessingEvent() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createSchema(jdbcTemplate);
        jdbcTemplate.update("""
                INSERT INTO integration_inbound_event_inbox(
                    event_id, event_kind, platform, channel_id, ticket_id, transport_source,
                    routing_key, payload_json, status, received_at, attempt_count, processing_started_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, datetime('now', '-30 minutes'), datetime('now', '-30 minutes'))
                """,
            "evt-3", "kind", "telegram", 1L, "T-3", "rabbitmq", "routing.3", "{}", "processing", 1
        );
        IntegrationInboundEventInboxService service = new IntegrationInboundEventInboxService(jdbcTemplate, new ObjectMapper());

        assertThat(service.beginProcessing("evt-3", "kind", "telegram", 1L, "T-3", "routing.3", java.util.Map.of("retry", true), null)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT attempt_count FROM integration_inbound_event_inbox WHERE event_id = ?",
            Integer.class,
            "evt-3"
        )).isEqualTo(2);
    }

    private JdbcTemplate jdbcTemplate() throws Exception {
        Path dbFile = Files.createTempFile("integration-inbox-", ".db");
        return new JdbcTemplate(new DriverManagerDataSource("jdbc:sqlite:" + dbFile.toAbsolutePath()));
    }

    private void createSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE integration_inbound_event_inbox (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    event_id TEXT NOT NULL UNIQUE,
                    event_kind TEXT NOT NULL,
                    platform TEXT NOT NULL,
                    channel_id BIGINT NOT NULL,
                    ticket_id TEXT NOT NULL,
                    transport_source TEXT NOT NULL,
                    routing_key TEXT,
                    payload_json TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'received',
                    received_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    processed_at TEXT,
                    last_error TEXT,
                    attempt_count INTEGER NOT NULL DEFAULT 0,
                    processing_started_at TEXT,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
