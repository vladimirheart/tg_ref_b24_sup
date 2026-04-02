package com.example.panel.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OperatorNotificationWatcher {

    private static final Logger log = LoggerFactory.getLogger(OperatorNotificationWatcher.class);

    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notificationService;

    private final AtomicLong lastChatHistoryId = new AtomicLong(0);
    private final AtomicLong lastFeedbackId = new AtomicLong(0);

    public OperatorNotificationWatcher(JdbcTemplate jdbcTemplate,
                                       NotificationService notificationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationService = notificationService;
    }

    @PostConstruct
    void initialize() {
        lastChatHistoryId.set(readMaxId("chat_history"));
        lastFeedbackId.set(readMaxId("feedbacks"));
        log.info("Operator notification watcher initialized (chatHistoryId={}, feedbackId={})",
                lastChatHistoryId.get(), lastFeedbackId.get());
    }

    @Scheduled(fixedDelayString = "${panel.notifications.watch-interval-ms:12000}")
    void watch() {
        watchChatHistoryMessages();
        watchFeedbacks();
    }

    private void watchChatHistoryMessages() {
        long afterId = lastChatHistoryId.get();
        jdbcTemplate.query(
                """
                SELECT id, ticket_id, sender, message, message_type
                  FROM chat_history
                 WHERE id > ?
                 ORDER BY id ASC
                """,
                rs -> {
                    long maxSeen = afterId;
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        if (id > maxSeen) {
                            maxSeen = id;
                        }
                        String ticketId = trimToNull(rs.getString("ticket_id"));
                        String sender = normalizeSender(rs.getString("sender"));
                        String messageType = normalizeSender(rs.getString("message_type"));
                        String message = trimToNull(rs.getString("message"));

                        if (!StringUtils.hasText(ticketId)) {
                            continue;
                        }
                        if (!isExternalDialogEvent(sender, messageType)) {
                            continue;
                        }

                        String text = "Новое сообщение в обращении " + ticketId;
                        if (StringUtils.hasText(message)) {
                            text += ": " + truncate(message, 100);
                        }
                        notificationService.notifyDialogParticipants(
                                ticketId,
                                text,
                                "/dialogs?ticketId=" + ticketId,
                                null
                        );
                    }
                    if (maxSeen > afterId) {
                        lastChatHistoryId.set(maxSeen);
                    }
                },
                afterId
        );
    }

    private void watchFeedbacks() {
        long afterId = lastFeedbackId.get();
        Set<String> columns = loadColumns("feedbacks");
        boolean hasTicketId = columns.contains("ticket_id");
        String sql = hasTicketId
                ? """
                SELECT id, user_id, rating, ticket_id
                  FROM feedbacks
                 WHERE id > ?
                 ORDER BY id ASC
                """
                : """
                SELECT id, user_id, rating
                  FROM feedbacks
                 WHERE id > ?
                 ORDER BY id ASC
                """;
        jdbcTemplate.query(
                sql,
                rs -> {
                    long maxSeen = afterId;
                    while (rs.next()) {
                        long id = rs.getLong("id");
                        if (id > maxSeen) {
                            maxSeen = id;
                        }
                        Long userId = rs.getObject("user_id") != null ? rs.getLong("user_id") : null;
                        Integer rating = rs.getObject("rating") != null ? rs.getInt("rating") : null;
                        String ticketId = hasTicketId ? trimToNull(rs.getString("ticket_id")) : null;
                        if (!StringUtils.hasText(ticketId) && userId != null) {
                            ticketId = resolveLastTicketId(userId);
                        }
                        if (!StringUtils.hasText(ticketId) || rating == null) {
                            continue;
                        }
                        notificationService.notifyDialogParticipants(
                                ticketId,
                                "Новая оценка по обращению " + ticketId + ": " + rating + "/5",
                                "/dialogs?ticketId=" + ticketId,
                                null
                        );
                    }
                    if (maxSeen > afterId) {
                        lastFeedbackId.set(maxSeen);
                    }
                },
                afterId
        );
    }

    private long readMaxId(String table) {
        try {
            Long value = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + table, Long.class);
            return value != null ? value : 0L;
        } catch (Exception ex) {
            log.warn("Unable to read max id from {}: {}", table, ex.getMessage());
            return 0L;
        }
    }

    private Set<String> loadColumns(String tableName) {
        try {
            return jdbcTemplate.execute((Connection connection) -> {
                var columns = new java.util.LinkedHashSet<String>();
                var metaData = connection.getMetaData();
                try (var resultSet = metaData.getColumns(null, null, tableName, null)) {
                    while (resultSet.next()) {
                        columns.add(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                    }
                }
                if (!columns.isEmpty()) {
                    return columns;
                }
                try (var resultSet = metaData.getColumns(null, null, tableName.toUpperCase(Locale.ROOT), null)) {
                    while (resultSet.next()) {
                        columns.add(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                    }
                }
                return columns;
            });
        } catch (Exception ex) {
            return Set.of();
        }
    }

    private String resolveLastTicketId(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            return jdbcTemplate.query(
                    """
                    SELECT ticket_id
                      FROM messages
                     WHERE user_id = ?
                       AND ticket_id IS NOT NULL
                     ORDER BY created_at DESC
                     LIMIT 1
                    """,
                    rs -> rs.next() ? trimToNull(rs.getString("ticket_id")) : null,
                    userId
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isExternalDialogEvent(String sender, String messageType) {
        if ("system_notification".equals(messageType)) {
            return false;
        }
        if (!StringUtils.hasText(sender)) {
            return true;
        }
        return !switch (sender) {
            case "operator", "support", "admin", "system" -> true;
            default -> false;
        };
    }

    private String normalizeSender(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int limit) {
        if (!StringUtils.hasText(value) || value.length() <= limit) {
            return value;
        }
        return value.substring(0, limit) + "...";
    }
}
