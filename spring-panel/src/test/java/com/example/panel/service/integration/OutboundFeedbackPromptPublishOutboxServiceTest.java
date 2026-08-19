package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.example.panel.config.IntegrationRabbitProperties;
import com.example.panel.service.RuntimeCoordinationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class OutboundFeedbackPromptPublishOutboxServiceTest {

    private JdbcTemplate jdbcTemplate;
    private RabbitTemplate rabbitTemplate;
    private OutboundFeedbackPromptPublishOutboxService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:panel_outbox_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "sa",
            ""
        ));
        jdbcTemplate.execute("""
                CREATE TABLE integration_transport_outbox (
                    event_id VARCHAR(120) PRIMARY KEY,
                    transport_source VARCHAR(120) NOT NULL,
                    event_kind VARCHAR(120) NOT NULL,
                    exchange_name VARCHAR(255) NOT NULL,
                    routing_key VARCHAR(255) NOT NULL,
                    payload_json CLOB NOT NULL,
                    channel_id BIGINT,
                    user_id BIGINT,
                    ticket_id VARCHAR(255),
                    request_id BIGINT,
                    status VARCHAR(32) NOT NULL,
                    attempt_count INTEGER NOT NULL DEFAULT 0,
                    last_error CLOB,
                    available_at TIMESTAMP,
                    processing_started_at TIMESTAMP,
                    published_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
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
            coordinationService
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
}
