package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.panel.config.IntegrationRabbitProperties;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.example.panel.support.FeedbackPromptOutboxTestSupport;
import com.example.panel.service.RuntimeCoordinationService;
import com.example.panel.service.LegacyTicketIdJdbcGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

class OutboundFeedbackPromptPublishOutboxServiceTest {

    private JdbcTemplate jdbcTemplate;
    private RabbitTemplate rabbitTemplate;
    private OutboundFeedbackPromptPublishOutboxService service;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate = PostgresqlJdbcTestSupport.freshJdbcTemplate("panel_outbox");
        FeedbackPromptOutboxTestSupport.createSchema(jdbcTemplate);
        jdbcTemplate.execute("ALTER TABLE tickets ALTER COLUMN channel_id SET DEFAULT 42");
        jdbcTemplate.execute("ALTER TABLE tickets ALTER COLUMN is_general SET DEFAULT false");
        jdbcTemplate.update("INSERT INTO tickets (user_id, ticket_id) VALUES (?, ?)", 200L, "T-1");
        FeedbackPromptOutboxTestSupport.request(jdbcTemplate,100L,200L,42L,"T-1");
        rabbitTemplate = mock(RabbitTemplate.class);
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(), any(), any(CorrelationData.class));
        RuntimeCoordinationService coordinationService = mock(RuntimeCoordinationService.class);
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(2);
            action.run();
            return null;
        }).when(coordinationService).runWithLease(anyString(), any(), any(Runnable.class));
        IntegrationRabbitProperties properties = new IntegrationRabbitProperties();
        properties.setOutboundExchange("iguana.integration.outbound");
        service = new OutboundFeedbackPromptPublishOutboxService(
            jdbcTemplate,
            new ObjectMapper(),
            rabbitTemplate,
            properties,
            coordinationService,
            new LegacyTicketIdJdbcGuard(jdbcTemplate)
        );
    }

    @Test
    void enqueueAndDispatchMarksEventPublished() {
        OutboundFeedbackPromptEvent event = new OutboundFeedbackPromptEvent(
            UUID.randomUUID().toString(),
            "feedback.prompt.dispatch",
            UUID.randomUUID().toString(),
            "telegram",
            42L,
            100L,
            200L,
            "T-1",
            "Rate it"
        );

        service.enqueue(event, "integration.outbound.feedback.prompt.telegram.channel.42");
        service.dispatchScheduled();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM integration_transport_outbox WHERE event_id = ?",
            String.class,
            event.eventId()
        )).isEqualTo("published");
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "T-MISSING", "T-DUPLICATE", " T-1 "})
    void rejectsUnresolvedLegacyTicketBeforeEnqueue(String ticketId) {
        jdbcTemplate.update("INSERT INTO tickets (user_id, ticket_id) VALUES (?, ?)", 200L, "T-DUPLICATE");
        jdbcTemplate.update("INSERT INTO tickets (user_id, ticket_id) VALUES (?, ?)", 201L, "T-DUPLICATE");
        clearInvocations(rabbitTemplate);

        assertThatThrownBy(() -> service.enqueue(event(ticketId), "feedback.route"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unique legacy ticket id");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM integration_transport_outbox", Integer.class)).isZero();
        verifyNoInteractions(rabbitTemplate);
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "missing", "inconsistent-envelope"})
    void doesNotPublishQueuedEventWhenTicketContextBecomesUnavailable(String scenario) {
        OutboundFeedbackPromptEvent event = event("T-1");
        service.enqueue(event, "feedback.route");
        switch (scenario) {
            case "duplicate" -> jdbcTemplate.update(
                "INSERT INTO tickets (user_id, ticket_id) VALUES (?, ?)", 201L, "T-1"
            );
            case "missing" -> jdbcTemplate.update("DELETE FROM tickets WHERE ticket_id = ?", "T-1");
            case "inconsistent-envelope" -> jdbcTemplate.update(
                "UPDATE integration_transport_outbox SET user_id = ? WHERE event_id = ?", 201L, event.eventId()
            );
            default -> throw new IllegalArgumentException(scenario);
        }
        clearInvocations(rabbitTemplate);

        service.dispatchScheduled();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM integration_transport_outbox WHERE event_id = ?", String.class, event.eventId()
        )).isEqualTo("failed");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT published_at FROM integration_transport_outbox WHERE event_id = ?", java.sql.Timestamp.class, event.eventId()
        )).isNull();
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void preservesExactStoredTicketIdAcrossEnqueueAndDispatch() {
        jdbcTemplate.update("INSERT INTO tickets (user_id, ticket_id) VALUES (?, ?)", 200L, " T-SPACED ");
        jdbcTemplate.update("DELETE FROM pending_feedback_requests");
        FeedbackPromptOutboxTestSupport.request(jdbcTemplate,100L,200L,42L," T-SPACED ");
        OutboundFeedbackPromptEvent event = event(" T-SPACED ");

        service.enqueue(event, "feedback.route");
        service.dispatchScheduled();

        assertThat(jdbcTemplate.queryForObject(
            "SELECT ticket_id FROM integration_transport_outbox WHERE event_id = ?", String.class, event.eventId()
        )).isEqualTo(" T-SPACED ");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM integration_transport_outbox WHERE event_id = ?", String.class, event.eventId()
        )).isEqualTo("published");
    }

    private OutboundFeedbackPromptEvent event(String ticketId) {
        return new OutboundFeedbackPromptEvent(
            UUID.randomUUID().toString(), "feedback.prompt.dispatch", UUID.randomUUID().toString(),
            "telegram", 42L, 100L, 200L, ticketId, "Rate it"
        );
    }
}
