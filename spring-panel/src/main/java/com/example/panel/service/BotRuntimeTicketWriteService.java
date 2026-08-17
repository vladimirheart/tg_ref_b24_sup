package com.example.panel.service;

import com.example.panel.entity.Feedback;
import com.example.panel.entity.PendingFeedbackRequest;
import com.example.panel.repository.FeedbackRepository;
import com.example.panel.repository.PendingFeedbackRequestRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BotRuntimeTicketWriteService {

    private static final Logger log = LoggerFactory.getLogger(BotRuntimeTicketWriteService.class);
    static final String REOPEN_EVENT_TEXT = "\u0417\u0430\u044f\u0432\u043a\u0430 \u043f\u0435\u0440\u0435\u043e\u0442\u043a\u0440\u044b\u0442\u0430 \u043e\u043f\u0435\u0440\u0430\u0442\u043e\u0440\u043e\u043c.";

    private final JdbcTemplate jdbcTemplate;
    private final DialogReplyTargetService dialogReplyTargetService;
    private final DialogResponsibilityService dialogResponsibilityService;
    private final DialogParticipantService dialogParticipantService;
    private final UiEventOutboxAppendService uiEventOutboxAppendService;
    private final PendingFeedbackRequestRepository pendingFeedbackRequestRepository;
    private final FeedbackRepository feedbackRepository;

    public BotRuntimeTicketWriteService(JdbcTemplate jdbcTemplate,
                                        DialogReplyTargetService dialogReplyTargetService,
                                        DialogResponsibilityService dialogResponsibilityService,
                                        DialogParticipantService dialogParticipantService,
                                        UiEventOutboxAppendService uiEventOutboxAppendService,
                                        PendingFeedbackRequestRepository pendingFeedbackRequestRepository,
                                        FeedbackRepository feedbackRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialogReplyTargetService = dialogReplyTargetService;
        this.dialogResponsibilityService = dialogResponsibilityService;
        this.dialogParticipantService = dialogParticipantService;
        this.uiEventOutboxAppendService = uiEventOutboxAppendService;
        this.pendingFeedbackRequestRepository = pendingFeedbackRequestRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional
    public MutationResult reopenTicket(String ticketId) {
        return reopenTicket(ticketId, null);
    }

    @Transactional
    public MutationResult reopenTicket(String ticketId, String operatorIdentity) {
        TicketSnapshot ticket = loadTicket(ticketId);
        if (ticket == null) {
            return new MutationResult(false, false);
        }
        if (!isClosedStatus(ticket.status())) {
            return new MutationResult(false, true);
        }
        int updated = jdbcTemplate.update("""
                UPDATE tickets
                   SET status = 'pending',
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
            assignResponsibleIfPresent(ticket.ticketId(), operatorIdentity);
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
        syncOperatorWorkflowOwnership(normalizedTicketId, operatorIdentity);
        return new MutationResult(true, true);
    }

    @Transactional
    public MutationResult markClientMessageEdited(Long channelId,
                                                  Long telegramMessageId,
                                                  String message) {
        if (channelId == null || telegramMessageId == null || !StringUtils.hasText(message)) {
            return new MutationResult(false, false);
        }
        ClientMessageSnapshot snapshot = loadClientMessage(channelId, telegramMessageId);
        if (snapshot == null) {
            return new MutationResult(false, false);
        }
        int updated = jdbcTemplate.update("""
                UPDATE chat_history
                   SET original_message = COALESCE(original_message, message),
                       message = ?,
                       edited_at = CURRENT_TIMESTAMP
                 WHERE channel_id = ?
                   AND tg_message_id = ?
                   AND sender = 'client'
                """,
                message.trim(),
                channelId,
                telegramMessageId
        );
        if (updated > 0) {
            uiEventOutboxAppendService.publishClientMessageEdited(snapshot.ticketId(), snapshot.channelId(), message);
        }
        return new MutationResult(updated > 0, true);
    }

    @Transactional
    public MutationResult markOperatorMessageEdited(String ticketId,
                                                    Long telegramMessageId,
                                                    String message,
                                                    String operatorIdentity) {
        if (!StringUtils.hasText(ticketId) || telegramMessageId == null || !StringUtils.hasText(message)) {
            return new MutationResult(false, false);
        }
        String normalizedTicketId = ticketId.trim();
        int updated = dialogReplyTargetService.markOperatorMessageEdited(
            normalizedTicketId,
            telegramMessageId,
            message.trim()
        );
        if (updated > 0) {
            assignResponsibleIfPresent(normalizedTicketId, operatorIdentity);
            TicketSnapshot ticket = loadTicket(normalizedTicketId);
            uiEventOutboxAppendService.publishOperatorMessageEdited(
                normalizedTicketId,
                ticket != null ? ticket.channelId() : null,
                message
            );
        }
        return new MutationResult(updated > 0, ticketExists(normalizedTicketId));
    }

    @Transactional
    public MutationResult storeFeedback(Long requestId, Integer rating) {
        if (requestId == null || rating == null || rating < 1 || rating > 5) {
            return new MutationResult(false, false);
        }
        Optional<PendingFeedbackRequest> requestOpt = pendingFeedbackRequestRepository.findById(requestId);
        if (requestOpt.isEmpty()) {
            return new MutationResult(false, false);
        }
        PendingFeedbackRequest request = requestOpt.get();
        OffsetDateTime now = OffsetDateTime.now();
        if (request.getExpiresAt() != null && !request.getExpiresAt().isAfter(now)) {
            return new MutationResult(false, true);
        }
        String ticketId = request.getTicketId();
        Long channelId = request.getChannel() != null ? request.getChannel().getId() : null;
        Feedback feedback = StringUtils.hasText(ticketId)
                ? feedbackRepository.findFirstByTicketIdOrderByTimestampDesc(ticketId).orElseGet(Feedback::new)
                : new Feedback();
        feedback.setUserId(request.getUserId());
        feedback.setTicketId(ticketId);
        feedback.setChannelId(channelId);
        feedback.setRating(rating);
        feedback.setTimestamp(now);
        feedbackRepository.save(feedback);
        uiEventOutboxAppendService.publishFeedbackCreated(ticketId, channelId, rating);
        request.setExpiresAt(now);
        try {
            pendingFeedbackRequestRepository.save(request);
        } catch (RuntimeException ex) {
            log.warn("Saved feedback for ticket {}, but failed to expire pending feedback request {}: {}",
                    ticketId,
                    requestId,
                    ex.getMessage());
        }
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

    private ClientMessageSnapshot loadClientMessage(Long channelId, Long telegramMessageId) {
        return jdbcTemplate.query("""
                SELECT ticket_id, channel_id
                  FROM chat_history
                 WHERE channel_id = ?
                   AND tg_message_id = ?
                   AND sender = 'client'
                 ORDER BY id DESC
                 LIMIT 1
                """,
                rs -> rs.next()
                    ? new ClientMessageSnapshot(
                        rs.getString("ticket_id"),
                        rs.getObject("channel_id") != null ? rs.getLong("channel_id") : channelId
                    )
                    : null,
                channelId,
                telegramMessageId
        );
    }

    private boolean isClosedStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return "resolved".equals(normalized) || "closed".equals(normalized);
    }

    private void syncOperatorWorkflowOwnership(String ticketId, String operatorIdentity) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(operatorIdentity)) {
            return;
        }
        String normalizedOperator = operatorIdentity.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalizedOperator)) {
            return;
        }
        String responsible = dialogResponsibilityService.assignResponsibleIfMissing(ticketId, normalizedOperator);
        if (!StringUtils.hasText(responsible) || normalizedOperator.equalsIgnoreCase(responsible.trim())) {
            return;
        }
        dialogParticipantService.addParticipant(ticketId, normalizedOperator, normalizedOperator);
    }

    private void assignResponsibleIfPresent(String ticketId, String operatorIdentity) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(operatorIdentity)) {
            return;
        }
        String normalizedOperator = operatorIdentity.trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalizedOperator)) {
            return;
        }
        dialogResponsibilityService.assignResponsibleIfMissing(ticketId, normalizedOperator);
    }

    private record TicketSnapshot(String ticketId,
                                  String status,
                                  Long userId,
                                  Long channelId) {
        private String userIdentity() {
            return userId != null ? Long.toString(userId) : null;
        }
    }

    private record ClientMessageSnapshot(String ticketId,
                                         Long channelId) {
    }

    public record MutationResult(boolean updated, boolean exists) {
    }
}
