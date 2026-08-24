package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
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

    public DialogReplyTargetService(JdbcTemplate jdbcTemplate,
                                    ChatAttachmentMetadataService chatAttachmentMetadataService) {
        this.jdbcTemplate = jdbcTemplate;
        this.chatAttachmentMetadataService = chatAttachmentMetadataService;
    }

    public Optional<DialogReplyTarget> loadReplyTarget(String ticketId) {
        return jdbcTemplate.query("""
                        SELECT user_id, channel_id
                          FROM messages
                         WHERE ticket_id = ?
                         ORDER BY created_at DESC
                         LIMIT 1
                        """,
                (rs, rowNum) -> new DialogReplyTarget(rs.getLong("user_id"), rs.getLong("channel_id")),
                ticketId
        ).stream().findFirst();
    }

    public boolean hasWebFormSession(String ticketId) {
        if (!StringUtils.hasText(ticketId)) {
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
        if (!StringUtils.hasText(ticketId)) {
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
                try (ResultSet rs = insert.getGeneratedKeys()) {
                    return rs.next() ? rs.getLong(1) : null;
                }
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

    public void touchTicketActivity(String ticketId, String operatorIdentity) {
        if (!StringUtils.hasText(ticketId)) {
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
        return jdbcTemplate.update("""
                UPDATE chat_history
                   SET original_message = COALESCE(original_message, message),
                       message = ?,
                       edited_at = CURRENT_TIMESTAMP
                 WHERE ticket_id = ?
                   AND tg_message_id = ?
                   AND sender = 'operator'
                """, message, ticketId, telegramMessageId);
    }

    public int markOperatorMessageDeleted(String ticketId, Long telegramMessageId) {
        return jdbcTemplate.update("""
                UPDATE chat_history
                   SET deleted_at = CURRENT_TIMESTAMP
                 WHERE ticket_id = ?
                   AND tg_message_id = ?
                   AND sender = 'operator'
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
}
