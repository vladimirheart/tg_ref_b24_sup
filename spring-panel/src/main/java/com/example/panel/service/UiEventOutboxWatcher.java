package com.example.panel.service;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UiEventOutboxWatcher {

    private final JdbcTemplate jdbcTemplate;
    private final DialogRealtimeEventService dialogRealtimeEventService;
    private final AtomicLong lastProcessedId = new AtomicLong(0L);

    public UiEventOutboxWatcher(JdbcTemplate jdbcTemplate,
                                DialogRealtimeEventService dialogRealtimeEventService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialogRealtimeEventService = dialogRealtimeEventService;
        ensureSchema();
    }

    @PostConstruct
    void initialize() {
        lastProcessedId.set(readMaxId());
    }

    @Scheduled(fixedDelayString = "${panel.ui-event-outbox.watch-interval-ms:1000}")
    void watch() {
        long afterId = lastProcessedId.get();
        jdbcTemplate.query("""
                SELECT id, event_type, ticket_id, channel_id, message_text, message_type, attachment, rating
                  FROM ui_event_outbox
                 WHERE id > ?
                 ORDER BY id ASC
                """, rs -> {
            long maxSeen = afterId;
            while (rs.next()) {
                long id = rs.getLong("id");
                if (id > maxSeen) {
                    maxSeen = id;
                }
                handleEvent(
                        rs.getString("event_type"),
                        rs.getString("ticket_id"),
                        rs.getObject("channel_id") != null ? rs.getLong("channel_id") : null,
                        rs.getString("message_text"),
                        rs.getString("message_type"),
                        rs.getString("attachment"),
                        rs.getObject("rating") != null ? rs.getInt("rating") : null
                );
            }
            if (maxSeen > afterId) {
                lastProcessedId.set(maxSeen);
            }
        }, afterId);
    }

    @Scheduled(fixedDelayString = "${panel.ui-event-outbox.cleanup-interval-ms:3600000}")
    void cleanup() {
        long safeThresholdId = lastProcessedId.get();
        if (safeThresholdId <= 0) {
            return;
        }
        String thresholdCreatedAt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).toString();
        jdbcTemplate.update("""
                DELETE FROM ui_event_outbox
                 WHERE id <= ?
                   AND created_at < ?
                """, safeThresholdId, thresholdCreatedAt);
    }

    private void handleEvent(String eventType,
                             String ticketId,
                             Long channelId,
                             String messageText,
                             String messageType,
                             String attachment,
                             Integer rating) {
        String normalizedEventType = StringUtils.hasText(eventType) ? eventType.trim().toLowerCase() : "";
        switch (normalizedEventType) {
            case "ticket_created" -> dialogRealtimeEventService.handleTicketCreated(ticketId, channelId, messageText);
            case "client_message_created" -> dialogRealtimeEventService.handleIncomingClientMessage(ticketId, channelId, messageText, messageType, attachment);
            case "client_message_edited" -> dialogRealtimeEventService.handleClientMessageEdited(ticketId, channelId);
            case "feedback_created" -> dialogRealtimeEventService.handleFeedbackCreated(ticketId, rating);
            case "ticket_closed_auto" -> dialogRealtimeEventService.handleTicketAutoClosed(ticketId, channelId, messageText);
            case "ticket_closed" -> dialogRealtimeEventService.handleTicketClosed(ticketId, channelId);
            case "ticket_reopened" -> dialogRealtimeEventService.handleTicketReopened(ticketId, channelId);
            default -> {
                // ignore unknown event types
            }
        }
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

    private long readMaxId() {
        try {
            Long value = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM ui_event_outbox", Long.class);
            return value != null ? value : 0L;
        } catch (RuntimeException ex) {
            return 0L;
        }
    }
}
