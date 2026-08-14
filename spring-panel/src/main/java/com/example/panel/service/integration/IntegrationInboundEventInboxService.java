package com.example.panel.service.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationInboundEventInboxService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public IntegrationInboundEventInboxService(JdbcTemplate jdbcTemplate,
                                               ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean beginProcessing(InboundClientMessageEvent event, String routingKey) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO integration_inbound_event_inbox (
                        event_id,
                        event_kind,
                        platform,
                        channel_id,
                        ticket_id,
                        transport_source,
                        routing_key,
                        payload_json,
                        status,
                        received_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                event.eventId(),
                event.eventKind(),
                event.platform(),
                event.channelId(),
                event.ticketId(),
                "rabbitmq",
                routingKey,
                serialize(event),
                "received",
                event.occurredAt()
            );
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String eventId) {
        jdbcTemplate.update("""
                UPDATE integration_inbound_event_inbox
                   SET status = ?,
                       processed_at = CURRENT_TIMESTAMP,
                       last_error = NULL
                 WHERE event_id = ?
                """, "processed", eventId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String eventId, Exception exception) {
        jdbcTemplate.update("""
                UPDATE integration_inbound_event_inbox
                   SET status = ?,
                       last_error = ?
                 WHERE event_id = ?
                """, "failed", truncateError(exception), eventId);
    }

    private String serialize(InboundClientMessageEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize inbound client message event payload.", ex);
        }
    }

    private String truncateError(Exception exception) {
        String message = exception == null ? "" : String.valueOf(exception.getMessage());
        if (message.length() <= 2000) {
            return message;
        }
        return message.substring(0, 2000);
    }
}
