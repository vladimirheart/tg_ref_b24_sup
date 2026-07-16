package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.management.ManagementFactory;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class UiEventOutboxService {

    private static final int MAX_COUNTER_VALUE = 999;

    private final JdbcTemplate jdbcTemplate;
    private final long nodeId;
    private final AtomicInteger counter = new AtomicInteger(0);

    public UiEventOutboxService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.nodeId = resolveNodeId();
        ensureSchema();
    }

    public void publishTicketCreated(String ticketId, Channel channel, String previewText) {
        publish("ticket_created", ticketId, channel, previewText, null, null, null);
    }

    public void publishClientMessage(String ticketId,
                                     Channel channel,
                                     String text,
                                     String messageType,
                                     String attachmentPath) {
        publish("client_message_created", ticketId, channel, text, messageType, attachmentPath, null);
    }

    public void publishClientMessageEdited(String ticketId, Long channelId, String text) {
        publish("client_message_edited", ticketId, channelId, text, null, null, null);
    }

    public void publishFeedbackCreated(String ticketId, Channel channel, Integer rating) {
        publish("feedback_created", ticketId, channel, null, null, null, rating);
    }

    public void publishTicketClosed(String ticketId, Channel channel, String closeReason, boolean automatic) {
        publish(automatic ? "ticket_closed_auto" : "ticket_closed", ticketId, channel, closeReason, null, null, null);
    }

    public void publishTicketReopened(String ticketId, Channel channel, String text) {
        publish("ticket_reopened", ticketId, channel, text, null, null, null);
    }

    private void publish(String eventType,
                         String ticketId,
                         Channel channel,
                         String messageText,
                         String messageType,
                         String attachment,
                         Integer rating) {
        publish(eventType, ticketId, channel == null ? null : channel.getId(), messageText, messageType, attachment, rating);
    }

    private void publish(String eventType,
                         String ticketId,
                         Long channelId,
                         String messageText,
                         String messageType,
                         String attachment,
                         Integer rating) {
        if (!StringUtils.hasText(eventType) || !StringUtils.hasText(ticketId)) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO ui_event_outbox(
                        id, event_type, ticket_id, channel_id, message_text, message_type, attachment, rating, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    nextEventId(),
                    eventType.trim(),
                    ticketId.trim(),
                    channelId,
                    StringUtils.hasText(messageText) ? messageText.trim() : null,
                    StringUtils.hasText(messageType) ? messageType.trim() : null,
                    StringUtils.hasText(attachment) ? attachment.trim() : null,
                    rating,
                    OffsetDateTime.now().toString()
            );
        } catch (RuntimeException ignored) {
            // Outbox must not break the main flow.
        }
    }

    private synchronized long nextEventId() {
        long nowMillis = System.currentTimeMillis();
        int nextCounter = counter.updateAndGet(current -> current >= MAX_COUNTER_VALUE ? 0 : current + 1);
        return (nowMillis * 1_000_000L) + (nodeId * 1_000L) + nextCounter;
    }

    private void ensureSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ui_event_outbox (
                    id BIGINT PRIMARY KEY,
                    event_type TEXT NOT NULL,
                    ticket_id TEXT NOT NULL,
                    channel_id BIGINT,
                    message_text TEXT,
                    message_type TEXT,
                    attachment TEXT,
                    rating INTEGER,
                    created_at TEXT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_ui_event_outbox_ticket
                ON ui_event_outbox(ticket_id, id)
                """);
    }

    private long resolveNodeId() {
        String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
        return Math.floorMod(runtimeName == null ? 0 : runtimeName.hashCode(), 1_000);
    }
}
