package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.example.supportbot.config.IntegrationRabbitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class IntegrationTransportOutboxServiceTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
        .withDatabaseName("iguana_test")
        .withUsername("iguana")
        .withPassword("iguana");

    static {
        POSTGRES.start();
    }

    private JdbcTemplate jdbcTemplate;
    private IntegrationTransportOutboxService service;
    private IntegrationRabbitProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        String schema = "bot_outbox_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()
        ));
        admin.execute("CREATE SCHEMA " + schema);
        String separator = POSTGRES.getJdbcUrl().contains("?") ? "&" : "?";
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl() + separator + "currentSchema=" + schema + "&stringtype=unspecified",
            POSTGRES.getUsername(),
            POSTGRES.getPassword()
        ));
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(), any(), any(CorrelationData.class));
        service = new IntegrationTransportOutboxService(
            jdbcTemplate,
            new ObjectMapper().findAndRegisterModules(),
            rabbitTemplate
        );
        properties = new IntegrationRabbitProperties();
        properties.setInboundExchange("iguana.integration.inbound");
        properties.setRoutingTicketCreated("integration.inbound.ticket.created");
    }

    @Test
    void inboundClientMessagePublishIsPersistedAndMarkedPublished() {
        InboundClientMessageEvent event = new InboundClientMessageEvent(
            UUID.randomUUID().toString(),
            "client_message.active_ticket",
            "telegram",
            10L,
            "T-42",
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

        service.enqueueInboundClientMessage(event, "integration.inbound.telegram", properties);
        service.dispatchScheduled();

        assertThat(statusOf(event.eventId())).isEqualTo("published");
    }

    @Test
    void conversationTicketCreatedPublishIsPersistedAndMarkedPublished() {
        ConversationTicketCreatedEvent event = new ConversationTicketCreatedEvent(
            UUID.randomUUID().toString(),
            "ticket.created.initial_contact",
            "telegram",
            10L,
            "T-99",
            20L,
            "u20",
            "tester",
            "Test User",
            "business",
            "location_type",
            "city",
            "location",
            "problem",
            OffsetDateTime.now(),
            List.of(new ConversationTicketCreatedEvent.TicketAttributePayload(
                "q1",
                "business",
                "Business",
                "select",
                "b1",
                "Business 1",
                "Business 1",
                true
            )),
            List.of(new ConversationHistoryEntry(
                20L,
                "hello",
                "text",
                null,
                null,
                null,
                OffsetDateTime.now()
            )),
            51L
        );

        service.enqueueConversationTicketCreated(event, properties);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT payload_json::jsonb ->> 'businessId' FROM integration_transport_outbox WHERE event_id = ?",
            String.class, event.eventId())).isEqualTo("51");
        service.dispatchScheduled();

        assertThat(statusOf(event.eventId())).isEqualTo("published");
    }

    @Test
    void duplicateInboundClientMessageEventIsIgnoredAtomically() {
        InboundClientMessageEvent event = new InboundClientMessageEvent(
            "duplicate-client-event",
            "client_message.active_ticket",
            "telegram",
            10L,
            "T-42",
            20L,
            "u20",
            "tester",
            "Test User",
            "hello",
            "text",
            null,
            null,
            "9001",
            null,
            null,
            OffsetDateTime.parse("2026-09-21T10:00:00Z")
        );

        service.enqueueInboundClientMessage(event, "integration.inbound.telegram", properties);
        service.enqueueInboundClientMessage(event, "integration.inbound.telegram", properties);

        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM integration_transport_outbox WHERE event_id = ?",
            Integer.class,
            event.eventId()
        )).isEqualTo(1);
    }

    @Test
    void duplicateConversationTicketEventReturnsFalseAndKeepsSingleOutboxRow() {
        ConversationTicketCreatedEvent event = new ConversationTicketCreatedEvent(
            "duplicate-ticket-event",
            "ticket.created.initial_contact",
            "max",
            2L,
            "T-MAX-1",
            1001L,
            "1001",
            "max-user",
            "MAX User",
            "business",
            "type",
            "city",
            "location",
            "problem",
            OffsetDateTime.parse("2026-09-21T10:00:00Z"),
            List.of(),
            List.of()
        );

        assertThat(service.enqueueConversationTicketCreated(event, properties)).isTrue();
        jdbcTemplate.update("UPDATE integration_transport_outbox "
            + "SET payload_json = (payload_json::jsonb - 'businessId')::text WHERE event_id = ?", event.eventId());
        assertThat(service.enqueueConversationTicketCreated(event, properties)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM integration_transport_outbox WHERE event_id = ?",
            Integer.class,
            event.eventId()
        )).isEqualTo(1);
    }

    @Test
    void duplicateTicketCreationCannotChangeFrozenOwnerOrChannel() {
        ConversationTicketCreatedEvent original = new ConversationTicketCreatedEvent(
            "frozen-ticket-event", "ticket.created.initial_contact", "telegram", 10L,
            "T-FROZEN", 20L, "u20", "tester", "Test User", "legacy", "type", "city",
            "location", "problem", OffsetDateTime.parse("2026-10-15T10:00:00Z"),
            List.of(), List.of(), 51L);
        assertThat(service.enqueueConversationTicketCreated(original, properties)).isTrue();
        ConversationTicketCreatedEvent wrongBusiness = new ConversationTicketCreatedEvent(
            original.eventId(), original.eventKind(), original.platform(), original.channelId(),
            original.ticketId(), original.userId(), original.userIdentity(), original.username(),
            original.clientName(), original.business(), original.locationType(), original.city(),
            original.locationName(), original.problem(), original.occurredAt(),
            original.attributes(), original.historyEntries(), 52L);
        ConversationTicketCreatedEvent wrongChannel = new ConversationTicketCreatedEvent(
            original.eventId(), original.eventKind(), original.platform(), 11L,
            original.ticketId(), original.userId(), original.userIdentity(), original.username(),
            original.clientName(), original.business(), original.locationType(), original.city(),
            original.locationName(), original.problem(), original.occurredAt(),
            original.attributes(), original.historyEntries(), 51L);

        assertThatThrownBy(() -> service.enqueueConversationTicketCreated(wrongBusiness, properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("identity or business mismatch");
        assertThatThrownBy(() -> service.enqueueConversationTicketCreated(wrongChannel, properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("identity or business mismatch");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT payload_json::jsonb ->> 'businessId' FROM integration_transport_outbox WHERE event_id = ?",
            String.class, original.eventId())).isEqualTo("51");
    }

    @Test
    void inboundOutboxMetadataKeepsOpaqueTicketIdsDistinctFromPayload() {
        for (String ticketId : List.of("T-42", " T-42 ", "null", " null ")) {
            InboundClientMessageEvent event = new InboundClientMessageEvent(
                UUID.randomUUID().toString(), "client_message.active_ticket", "telegram", 10L,
                ticketId, 20L, "u20", "tester", "Test User", "hello", "text",
                null, null, null, null, null, OffsetDateTime.now()
            );
            service.enqueueInboundClientMessage(event, "integration.inbound.telegram", properties);
            assertThat(ticketIdOf(event.eventId())).isEqualTo(ticketId);
            assertThat(jdbcTemplate.queryForObject(
                "SELECT payload_json::jsonb ->> 'ticketId' FROM integration_transport_outbox WHERE event_id = ?",
                String.class, event.eventId()
            )).isEqualTo(ticketId);
        }
    }

    @Test
    void ticketCreatedOutboxMetadataKeepsOpaqueTicketIdAndBlankBecomesNull() {
        for (String ticketId : new String[] {"T-99", " T-99 ", "null", " null ", "   ", null}) {
            ConversationTicketCreatedEvent event = new ConversationTicketCreatedEvent(
                UUID.randomUUID().toString(), "ticket.created.initial_contact", "telegram", 10L,
                ticketId, 20L, "u20", "tester", "Test User", "business", "type", "city",
                "location", "problem", OffsetDateTime.now(), List.of(), List.of()
            );
            service.enqueueConversationTicketCreated(event, properties);
            assertThat(ticketIdOf(event.eventId())).isEqualTo(
                ticketId == null || ticketId.isBlank() ? null : ticketId
            );
        }
    }

    private String ticketIdOf(String eventId) {
        return jdbcTemplate.queryForObject(
            "SELECT ticket_id FROM integration_transport_outbox WHERE event_id = ?",
            String.class, eventId
        );
    }

    private String statusOf(String eventId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM integration_transport_outbox WHERE event_id = ?",
            String.class,
            eventId
        );
    }
}
