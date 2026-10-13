package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class DialogReplyTargetService {

    private final JdbcTemplate jdbcTemplate;
    private final ChatAttachmentMetadataService chatAttachmentMetadataService;
    private final LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard;

    public DialogReplyTargetService(JdbcTemplate jdbcTemplate,
                                    ChatAttachmentMetadataService chatAttachmentMetadataService,
                                    LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard) {
        this.jdbcTemplate = jdbcTemplate;
        this.chatAttachmentMetadataService = chatAttachmentMetadataService;
        this.legacyTicketIdJdbcGuard = legacyTicketIdJdbcGuard;
    }

    public Optional<DialogReplyTarget> loadReplyTarget(String ticketId) {
        if (!legacyTicketIdJdbcGuard.hasUniqueTicket(ticketId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                        SELECT m.user_id, m.channel_id
                          FROM messages m
                          JOIN tickets t ON t.ticket_id = m.ticket_id
                                        AND t.user_id = m.user_id
                                        AND t.channel_id = m.channel_id
                         WHERE m.ticket_id = ?
                         ORDER BY m.created_at DESC
                         LIMIT 1
                        """,
                (rs, rowNum) -> new DialogReplyTarget(rs.getLong("user_id"), rs.getLong("channel_id")),
                ticketId
        ).stream().findFirst();
    }

    public boolean hasWebFormSession(String ticketId) {
        if (!legacyTicketIdJdbcGuard.hasUniqueTicket(ticketId)) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM web_form_sessions WHERE ticket_id = ?",
                Integer.class,
                ticketId
        );
        return count != null && count > 0;
    }

    public String logOutgoingMessage(DialogReplyTarget target,
                                     String ticketId,
                                     String message,
                                     String messageType,
                                     Long telegramMessageId,
                                     Long replyToTelegramId,
                                     String sender) {
        requireUniqueTicket(ticketId, target);
        OffsetDateTime timestamp = OffsetDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO chat_history(user_id, sender, message, timestamp, ticket_id, message_type, channel_id, tg_message_id, reply_to_tg_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                target.userId(),
                normalizeSender(sender),
                message,
                timestamp,
                ticketId,
                messageType,
                target.channelId(),
                telegramMessageId,
                replyToTelegramId
        );
        return timestamp.toString();
    }

    public Long nextLocalTelegramMessageId(String ticketId) {
        if (!legacyTicketIdJdbcGuard.hasUniqueTicket(ticketId)) {
            return 1L;
        }
        Long max = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(tg_message_id), 0)
                  FROM chat_history
                 WHERE ticket_id = ?
                """, Long.class, ticketId);
        return (max != null ? max : 0L) + 1L;
    }

    public String logOutgoingMediaMessage(DialogReplyTarget target,
                                          String ticketId,
                                          String caption,
                                          String storedName,
                                          String originalName,
                                          String mimeType,
                                          Long size,
                                          String messageType,
                                          Long telegramMessageId,
                                          Long replyToTelegramId) {
        requireUniqueTicket(ticketId, target);
        OffsetDateTime timestamp = OffsetDateTime.now();
        Long chatHistoryId = jdbcTemplate.execute((Connection connection) -> {
            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO chat_history(user_id, sender, message, timestamp, ticket_id, message_type, attachment, channel_id, tg_message_id, reply_to_tg_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                insert.setLong(1, target.userId());
                insert.setString(2, "operator");
                insert.setString(3, caption != null ? caption : "");
                insert.setObject(4, timestamp);
                insert.setString(5, ticketId);
                insert.setString(6, messageType);
                insert.setString(7, storedName);
                insert.setLong(8, target.channelId());
                insert.setObject(9, telegramMessageId);
                insert.setObject(10, replyToTelegramId);
                insert.executeUpdate();
                return extractGeneratedKey(insert);
            }
        });
        if (chatHistoryId != null && chatHistoryId > 0) {
            chatAttachmentMetadataService.upsertForChatHistory(
                    chatHistoryId,
                    ticketId,
                    target.channelId(),
                    storedName,
                    originalName,
                    mimeType,
                    size,
                    messageType
            );
        }
        return timestamp.toString();
    }

    public boolean hasReplyTargetOwner(String ticketId, DialogReplyTarget target) {
        if (target == null || target.userId() == null || target.channelId() == null
                || !legacyTicketIdJdbcGuard.hasUniqueTicket(ticketId)) {
            return false;
        }
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM tickets
                     WHERE ticket_id = ? AND user_id = ? AND channel_id = ?
                    """, Integer.class, ticketId, target.userId(), target.channelId());
            return count != null && count == 1;
        } catch (DataAccessException ex) {
            // A schema/lookup failure cannot authorize an outbound recipient or child write.
            return false;
        }
    }

    public boolean hasOperatorMessageOwner(String ticketId, DialogReplyTarget target, Long telegramMessageId) {
        if (telegramMessageId == null || !hasReplyTargetOwner(ticketId, target)) {
            return false;
        }
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM chat_history
                     WHERE ticket_id = ? AND user_id = ? AND channel_id = ?
                       AND tg_message_id = ? AND sender = 'operator'
                    """, Integer.class, ticketId, target.userId(), target.channelId(), telegramMessageId);
            return count != null && count == 1;
        } catch (DataAccessException ex) {
            // An unverified history row cannot authorize a provider edit/delete.
            return false;
        }
    }

    private void requireUniqueTicket(String ticketId, DialogReplyTarget target) {
        if (!hasReplyTargetOwner(ticketId, target)) {
            throw new IllegalArgumentException("Outgoing message requires a unique legacy ticket id.");
        }
    }

    public void touchTicketActivity(String ticketId, String operatorIdentity) {
        if (!legacyTicketIdJdbcGuard.hasUniqueTicket(ticketId)) {
            return;
        }
        String identity = normalizeOperatorIdentity(operatorIdentity);
        OffsetDateTime timestamp = OffsetDateTime.now();
        int updated;
        if (StringUtils.hasText(identity)) {
            updated = jdbcTemplate.update("""
                    UPDATE ticket_active
                       SET last_seen = ?,
                           user_identity = CASE
                               WHEN user_identity IS NULL OR trim(user_identity) = '' THEN ?
                               ELSE user_identity
                           END
                     WHERE ticket_id = ?
                    """,
                    timestamp,
                    identity,
                    ticketId
            );
        } else {
            updated = jdbcTemplate.update("""
                    UPDATE ticket_active
                       SET last_seen = ?
                     WHERE ticket_id = ?
                    """,
                    timestamp,
                    ticketId
            );
        }
        if (updated == 0) {
            jdbcTemplate.update("""
                    INSERT INTO ticket_active(ticket_id, user_identity, last_seen)
                    VALUES (?, ?, ?)
                    """,
                    ticketId,
                    identity,
                    timestamp
            );
        }
    }

    public int markOperatorMessageEdited(String ticketId, Long telegramMessageId, String message) {
        if (!legacyTicketIdJdbcGuard.hasUniqueTicket(ticketId)) {
            return 0;
        }
        return jdbcTemplate.update("""
                UPDATE chat_history
                   SET original_message = COALESCE(original_message, message),
                       message = ?,
                       edited_at = CURRENT_TIMESTAMP
                 WHERE ticket_id = ?
                   AND tg_message_id = ?
                   AND sender = 'operator'
                   AND EXISTS (
                       SELECT 1 FROM tickets t
                        WHERE t.ticket_id = chat_history.ticket_id
                          AND t.user_id = chat_history.user_id
                          AND t.channel_id = chat_history.channel_id
                   )
                """, message, ticketId, telegramMessageId);
    }

    public int markOperatorMessageDeleted(String ticketId, Long telegramMessageId) {
        if (!legacyTicketIdJdbcGuard.hasUniqueTicket(ticketId)) {
            return 0;
        }
        return jdbcTemplate.update("""
                UPDATE chat_history
                   SET deleted_at = CURRENT_TIMESTAMP
                 WHERE ticket_id = ?
                   AND tg_message_id = ?
                   AND sender = 'operator'
                   AND EXISTS (
                       SELECT 1 FROM tickets t
                        WHERE t.ticket_id = chat_history.ticket_id
                          AND t.user_id = chat_history.user_id
                          AND t.channel_id = chat_history.channel_id
                   )
                """, ticketId, telegramMessageId);
    }

    String normalizeSender(String sender) {
        String normalized = StringUtils.hasText(sender) ? sender.trim().toLowerCase() : "";
        return switch (normalized) {
            case "operator", "support", "admin", "system", "ai_agent" -> normalized;
            default -> "operator";
        };
    }

    private String normalizeOperatorIdentity(String operatorIdentity) {
        if (!StringUtils.hasText(operatorIdentity)) {
            return null;
        }
        String normalized = operatorIdentity.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private Long extractGeneratedKey(PreparedStatement statement) throws java.sql.SQLException {
        try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
            if (generatedKeys != null && generatedKeys.next()) {
                return generatedKeys.getLong(1);
            }
        }
        return null;
    }
}
