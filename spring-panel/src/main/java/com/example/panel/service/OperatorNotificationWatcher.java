package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.time.Duration;
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
    private static final Duration LIVE_MESSAGE_REPLAY_WINDOW = Duration.ofDays(1);
    private static final DateTimeFormatter LOCAL_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final NotificationService notificationService;
    private final DialogAiAssistantService dialogAiAssistantService;
    private final AlertQueueService alertQueueService;
    private final ChannelRepository channelRepository;
    private final DialogAuditService dialogAuditService;
    private final SharedConfigService sharedConfigService;
    private final UiEventStreamService uiEventStreamService;
    @Autowired
    private DialogNotificationService dialogNotificationService;

    private final AtomicLong lastChatHistoryId = new AtomicLong(0);
    private final AtomicLong lastFeedbackId = new AtomicLong(0);

    public OperatorNotificationWatcher(JdbcTemplate jdbcTemplate,
                                       NotificationService notificationService,
                                       DialogAiAssistantService dialogAiAssistantService,
                                       AlertQueueService alertQueueService,
                                       ChannelRepository channelRepository,
                                       DialogAuditService dialogAuditService,
                                       SharedConfigService sharedConfigService,
                                       UiEventStreamService uiEventStreamService) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationService = notificationService;
        this.dialogAiAssistantService = dialogAiAssistantService;
        this.alertQueueService = alertQueueService;
        this.channelRepository = channelRepository;
        this.dialogAuditService = dialogAuditService;
        this.sharedConfigService = sharedConfigService;
        this.uiEventStreamService = uiEventStreamService;
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
                SELECT id, ticket_id, sender, message, message_type, attachment, channel_id, timestamp
                  FROM chat_history
                 WHERE id > ?
                 ORDER BY id ASC
                """,
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> {
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
                        Long channelId = rs.getObject("channel_id") != null ? rs.getLong("channel_id") : null;
                        String timestampRaw = trimToNull(rs.getString("timestamp"));
                        Channel channel = channelId != null ? channelRepository.findById(channelId).orElse(null) : null;

                        if (!StringUtils.hasText(ticketId)) {
                            continue;
                        }
                        if (isInactivityAutoCloseEvent(sender, messageType, message)) {
                            String text = "Диалог " + ticketId + " автоматически закрыт из-за отсутствия активности.";
                            Set<String> recipients = notificationService.findDialogRecipients(ticketId);
                            if (recipients.isEmpty()) {
                                notificationService.notifyAllOperators(text, notificationService.buildDialogUrl(ticketId), null);
                            } else {
                                notificationService.notifyUsers(recipients, text, notificationService.buildDialogUrl(ticketId));
                            }
                            notifySupportChat(channel, text);
                            uiEventStreamService.publishDialogsChanged("dialog_auto_closed", ticketId);
                            continue;
                        }
                        if (!shouldReplayAsLiveMessage(timestampRaw)) {
                            continue;
                        }
                        if (!isExternalDialogEvent(sender, messageType)) {
                            continue;
                        }
                        notifyIncomingClientMessage(ticketId, channel, message);
                        dialogAiAssistantService.processIncomingClientMessage(ticketId, message, messageType, attachment);
                    }
                    if (maxSeen > afterId) {
                        lastChatHistoryId.set(maxSeen);
                    }
                    return null;
                },
                afterId
        );
    }

    private void notifyIncomingClientMessage(String ticketId, Channel channel, String message) {
        if (!StringUtils.hasText(ticketId)) {
            return;
        }
        boolean handledByQueue = channel != null
                && alertQueueService.notifyIncomingClientMessage(channel, ticketId, message);
        if (handledByQueue) {
            uiEventStreamService.publishDialogsChanged("incoming_client_message", ticketId);
            uiEventStreamService.publishDialogHistoryChanged(ticketId, channel.getId(), "incoming_client_message");
            return;
        }
        String text = "Новое сообщение в обращении " + ticketId;
        if (StringUtils.hasText(message)) {
            text += ": " + truncate(message, 100);
        }
        notificationService.notifyAllOperators(
                text,
                notificationService.buildDialogUrl(ticketId),
                null
        );
        uiEventStreamService.publishDialogsChanged("incoming_client_message", ticketId);
        uiEventStreamService.publishDialogHistoryChanged(ticketId, channel == null ? null : channel.getId(), "incoming_client_message");
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
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> {
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
                                notificationService.buildDialogUrl(ticketId),
                                null
                        );
                        uiEventStreamService.publishDialogsChanged("dialog_feedback_created", ticketId);
                    }
                    if (maxSeen > afterId) {
                        lastFeedbackId.set(maxSeen);
                    }
                    return null;
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
                (org.springframework.jdbc.core.ResultSetExtractor<Void>) rs -> {
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
                        String notificationText = buildFirstResponseOverdueText(channel, ticketId, overdueMinutes);
                        boolean notified = notifySupportChat(channel, notificationText);
                        if (alertQueueService.notifyFirstResponseOverdue(channel, ticketId, overdueMinutes)) {
                            notified = true;
                        }
                        String auditDetail = "channel=" + channelId + ", overdue_minutes=" + overdueMinutes + ", threshold_minutes=" + targetMinutes;
                        if (!notified) {
                            Set<String> fallbackRecipients = notificationService.findAllOperatorRecipients();
                            if (!fallbackRecipients.isEmpty()) {
                                notificationService.notifyUsers(
                                        fallbackRecipients,
                                        notificationText,
                                        notificationService.buildDialogUrl(ticketId)
                                );
                                notified = true;
                                auditDetail += ", route=fallback_all_operators";
                            }
                        }
                        if (notified) {
                            dialogAuditService.logDialogActionAudit(
                                    ticketId,
                                    "notification_watcher",
                                    "first_response_overdue_notification",
                                    "success",
                                    auditDetail
                            );
                            uiEventStreamService.publishDialogsChanged("first_response_overdue", ticketId);
                        }
                    }
                    return null;
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

    private boolean shouldReplayAsLiveMessage(String timestampRaw) {
        OffsetDateTime eventTimestamp = parseTimestamp(timestampRaw);
        if (eventTimestamp == null) {
            return true;
        }
        Duration age = Duration.between(eventTimestamp, OffsetDateTime.now(ZoneOffset.UTC));
        if (age.isNegative()) {
            return true;
        }
        return age.compareTo(LIVE_MESSAGE_REPLAY_WINDOW) <= 0;
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

    private boolean notifySupportChat(Channel channel, String text) {
        return dialogNotificationService != null
                && channel != null
                && StringUtils.hasText(text)
                && dialogNotificationService.notifySupportChat(channel, text);
    }

    private String buildFirstResponseOverdueText(Channel channel, String ticketId, long overdueMinutes) {
        String channelLabel = channel != null && StringUtils.hasText(channel.getChannelName())
                ? channel.getChannelName()
                : "Канал";
        String overdueLabel = overdueMinutes > 0
                ? " Просрочка: " + overdueMinutes + " мин."
                : "";
        return "Первая реакция просрочена (" + channelLabel + ") в обращении " + ticketId + "." + overdueLabel;
    }
}
