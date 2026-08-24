package com.example.panel.service.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.panel.converter.LenientOffsetDateTimeConverter;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationInboundEventInboxService {

    private static final Duration STALE_PROCESSING_TIMEOUT = Duration.ofMinutes(15);
    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public IntegrationInboundEventInboxService(JdbcTemplate jdbcTemplate,
                                               ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean beginProcessing(String eventId,
                                   String eventKind,
                                   String platform,
                                   Long channelId,
                                   String ticketId,
                                   String routingKey,
                                   Object payload,
                                   OffsetDateTime receivedAt) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime effectiveReceivedAt = receivedAt != null ? receivedAt : now;
        int inserted = jdbcTemplate.update("""
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
                    received_at,
                    attempt_count,
                    processing_started_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(event_id) DO NOTHING
                """,
            eventId,
            eventKind,
            platform,
            channelId,
            ticketId,
            "rabbitmq",
            routingKey,
            serialize(payload),
            "processing",
            effectiveReceivedAt,
            1,
            now,
            now
        );
        if (inserted > 0) {
            return true;
        }
        return reclaimExistingEvent(eventId, routingKey, payload, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String eventId) {
        jdbcTemplate.update("""
                UPDATE integration_inbound_event_inbox
                   SET status = ?,
                       processed_at = CURRENT_TIMESTAMP,
                       updated_at = CURRENT_TIMESTAMP,
                       last_error = NULL
                 WHERE event_id = ?
                """, "processed", eventId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(String eventId, Exception exception) {
        jdbcTemplate.update("""
                UPDATE integration_inbound_event_inbox
                   SET status = ?,
                       updated_at = CURRENT_TIMESTAMP,
                       last_error = ?
                 WHERE event_id = ?
                """, "failed", truncateError(exception), eventId);
    }

    private boolean reclaimExistingEvent(String eventId,
                                         String routingKey,
                                         Object payload,
                                         OffsetDateTime now) {
        ExistingInboxRecord record = loadExistingRecord(eventId);
        if (record == null) {
            return false;
        }
        if ("processed".equalsIgnoreCase(record.status())) {
            return false;
        }
        if (!record.isReclaimable(now.minus(STALE_PROCESSING_TIMEOUT))) {
            return false;
        }
        int updated = jdbcTemplate.update("""
                UPDATE integration_inbound_event_inbox
                   SET status = ?,
                       routing_key = ?,
                       payload_json = ?,
                       processing_started_at = ?,
                       updated_at = ?,
                       last_error = NULL,
                       attempt_count = COALESCE(attempt_count, 0) + 1
                 WHERE event_id = ?
                   AND status <> ?
                """,
            "processing",
            routingKey,
            serialize(payload),
            now,
            now,
            eventId,
            "processed"
        );
        return updated > 0;
    }

    private ExistingInboxRecord loadExistingRecord(String eventId) {
        return jdbcTemplate.query("""
                SELECT status, processing_started_at, updated_at, received_at
                  FROM integration_inbound_event_inbox
                 WHERE event_id = ?
                """,
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                return new ExistingInboxRecord(
                    rs.getString("status"),
                    parseOffsetDateTime(rs.getString("processing_started_at")),
                    parseOffsetDateTime(rs.getString("updated_at")),
                    parseOffsetDateTime(rs.getString("received_at"))
                );
            },
            eventId
        );
    }

    private OffsetDateTime parseOffsetDateTime(String rawValue) {
        return DATE_TIME_CONVERTER.convertToEntityAttribute(rawValue);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize integration inbound event payload.", ex);
        }
    }

    private String truncateError(Exception exception) {
        String message = exception == null ? "" : String.valueOf(exception.getMessage());
        if (message.length() <= 2000) {
            return message;
        }
        return message.substring(0, 2000);
    }

    private record ExistingInboxRecord(String status,
                                       OffsetDateTime processingStartedAt,
                                       OffsetDateTime updatedAt,
                                       OffsetDateTime receivedAt) {

        private boolean isReclaimable(OffsetDateTime staleThreshold) {
            if ("failed".equalsIgnoreCase(status) || "received".equalsIgnoreCase(status)) {
                return true;
            }
            if (!"processing".equalsIgnoreCase(status)) {
                return false;
            }
            OffsetDateTime activityAt = processingStartedAt != null
                ? processingStartedAt
                : (updatedAt != null ? updatedAt : receivedAt);
            return activityAt == null || !activityAt.isAfter(staleThreshold);
        }
    }
}
