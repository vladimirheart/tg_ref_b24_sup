package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.panel.service.IncidentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class IntegrationTransportOpsServiceTest {

    private JdbcTemplate jdbcTemplate;
    private InboundClientMessageIngestionService inboundIngestionService;
    private ConversationTicketCreationIngestionService ticketCreationIngestionService;
    private OutboundFeedbackPromptPublishOutboxService outboxService;
    private IntegrationTransportOpsService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:transport_ops_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        ));
        jdbcTemplate.execute("""
                CREATE TABLE integration_inbound_event_inbox (
                    event_id VARCHAR(120) PRIMARY KEY,
                    event_kind VARCHAR(120),
                    ticket_id VARCHAR(120),
                    routing_key VARCHAR(255),
                    payload_json CLOB,
                    status VARCHAR(32),
                    attempt_count INTEGER DEFAULT 0,
                    processing_started_at TIMESTAMP,
                    updated_at TIMESTAMP,
                    last_error CLOB
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE integration_transport_outbox (
                    event_id VARCHAR(120) PRIMARY KEY,
                    event_kind VARCHAR(120),
                    ticket_id VARCHAR(120),
                    routing_key VARCHAR(255),
                    status VARCHAR(32),
                    attempt_count INTEGER DEFAULT 0,
                    processing_started_at TIMESTAMP,
                    updated_at TIMESTAMP,
                    available_at TIMESTAMP,
                    last_error CLOB
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE runtime_worker_checkpoints (
                    worker_key VARCHAR(120) PRIMARY KEY,
                    cursor_text VARCHAR(255),
                    updated_at TIMESTAMP
                )
                """);

        inboundIngestionService = mock(InboundClientMessageIngestionService.class);
        ticketCreationIngestionService = mock(ConversationTicketCreationIngestionService.class);
        outboxService = mock(OutboundFeedbackPromptPublishOutboxService.class);
        doNothing().when(outboxService).dispatchBatch();
        IncidentService incidentService = mock(IncidentService.class);
        doNothing().when(incidentService).appendSignalEvent(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        whenListSignalIncidentsEmpty(incidentService);
        service = new IntegrationTransportOpsService(
            jdbcTemplate,
            new ObjectMapper().findAndRegisterModules(),
            inboundIngestionService,
            ticketCreationIngestionService,
            outboxService,
            incidentService
        );
    }

    @Test
    void overviewReturnsStatusCounters() {
        jdbcTemplate.update("""
                INSERT INTO integration_inbound_event_inbox(event_id, event_kind, ticket_id, routing_key, payload_json, status, attempt_count, updated_at, last_error)
                VALUES ('in-1', 'client_message.active_ticket', 'T-1', 'rk', '{}', 'failed', 1, CURRENT_TIMESTAMP, 'boom')
                """);
        jdbcTemplate.update("""
                INSERT INTO integration_transport_outbox(event_id, event_kind, ticket_id, routing_key, status, attempt_count, updated_at, last_error)
                VALUES ('out-1', 'feedback.prompt.dispatch', 'T-2', 'rk', 'failed', 2, CURRENT_TIMESTAMP, 'boom')
                """);

        Map<String, Object> payload = service.buildOverview();

        assertThat(((Map<?, ?>) payload.get("inbound")).get("failed")).isEqualTo(1L);
        assertThat(((Map<?, ?>) payload.get("outbound")).get("failed")).isEqualTo(1L);
    }

    @Test
    void replayInboundEventDelegatesToIngestionService() throws Exception {
        InboundClientMessageEvent event = new InboundClientMessageEvent(
            "evt-1",
            "client_message.active_ticket",
            "telegram",
            10L,
            "T-1",
            20L,
            "u20",
            "tester",
            "Test User",
            "hello",
            "text",
            null,
            null,
            null,
            null,
            null,
            OffsetDateTime.now()
        );
        String payloadJson = new ObjectMapper().findAndRegisterModules().writeValueAsString(event);
        jdbcTemplate.update("""
                INSERT INTO integration_inbound_event_inbox(event_id, event_kind, ticket_id, routing_key, payload_json, status, attempt_count, updated_at, last_error)
                VALUES (?, ?, ?, ?, ?, 'failed', 1, CURRENT_TIMESTAMP, 'boom')
                """,
            event.eventId(),
            event.eventKind(),
            event.ticketId(),
            "integration.inbound.telegram",
            payloadJson
        );

        Map<String, Object> result = service.replayInboundEvent(event.eventId(), "operator");

        verify(inboundIngestionService).ingest(org.mockito.ArgumentMatchers.any(InboundClientMessageEvent.class), org.mockito.ArgumentMatchers.eq("integration.inbound.telegram"));
        assertThat(result.get("action")).isEqualTo("replayed");
    }

    @Test
    void requeueOutboundEventMovesRowBackToQueued() {
        jdbcTemplate.update("""
                INSERT INTO integration_transport_outbox(event_id, event_kind, ticket_id, routing_key, status, attempt_count, updated_at, last_error)
                VALUES ('out-1', 'feedback.prompt.dispatch', 'T-2', 'rk', 'failed', 2, CURRENT_TIMESTAMP, 'boom')
                """);

        Map<String, Object> result = service.requeueOutboundEvent("out-1", "operator");

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM integration_transport_outbox WHERE event_id = 'out-1'",
            String.class
        )).isEqualTo("queued");
        assertThat(result.get("action")).isEqualTo("requeued");
        verify(outboxService).dispatchBatch();
    }

    private void whenListSignalIncidentsEmpty(IncidentService incidentService) {
        org.mockito.Mockito.when(incidentService.listIncidentSummariesForSignalType("integration_transport"))
            .thenReturn(List.of());
    }
}
