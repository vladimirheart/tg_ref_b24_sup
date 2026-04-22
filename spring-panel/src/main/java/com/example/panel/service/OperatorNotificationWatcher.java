package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OperatorNotificationWatcher {

    private static final Logger log = LoggerFactory.getLogger(OperatorNotificationWatcher.class);
    private static final int DEFAULT_FIRST_RESPONSE_TARGET_MINUTES = 24 * 60;
    private static final DateTimeFormatter LOCAL_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notificationService;
    private final DialogAiAssistantService dialogAiAssistantService;
    private final AlertQueueService alertQueueService;
    private final ChannelRepository channelRepository;
    private final DialogAuditService dialogAuditService;
    private final SharedConfigService sharedConfigService;

    private final AtomicLong lastChatHistoryId = new AtomicLong(0);
    private final AtomicLong lastFeedbackId = new AtomicLong(0);

    public OperatorNotificationWatcher(JdbcTemplate jdbcTemplate,
                                       NotificationService notificationService,
                                       DialogAiAssistantService dialogAiAssistantService,
                                       AlertQueueService alertQueueService,
                                       ChannelRepository channelRepository,
                                       DialogAuditService dialogAuditService,
                                       SharedConfigService sharedConfigService) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationService = notificationService;
        this.dialogAiAssistantService = dialogAiAssistantService;
        this.alertQueueService = alertQueueService;
        this.channelRepository = channelRepository;
        this.dialogAuditService = dialogAuditService;
        this.sharedConfigService = sharedConfigService;
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
        watchFirstResponseOverdue();
    }

    private void watchChatHistoryMessages() {
        long afterId = lastChatHistoryId.get();
        jdbcTemplate.query(
                """
                SELECT id, ticket_id, sender, message, message_type, attachment
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
                        String attachment = trimToNull(rs.getString("attachment"));

                        if (!StringUtils.hasText(ticketId)) {
                            continue;
                        }
                        if (isInactivityAutoCloseEvent(sender, messageType, message)) {
                            String text = "Диалог " + ticketId + " автоматически закрыт из-за отсутствия активности.";
                            Set<String> recipients = notificationService.findDialogRecipients(ticketId);
                            if (recipients.isEmpty()) {
                                notificationService.notifyAllOperators(text, "/dialogs?ticketId=" + ticketId, null);
                            } else {
                                notificationService.notifyUsers(recipients, text, "/dialogs?ticketId=" + ticketId);
                            }
                            continue;
                        }
                        if (!isExternalDialogEvent(sender, messageType)) {
                            continue;
                        }
                        boolean initialPublicFormMessage = dialogAuditService.hasSuccessfulDialogAction(ticketId, "public_form_submit")
                                && isFirstExternalMessage(ticketId, id);
                        if (initialPublicFormMessage) {
                            dialogAiAssistantService.processIncomingClientMessage(ticketId, message, messageType, attachment);
                            continue;
                        }

                        String text = "Новое сообщение в обращении " + ticketId;
                        if (StringUtils.hasText(message)) {
                            text += ": " + truncate(message, 100);
                        }
                        notificationService.notifyAllOperators(
                                text,
                                "/dialogs?ticketId=" + ticketId,
                                null
                        );
                        dialogAiAssistantService.processIncomingClientMessage(ticketId, message, messageType, attachment);
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

    private void watchFirstResponseOverdue() {
        int targetMinutes = resolveFirstResponseTargetMinutes();
        jdbcTemplate.query(
                """
                SELECT t.ticket_id,
                       t.channel_id,
                       COALESCE(
                           (
                               SELECT MIN(ch.timestamp)
                                 FROM chat_history ch
                                WHERE ch.ticket_id = t.ticket_id
                                  AND lower(COALESCE(ch.sender, '')) NOT IN ('operator', 'support', 'admin', 'system', 'ai_agent')
                           ),
                           t.created_at
                       ) AS first_client_at
                  FROM tickets t
                 WHERE COALESCE(lower(t.status), 'open') NOT IN ('resolved', 'closed')
                   AND EXISTS (
                       SELECT 1
                         FROM chat_history ch
                        WHERE ch.ticket_id = t.ticket_id
                          AND lower(COALESCE(ch.sender, '')) NOT IN ('operator', 'support', 'admin', 'system', 'ai_agent')
                   )
                   AND NOT EXISTS (
                       SELECT 1
                         FROM chat_history ch
                        WHERE ch.ticket_id = t.ticket_id
                          AND lower(COALESCE(ch.sender, '')) IN ('operator', 'support', 'admin', 'system', 'ai_agent')
                   )
                 ORDER BY t.ticket_id DESC
                """,
                rs -> {
                    while (rs.next()) {
                        String ticketId = trimToNull(rs.getString("ticket_id"));
                        Long channelId = rs.getObject("channel_id") != null ? rs.getLong("channel_id") : null;
                        String firstClientAtRaw = trimToNull(rs.getString("first_client_at"));
                        if (!StringUtils.hasText(ticketId) || channelId == null || !StringUtils.hasText(firstClientAtRaw)) {
                            continue;
                        }
                        if (dialogAuditService.hasSuccessfulDialogAction(ticketId, "first_response_overdue_notification")) {
                            continue;
                        }
                        OffsetDateTime firstClientAt = parseTimestamp(firstClientAtRaw);
                        if (firstClientAt == null) {
                            continue;
                        }
                        long overdueMinutes = java.time.Duration.between(firstClientAt, OffsetDateTime.now(ZoneOffset.UTC)).toMinutes();
                        if (overdueMinutes < targetMinutes) {
                            continue;
                        }
                        Channel channel = channelRepository.findById(channelId).orElse(null);
                        if (channel == null) {
                            continue;
                        }
                        boolean notified = alertQueueService.notifyFirstResponseOverdue(channel, ticketId, overdueMinutes);
                        if (notified) {
                            dialogAuditService.logDialogActionAudit(
                                    ticketId,
                                    "notification_watcher",
                                    "first_response_overdue_notification",
                                    "success",
                                    "channel=" + channelId + ", overdue_minutes=" + overdueMinutes + ", threshold_minutes=" + targetMinutes
                            );
                        }
                    }
                }
        );
    }

    private int resolveFirstResponseTargetMinutes() {
        try {
            Map<String, Object> settings = sharedConfigService.loadSettings();
            Object dialogConfigRaw = settings.get("dialog_config");
            if (dialogConfigRaw instanceof Map<?, ?> dialogConfig) {
                Object raw = dialogConfig.get("sla_target_minutes");
                if (raw instanceof Number number && number.intValue() > 0) {
                    return number.intValue();
                }
                if (raw != null) {
                    int parsed = Integer.parseInt(String.valueOf(raw).trim());
                    if (parsed > 0) {
                        return parsed;
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Unable to resolve sla_target_minutes for notification watcher: {}", ex.getMessage());
        }
        return DEFAULT_FIRST_RESPONSE_TARGET_MINUTES;
    }

    private boolean isFirstExternalMessage(String ticketId, long messageId) {
        if (!StringUtils.hasText(ticketId) || messageId <= 0) {
            return false;
        }
        try {
            Long count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                      FROM chat_history
                     WHERE ticket_id = ?
                       AND id < ?
                       AND lower(COALESCE(sender, '')) NOT IN ('operator', 'support', 'admin', 'system', 'ai_agent')
                    """,
                    Long.class,
                    ticketId.trim(),
                    messageId);
            return count != null && count == 0L;
        } catch (Exception ex) {
            return false;
        }
    }

    private OffsetDateTime parseTimestamp(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(value.replace(' ', 'T') + "Z");
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value.replace('T', ' '), LOCAL_TIMESTAMP_FORMATTER).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
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

    private boolean isInactivityAutoCloseEvent(String sender, String messageType, String message) {
        if (!"system".equals(sender)) {
            return false;
        }
        if (!"system_event".equals(messageType) && !"system_notification".equals(messageType)) {
            return false;
        }
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("автоматически закрыт")
                && normalized.contains("отсутств")
                && normalized.contains("активнос");
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
