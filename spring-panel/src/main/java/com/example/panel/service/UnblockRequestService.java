package com.example.panel.service;

import com.example.panel.model.clients.UnblockRequestItem;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UnblockRequestService {

    private final JdbcTemplate jdbcTemplate;
    private final BlacklistHistoryService blacklistHistoryService;
    private final DialogNotificationService dialogNotificationService;
    private final UiEventStreamService uiEventStreamService;

    public UnblockRequestService(JdbcTemplate jdbcTemplate,
                                 BlacklistHistoryService blacklistHistoryService,
                                 DialogNotificationService dialogNotificationService,
                                 UiEventStreamService uiEventStreamService) {
        this.jdbcTemplate = jdbcTemplate;
        this.blacklistHistoryService = blacklistHistoryService;
        this.dialogNotificationService = dialogNotificationService;
        this.uiEventStreamService = uiEventStreamService;
    }

    public long countPendingRequests() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM client_unblock_requests WHERE status = 'pending'",
                Long.class
        );
        return count != null ? count : 0L;
    }

    public List<UnblockRequestItem> loadRequests(String status) {
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toLowerCase() : null;
        String sql = """
            SELECT
                r.id,
                r.user_id,
                r.reason,
                r.status,
                r.created_at,
                r.decided_at,
                r.decided_by,
                r.decision_comment,
                c.channel_name
            FROM client_unblock_requests r
            LEFT JOIN channels c ON c.id = r.channel_id
            %s
            ORDER BY CASE WHEN r.status = 'pending' THEN 0 ELSE 1 END, r.created_at DESC LIMIT 300
            """.formatted(StringUtils.hasText(normalizedStatus) ? "WHERE r.status = ?" : "");

        Object[] args = StringUtils.hasText(normalizedStatus) ? new Object[]{normalizedStatus} : new Object[]{};
        return jdbcTemplate.query(
                sql,
                args,
                (rs, rowNum) -> new UnblockRequestItem(
                        rs.getLong("id"),
                        rs.getString("user_id"),
                        rs.getString("channel_name"),
                        rs.getString("reason"),
                        rs.getString("status"),
                        rs.getString("created_at"),
                        rs.getString("decided_at"),
                        rs.getString("decided_by"),
                        rs.getString("decision_comment")
                )
        ).stream().filter(Objects::nonNull).toList();
    }

    @Transactional
    public DecisionOutcome decideRequest(long requestId, boolean approve, String comment, String actor) {
        if (requestId <= 0) {
            return DecisionOutcome.failure("Некорректный запрос");
        }
        Optional<UnblockDecisionContext> contextOpt = loadDecisionContext(requestId, false);
        if (contextOpt.isEmpty()) {
            return DecisionOutcome.failure("Запрос не найден");
        }
        UnblockDecisionContext context = contextOpt.get();
        if (!"pending".equalsIgnoreCase(context.status())) {
            return DecisionOutcome.failure("Запрос уже обработан");
        }
        // Use the same lock order as /unblock creation and direct blacklist mutations.
        boolean currentlyBlocked = Boolean.TRUE.equals(jdbcTemplate.query(
            "SELECT is_blacklisted FROM client_blacklist WHERE user_id = ? FOR UPDATE",
            rs -> rs.next() ? rs.getBoolean("is_blacklisted") : false, context.userId()));
        Optional<UnblockDecisionContext> locked = loadDecisionContext(requestId, true);
        if (locked.isEmpty() || !Objects.equals(context.userId(), locked.get().userId())
                || !"pending".equalsIgnoreCase(locked.get().status())) {
            return DecisionOutcome.failure("Запрос уже обработан или изменён");
        }
        OffsetDateTime now = OffsetDateTime.now();
        String decidedBy = StringUtils.hasText(actor) ? actor : "system";
        String normalizedComment = StringUtils.hasText(comment) ? comment.trim() : null;

        if (!currentlyBlocked) {
            clearUnblockRequestFlag(context.userId());
            String explanation = "Клиент уже разблокирован; заявка утратила актуальность";
            finishDecision(requestId, "expired", now, decidedBy,
                normalizedComment == null ? explanation : explanation + ". " + normalizedComment);
            publishDecisionAfterCommit();
            return DecisionOutcome.success(explanation);
        }

        if (approve) {
            updateBlacklistStatus(context.userId(), false);
            blacklistHistoryService.recordEvent(context.userId(), "unblocked", normalizedComment, decidedBy, now);
        } else {
            clearUnblockRequestFlag(context.userId());
        }

        finishDecision(requestId, approve ? "approved" : "rejected", now, decidedBy, normalizedComment);

        String notification = buildNotificationMessage(context.userId(), approve, normalizedComment, now);
        dialogNotificationService.notifyUserByLastChannel(parseUserId(context.userId()), notification, true);
        publishDecisionAfterCommit();
        return DecisionOutcome.success(approve ? "Запрос одобрен" : "Запрос отклонен");
    }

    private void finishDecision(long requestId, String status, OffsetDateTime now,
                                String actor, String comment) {
        jdbcTemplate.update(
                """
                    UPDATE client_unblock_requests
                    SET status = ?, decided_at = ?, decided_by = ?, decision_comment = ?
                    WHERE id = ?
                    """,
                status,
                now,
                actor,
                comment,
                requestId
        );

    }

    private void publishDecisionAfterCommit() {
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() {
                        try { uiEventStreamService.publishSidebarUnblockChanged("unblock_request_decided"); }
                        catch (RuntimeException unavailable) {
                            org.slf4j.LoggerFactory.getLogger(UnblockRequestService.class).warn("Attention refresh unavailable after unblock decision");
                        }
                    }
                });

    }

    private Optional<UnblockDecisionContext> loadDecisionContext(long requestId, boolean lock) {
        return Optional.ofNullable(jdbcTemplate.query(
                """
                    SELECT id, user_id, status
                    FROM client_unblock_requests
                    WHERE id = ?
                    """ + (lock ? " FOR UPDATE" : ""),
                rs -> rs.next()
                        ? new UnblockDecisionContext(
                                rs.getLong("id"),
                                rs.getString("user_id"),
                                rs.getString("status")
                        )
                        : null,
                requestId
        ));
    }

    private void updateBlacklistStatus(String userId, boolean blocked) {
        int updated = jdbcTemplate.update(
                """
                    UPDATE client_blacklist
                    SET is_blacklisted = ?,
                        unblock_requested = FALSE,
                        unblock_requested_at = NULL
                    WHERE user_id = ?
                    """,
                blocked,
                userId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                        INSERT INTO client_blacklist(user_id, is_blacklisted, unblock_requested, unblock_requested_at)
                        VALUES (?, ?, FALSE, NULL)
                        """,
                    userId,
                    blocked
            );
        }
    }

    private void clearUnblockRequestFlag(String userId) {
        jdbcTemplate.update(
                """
                    UPDATE client_blacklist
                    SET unblock_requested = FALSE,
                        unblock_requested_at = NULL
                    WHERE user_id = ?
                    """,
                userId
        );
    }

    private String buildNotificationMessage(String userId, boolean approved, String comment, OffsetDateTime now) {
        if (approved) {
            String duration = blacklistHistoryService.calculateDurationFromLastBlock(userId, now)
                    .map(blacklistHistoryService::formatDuration)
                    .orElse(null);
            return duration == null
                    ? "Ваш аккаунт разблокирован."
                    : "Ваш аккаунт разблокирован. В блокировке: " + duration + ".";
        }
        String base = "Запрос на разблокировку отклонен.";
        return StringUtils.hasText(comment) ? base + " Причина: " + comment + "." : base;
    }

    private Long parseUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public record DecisionOutcome(boolean success, String message) {
        static DecisionOutcome success(String message) {
            return new DecisionOutcome(true, message);
        }

        static DecisionOutcome failure(String message) {
            return new DecisionOutcome(false, message);
        }
    }

    private record UnblockDecisionContext(long id, String userId, String status) {}
}
