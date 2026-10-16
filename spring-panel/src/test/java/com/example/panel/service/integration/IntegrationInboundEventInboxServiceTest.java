package com.example.panel.service.integration;

import com.example.panel.support.PostgresqlJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

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
    void beginProcessingAcceptsOpaqueEventIdAndDuplicateDoesNotCreateAnotherRow() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        createSchema(jdbcTemplate);
        IntegrationInboundEventInboxService service = new IntegrationInboundEventInboxService(jdbcTemplate, new ObjectMapper());

        String eventId = "ticket-created:telegram:450028723";
        assertThat(service.beginProcessing(eventId, "kind", "telegram", 1L, "T-opaque", "routing.opaque", java.util.Map.of(), null)).isTrue();
        service.markProcessed(eventId);
        assertThat(service.beginProcessing(eventId, "kind", "telegram", 1L, "T-opaque", "routing.opaque", java.util.Map.of("retry", true), null)).isFalse();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM integration_inbound_event_inbox WHERE event_id = ?",
            Integer.class,
            eventId
        )).isEqualTo(1);
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
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP - INTERVAL '30 minutes', CURRENT_TIMESTAMP - INTERVAL '30 minutes')
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

    @Test
    void processedTicketCreationCannotBeReplayedWithDifferentOwner() {
        JdbcTemplate jdbc = jdbcTemplate();
        createSchema(jdbc);
        IntegrationInboundEventInboxService service = new IntegrationInboundEventInboxService(
            jdbc, new ObjectMapper().findAndRegisterModules());
        ConversationTicketCreatedEvent original = ticketEvent("evt-frozen", 51L);
        ConversationTicketCreatedEvent changed = ticketEvent("evt-frozen", 52L);
        assertThat(service.beginProcessing(original.eventId(), original.eventKind(), original.platform(),
            original.channelId(), original.ticketId(), "routing", original, null)).isTrue();
        service.markProcessed(original.eventId());

        assertThatThrownBy(() -> service.beginProcessing(changed.eventId(), changed.eventKind(),
            changed.platform(), changed.channelId(), changed.ticketId(), "routing", changed, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("identity or business mismatch");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM integration_inbound_event_inbox WHERE event_id = ?",
            Integer.class, original.eventId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "SELECT payload_json::jsonb ->> 'businessId' FROM integration_inbound_event_inbox WHERE event_id = ?",
            String.class, original.eventId())).isEqualTo("51");
    }

    @Test
    void failedTicketCreationCannotOverwriteOwnerButSameOwnerCanRetry() {
        JdbcTemplate jdbc = jdbcTemplate();
        createSchema(jdbc);
        IntegrationInboundEventInboxService service = new IntegrationInboundEventInboxService(
            jdbc, new ObjectMapper().findAndRegisterModules());
        ConversationTicketCreatedEvent original = ticketEvent("evt-failed", 51L);
        ConversationTicketCreatedEvent changed = ticketEvent("evt-failed", 52L);
        assertThat(service.beginProcessing(original.eventId(), original.eventKind(), original.platform(),
            original.channelId(), original.ticketId(), "routing", original, null)).isTrue();
        service.markFailed(original.eventId(), new IllegalStateException("first attempt"));

        assertThatThrownBy(() -> service.beginProcessing(changed.eventId(), changed.eventKind(),
            changed.platform(), changed.channelId(), changed.ticketId(), "routing", changed, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("identity or business mismatch");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM integration_inbound_event_inbox WHERE event_id = ?",
            Integer.class, original.eventId())).isEqualTo(1);
        assertThat(service.beginProcessing(original.eventId(), original.eventKind(), original.platform(),
            original.channelId(), original.ticketId(), "routing", original, null)).isTrue();
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM integration_inbound_event_inbox WHERE event_id = ?",
            Integer.class, original.eventId())).isEqualTo(2);
    }

    @Test
    void processedGeneralTicketCannotLoseItsExplicitScopeOnReplay() {
        JdbcTemplate jdbc = jdbcTemplate();
        createSchema(jdbc);
        IntegrationInboundEventInboxService service = new IntegrationInboundEventInboxService(
            jdbc, new ObjectMapper().findAndRegisterModules());
        ConversationTicketCreatedEvent base = ticketEvent("evt-general-frozen", null);
        ConversationTicketCreatedEvent general = new ConversationTicketCreatedEvent(
            base.eventId(), base.eventKind(), base.platform(), base.channelId(), base.ticketId(),
            base.userId(), base.userIdentity(), base.username(), base.clientName(), base.business(),
            base.locationType(), base.city(), base.locationName(), base.problem(), base.occurredAt(),
            base.attributes(), base.historyEntries(), null, "GENERAL", "USER_SELECTION");
        assertThat(service.beginProcessing(general.eventId(), general.eventKind(), general.platform(),
            general.channelId(), general.ticketId(), "routing", general, null)).isTrue();
        service.markProcessed(general.eventId());

        assertThatThrownBy(() -> service.beginProcessing(base.eventId(), base.eventKind(),
            base.platform(), base.channelId(), base.ticketId(), "routing", base, null))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("identity or business mismatch");
        assertThat(jdbc.queryForObject(
            "SELECT payload_json::jsonb ->> 'scopeKind' FROM integration_inbound_event_inbox WHERE event_id = ?",
            String.class, general.eventId())).isEqualTo("GENERAL");
    }

    @Test
    void oldTicketCreationPayloadWithoutBusinessIdRemainsIdempotent() {
        JdbcTemplate jdbc = jdbcTemplate();
        createSchema(jdbc);
        IntegrationInboundEventInboxService service = new IntegrationInboundEventInboxService(
            jdbc, new ObjectMapper().findAndRegisterModules());
        ConversationTicketCreatedEvent old = ticketEvent("evt-old", null);
        assertThat(service.beginProcessing(old.eventId(), old.eventKind(), old.platform(),
            old.channelId(), old.ticketId(), "routing", old, null)).isTrue();
        service.markProcessed(old.eventId());
        jdbc.update("UPDATE integration_inbound_event_inbox "
            + "SET payload_json = (payload_json::jsonb - 'businessId' - 'scopeKind' "
            + "- 'scopeSelectionSource')::text WHERE event_id = ?", old.eventId());

        assertThat(service.beginProcessing(old.eventId(), old.eventKind(), old.platform(),
            old.channelId(), old.ticketId(), "routing", old, null)).isFalse();
    }

    private ConversationTicketCreatedEvent ticketEvent(String eventId, Long businessId) {
        return new ConversationTicketCreatedEvent(eventId, "ticket.created.initial_contact", "telegram",
            25L, "T-25", 901L, "tg-901", "client", "Client", "Legacy", "", "", "", "Help",
            null, List.of(), List.of(), businessId);
    }

    private JdbcTemplate jdbcTemplate() {
        return PostgresqlJdbcTestSupport.freshJdbcTemplate("integration_inbound_inbox");
    }

    private void createSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE integration_inbound_event_inbox (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    event_id TEXT NOT NULL UNIQUE,
                    event_kind TEXT NOT NULL,
                    platform TEXT NOT NULL,
                    channel_id BIGINT NOT NULL,
                    ticket_id TEXT NOT NULL,
                    transport_source TEXT NOT NULL,
                    routing_key TEXT,
                    payload_json TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'received',
                    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    processed_at TIMESTAMPTZ,
                    last_error TEXT,
                    attempt_count INTEGER NOT NULL DEFAULT 0,
                    processing_started_at TIMESTAMPTZ,
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }
}
