package com.example.supportbot.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OutboundTransportDeliveryLedgerService {

    private static final Duration STALE_PROCESSING_TIMEOUT = Duration.ofMinutes(15);

    private final JdbcTemplate jdbcTemplate;

    public OutboundTransportDeliveryLedgerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
    }

    public boolean beginDelivery(String eventId,
                                 String eventKind,
                                 String routingKey,
                                 Long channelId,
                                 Long userId,
                                 String ticketId,
                                 Long requestId) {
        if (!StringUtils.hasText(eventId)) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            jdbcTemplate.update("""
                    INSERT INTO integration_outbound_event_deliveries (
                        event_id,
                        event_kind,
                        routing_key,
                        channel_id,
                        user_id,
                        ticket_id,
                        request_id,
                        status,
                        created_at,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                eventId.trim(),
                trim(eventKind),
                trim(routingKey),
                channelId,
                userId,
                trim(ticketId),
                requestId,
                "processing",
                timestamp(now),
                timestamp(now)
            );
            return true;
        } catch (DataAccessException ex) {
            DeliveryRecord existing = findRecord(eventId);
            if (existing == null) {
                throw ex;
            }
            if ("delivered".equalsIgnoreCase(existing.status())) {
                return false;
            }
            if ("processing".equalsIgnoreCase(existing.status())
                    && existing.updatedAt() != null
                    && existing.updatedAt().plus(STALE_PROCESSING_TIMEOUT).isAfter(now)) {
                return false;
            }
            int updated = jdbcTemplate.update("""
                    UPDATE integration_outbound_event_deliveries
                       SET event_kind = ?,
                           routing_key = ?,
                           channel_id = ?,
                           user_id = ?,
                           ticket_id = ?,
                           request_id = ?,
                           status = ?,
                           last_error = NULL,
                           updated_at = ?
                     WHERE event_id = ?
                    """,
                trim(eventKind),
                trim(routingKey),
                channelId,
                userId,
                trim(ticketId),
                requestId,
                "processing",
                timestamp(now),
                eventId.trim()
            );
            return updated > 0;
        }
    }

    public void markDelivered(String eventId) {
        if (!StringUtils.hasText(eventId)) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                UPDATE integration_outbound_event_deliveries
                   SET status = ?,
                       delivered_at = ?,
                       last_error = NULL,
                       updated_at = ?
                 WHERE event_id = ?
                """,
            "delivered",
            timestamp(now),
            timestamp(now),
            eventId.trim()
        );
    }

    public void markFailed(String eventId, Exception exception) {
        if (!StringUtils.hasText(eventId)) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE integration_outbound_event_deliveries
                   SET status = ?,
                       last_error = ?,
                       updated_at = ?
                 WHERE event_id = ?
                """,
            "failed",
            truncateError(exception),
            timestamp(OffsetDateTime.now(ZoneOffset.UTC)),
            eventId.trim()
        );
    }

    private DeliveryRecord findRecord(String eventId) {
        List<DeliveryRecord> rows = jdbcTemplate.query("""
                SELECT status, updated_at
                  FROM integration_outbound_event_deliveries
                 WHERE event_id = ?
                """,
            (rs, rowNum) -> new DeliveryRecord(
                rs.getString("status"),
                rs.getTimestamp("updated_at") != null
                    ? rs.getTimestamp("updated_at").toInstant().atOffset(ZoneOffset.UTC)
                    : null
            ),
            eventId.trim()
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS integration_outbound_event_deliveries (
                    event_id TEXT PRIMARY KEY,
                    event_kind TEXT,
                    routing_key TEXT,
                    channel_id BIGINT,
                    user_id BIGINT,
                    ticket_id TEXT,
                    request_id BIGINT,
                    status TEXT NOT NULL,
                    last_error TEXT,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    delivered_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_outbound_event_deliveries_status
                ON integration_outbound_event_deliveries(status, updated_at)
                """);
    }

    private Timestamp timestamp(OffsetDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant());
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String truncateError(Exception exception) {
        String message = exception == null ? "" : String.valueOf(exception.getMessage());
        if (message.length() <= 2000) {
            return message;
        }
        return message.substring(0, 2000);
    }

    private record DeliveryRecord(String status, OffsetDateTime updatedAt) {
    }
}
