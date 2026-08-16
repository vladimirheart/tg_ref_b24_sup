package com.example.panel.service;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BotRuntimeTicketWriteService {

    static final String REOPEN_EVENT_TEXT = "Заявка переоткрыта оператором.";

    private final JdbcTemplate jdbcTemplate;
    private final DialogReplyTargetService dialogReplyTargetService;
    private final UiEventOutboxAppendService uiEventOutboxAppendService;

    public BotRuntimeTicketWriteService(JdbcTemplate jdbcTemplate,
                                        DialogReplyTargetService dialogReplyTargetService,
                                        UiEventOutboxAppendService uiEventOutboxAppendService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialogReplyTargetService = dialogReplyTargetService;
        this.uiEventOutboxAppendService = uiEventOutboxAppendService;
    }

    @Transactional
    public MutationResult reopenTicket(String ticketId) {
        TicketSnapshot ticket = loadTicket(ticketId);
        if (ticket == null) {
            return new MutationResult(false, false);
        }
        if (!isClosedStatus(ticket.status())) {
            return new MutationResult(false, true);
        }
        int updated = jdbcTemplate.update("""
                UPDATE tickets
                   SET status = 'open',
                       resolved_at = NULL,
                       resolved_by = NULL,
                       reopen_count = COALESCE(reopen_count, 0) + 1,
                       last_reopen_at = CURRENT_TIMESTAMP
                 WHERE ticket_id = ?
                   AND lower(COALESCE(status, '')) IN ('resolved', 'closed')
                """,
                ticket.ticketId()
        );
        if (updated > 0) {
            dialogReplyTargetService.touchTicketActivity(ticket.ticketId(), ticket.userIdentity());
            storeSystemEvent(ticket, REOPEN_EVENT_TEXT);
            uiEventOutboxAppendService.publishTicketReopened(ticket.ticketId(), ticket.channelId(), REOPEN_EVENT_TEXT);
        }
        return new MutationResult(updated > 0, true);
    }

    @Transactional
    public MutationResult registerActivity(String ticketId, String userIdentity) {
        if (!StringUtils.hasText(ticketId)) {
            return new MutationResult(false, false);
        }
        dialogReplyTargetService.touchTicketActivity(ticketId.trim(), userIdentity);
        return new MutationResult(true, ticketExists(ticketId));
    }

    @Transactional
    public MutationResult clearActivity(String ticketId) {
        if (!StringUtils.hasText(ticketId)) {
            return new MutationResult(false, false);
        }
        int updated = jdbcTemplate.update("DELETE FROM ticket_active WHERE ticket_id = ?", ticketId.trim());
        return new MutationResult(updated > 0, ticketExists(ticketId));
    }

    @Transactional
    public MutationResult recordOperatorRelay(String ticketId,
                                              String message,
                                              Long telegramMessageId,
                                              Long replyToTelegramId,
                                              String operatorIdentity) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(message)) {
            return new MutationResult(false, false);
        }
        String normalizedTicketId = ticketId.trim();
        Optional<DialogReplyTarget> targetOpt = dialogReplyTargetService.loadReplyTarget(normalizedTicketId);
        if (targetOpt.isEmpty()) {
            return new MutationResult(false, ticketExists(normalizedTicketId));
        }
        dialogReplyTargetService.logOutgoingMessage(
                targetOpt.get(),
                normalizedTicketId,
                message.trim(),
                "operator_message",
                telegramMessageId,
                replyToTelegramId,
                "operator"
        );
        dialogReplyTargetService.touchTicketActivity(normalizedTicketId, operatorIdentity);
        return new MutationResult(true, true);
    }

    private void storeSystemEvent(TicketSnapshot ticket, String text) {
        jdbcTemplate.update("""
                INSERT INTO chat_history(user_id, sender, message, timestamp, ticket_id, message_type, channel_id)
                VALUES (?, 'system', ?, ?, ?, 'system_event', ?)
                """,
                ticket.userId(),
                text,
                OffsetDateTime.now().toString(),
                ticket.ticketId(),
                ticket.channelId()
        );
    }

    private TicketSnapshot loadTicket(String ticketId) {
        if (!StringUtils.hasText(ticketId)) {
            return null;
        }
        return jdbcTemplate.query("""
                SELECT ticket_id, status, user_id, channel_id
                  FROM tickets
                 WHERE ticket_id = ?
                 LIMIT 1
                """,
                rs -> rs.next()
                    ? new TicketSnapshot(
                        rs.getString("ticket_id"),
                        rs.getString("status"),
                        rs.getObject("user_id") != null ? rs.getLong("user_id") : null,
                        rs.getObject("channel_id") != null ? rs.getLong("channel_id") : null
                    )
                    : null,
                ticketId.trim()
        );
    }

    private boolean ticketExists(String ticketId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tickets WHERE ticket_id = ?",
                Integer.class,
                ticketId.trim()
        );
        return count != null && count > 0;
    }

    private boolean isClosedStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return "resolved".equals(normalized) || "closed".equals(normalized);
    }

    private record TicketSnapshot(String ticketId,
                                  String status,
                                  Long userId,
                                  Long channelId) {
        private String userIdentity() {
            return userId != null ? Long.toString(userId) : null;
        }
    }

    public record MutationResult(boolean updated, boolean exists) {
    }
}
