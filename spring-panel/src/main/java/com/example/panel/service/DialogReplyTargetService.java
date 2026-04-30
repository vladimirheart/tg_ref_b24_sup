package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class DialogReplyTargetService {

    private final JdbcTemplate jdbcTemplate;

    public DialogReplyTargetService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
        String timestamp = OffsetDateTime.now().toString();
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
        return timestamp;
    }

    public String logOutgoingMediaMessage(DialogReplyTarget target,
                                          String ticketId,
                                          String caption,
                                          String storedName,
                                          String messageType,
                                          Long telegramMessageId) {
        String timestamp = OffsetDateTime.now().toString();
        jdbcTemplate.update("""
                INSERT INTO chat_history(user_id, sender, message, timestamp, ticket_id, message_type, attachment, channel_id, tg_message_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                target.userId(),
                "operator",
                caption != null ? caption : "",
                timestamp,
                ticketId,
                messageType,
                storedName,
                target.channelId(),
                telegramMessageId
        );
        return timestamp;
    }

    public void touchTicketActivity(String ticketId, Long userId) {
        if (!StringUtils.hasText(ticketId) || userId == null) {
            return;
        }
        String identity = Long.toString(userId);
        String timestamp = OffsetDateTime.now().toString();
        int updated = jdbcTemplate.update("""
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
}
