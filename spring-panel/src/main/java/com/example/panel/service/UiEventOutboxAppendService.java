package com.example.panel.service;

import java.lang.management.ManagementFactory;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UiEventOutboxAppendService {

    private static final int MAX_COUNTER_VALUE = 999;

    private final JdbcTemplate jdbcTemplate;
    private final long nodeId;
    private final AtomicInteger counter = new AtomicInteger(0);

    public UiEventOutboxAppendService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.nodeId = resolveNodeId();
    }

    public void publishTicketReopened(String ticketId, Long channelId, String text) {
        append("ticket_reopened", ticketId, channelId, text, null, null, null);
    }

    public void publishTicketClosed(String ticketId, Long channelId, String text, boolean automatic) {
        append(automatic ? "ticket_closed_auto" : "ticket_closed", ticketId, channelId, text, null, null, null);
    }

    public void append(String eventType,
                       String ticketId,
                       Long channelId,
                       String messageText,
                       String messageType,
                       String attachment,
                       Integer rating) {
        if (!StringUtils.hasText(eventType) || !StringUtils.hasText(ticketId)) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO ui_event_outbox(
                    id, event_type, ticket_id, channel_id, message_text, message_type, attachment, rating, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                nextEventId(),
                eventType.trim(),
                ticketId.trim(),
                channelId,
                trimToNull(messageText),
                trimToNull(messageType),
                trimToNull(attachment),
                rating,
                OffsetDateTime.now().toString()
        );
    }

    private synchronized long nextEventId() {
        long nowMillis = System.currentTimeMillis();
        int nextCounter = counter.updateAndGet(current -> current >= MAX_COUNTER_VALUE ? 0 : current + 1);
        return (nowMillis * 1_000_000L) + (nodeId * 1_000L) + nextCounter;
    }

    private long resolveNodeId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        return Math.floorMod(runtimeName == null ? 0 : runtimeName.hashCode(), 1_000);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
