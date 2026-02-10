package com.example.panel.service;

import com.example.panel.model.clients.UnblockRequestItem;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UnblockRequestService {

    private final JdbcTemplate botJdbcTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final BlacklistHistoryService blacklistHistoryService;
    private final DialogNotificationService dialogNotificationService;

    public UnblockRequestService(@Qualifier("botJdbcTemplate") JdbcTemplate botJdbcTemplate,
                                 JdbcTemplate jdbcTemplate,
                                 BlacklistHistoryService blacklistHistoryService,
                                 DialogNotificationService dialogNotificationService) {
        this.botJdbcTemplate = botJdbcTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.blacklistHistoryService = blacklistHistoryService;
        this.dialogNotificationService = dialogNotificationService;
    }

    public long countPendingRequests() {
        Long count = botJdbcTemplate.queryForObject(
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
            ORDER BY r.created_at DESC
            """.formatted(StringUtils.hasText(normalizedStatus) ? "WHERE r.status = ?" : "");

        Object[] args = StringUtils.hasText(normalizedStatus) ? new Object[]{normalizedStatus} : new Object[]{};
        return botJdbcTemplate.query(
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

    public DecisionOutcome decideRequest(long requestId, boolean approve, String comment, String actor) {
        if (requestId <= 0) {
            return DecisionOutcome.failure("Некорректный запрос");
        }
        Optional<UnblockDecisionContext> contextOpt = loadDecisionContext(requestId);
        if (contextOpt.isEmpty()) {
            return DecisionOutcome.failure("Запрос не найден");
        }
        UnblockDecisionContext context = contextOpt.get();
        if (!"pending".equalsIgnoreCase(context.status())) {
            return DecisionOutcome.failure("Запрос уже обработан");
        }
        OffsetDateTime now = OffsetDateTime.now();
        String decidedBy = StringUtils.hasText(actor) ? actor : "system";
        String normalizedComment = StringUtils.hasText(comment) ? comment.trim() : null;

        if (approve) {
            updateBlacklistStatus(context.userId(), false);
            blacklistHistoryService.recordEvent(context.userId(), "unblocked", normalizedComment, decidedBy, now);
        } else {
            clearUnblockRequestFlag(context.userId());
        }

        botJdbcTemplate.update(
                """
                    UPDATE client_unblock_requests
                    SET status = ?, decided_at = ?, decided_by = ?, decision_comment = ?
                    WHERE id = ?
                    """,
                approve ? "approved" : "rejected",
                now.toString(),
                decidedBy,
                normalizedComment,
                requestId
        );

        String notification = buildNotificationMessage(context.userId(), approve, normalizedComment, now);
        dialogNotificationService.notifyUserByLastChannel(parseUserId(context.userId()), notification, true);

        return DecisionOutcome.success(approve ? "Запрос одобрен" : "Запрос отклонен");
    }

    private Optional<UnblockDecisionContext> loadDecisionContext(long requestId) {
        return Optional.ofNullable(botJdbcTemplate.query(
                """
                    SELECT id, user_id, status
                    FROM client_unblock_requests
                    WHERE id = ?
                    """,
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
                        unblock_requested = 0,
                        unblock_requested_at = NULL
                    WHERE user_id = ?
                    """,
                blocked ? 1 : 0,
                userId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                        INSERT INTO client_blacklist(user_id, is_blacklisted, unblock_requested, unblock_requested_at)
                        VALUES (?, ?, 0, NULL)
                        """,
                    userId,
                    blocked ? 1 : 0
            );
        }
    }

    private void clearUnblockRequestFlag(String userId) {
        jdbcTemplate.update(
                """
                    UPDATE client_blacklist
                    SET unblock_requested = 0,
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
