package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.example.supportbot.config.IntegrationRabbitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class IntegrationTransportOutboxServiceTest {

    private JdbcTemplate jdbcTemplate;
    private IntegrationTransportOutboxService service;
    private IntegrationRabbitProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = Files.createTempFile("bot-outbox-", ".db");
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:sqlite:" + dbFile.toAbsolutePath()
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
            ))
        );

        service.enqueueConversationTicketCreated(event, properties);
        service.dispatchScheduled();

        assertThat(statusOf(event.eventId())).isEqualTo("published");
    }

    private String statusOf(String eventId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM integration_transport_outbox WHERE event_id = ?",
            String.class,
            eventId
        );
    }
}
