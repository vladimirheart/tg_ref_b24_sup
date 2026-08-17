package com.example.panel.service;

import com.example.panel.config.PanelIntegrationTransportMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BotRuntimeBlacklistService {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate botJdbcTemplate;
    private final PanelIntegrationTransportMode integrationTransportMode;
    private final UiEventStreamService uiEventStreamService;

    public BotRuntimeBlacklistService(JdbcTemplate jdbcTemplate,
                                      @Qualifier("botJdbcTemplate") JdbcTemplate botJdbcTemplate,
                                      PanelIntegrationTransportMode integrationTransportMode,
                                      UiEventStreamService uiEventStreamService) {
        this.jdbcTemplate = jdbcTemplate;
        this.botJdbcTemplate = botJdbcTemplate;
        this.integrationTransportMode = integrationTransportMode;
        this.uiEventStreamService = uiEventStreamService;
    }

    @Transactional(readOnly = true)
    public ResolvedBlacklistStatusLookup resolveStatus(Long userId, List<String> aliases) {
        Set<String> candidateKeys = new LinkedHashSet<>();
        if (userId != null && userId > 0) {
            candidateKeys.add(userId.toString());
        }
        if (aliases != null) {
            for (String alias : aliases) {
                if (StringUtils.hasText(alias)) {
                    candidateKeys.add(alias.trim());
                }
            }
        }
        if (candidateKeys.isEmpty()) {
            return new ResolvedBlacklistStatusLookup(null, false, false);
        }

        String numericKey = userId != null && userId > 0 ? userId.toString() : null;
        if (numericKey != null) {
            BlacklistEntry exact = findBlacklistEntry(numericKey).orElse(null);
            if (exact != null) {
                return new ResolvedBlacklistStatusLookup(exact.userId(), exact.blacklisted(), exact.unblockRequested());
            }
        }

        for (String candidateKey : candidateKeys) {
            BlacklistEntry candidate = findBlacklistEntry(candidateKey).orElse(null);
            if (candidate != null) {
                return new ResolvedBlacklistStatusLookup(candidate.userId(), candidate.blacklisted(), candidate.unblockRequested());
            }
        }
        return new ResolvedBlacklistStatusLookup(null, false, false);
    }

    @Transactional
    public UnblockRequestDecisionLookup requestUnblock(Long userId,
                                                       String reason,
                                                       Long channelId,
                                                       Duration cooldown) {
        if (userId == null || userId <= 0) {
            return new UnblockRequestDecisionLookup(null, false, Duration.ZERO);
        }
        String key = userId.toString();
        OffsetDateTime now = OffsetDateTime.now();
        BlacklistEntry current = findBlacklistEntry(key).orElse(null);
        OffsetDateTime lastRequestedAt = current != null ? current.unblockRequestedAt() : null;
        if (cooldown != null && !cooldown.isZero() && !cooldown.isNegative() && lastRequestedAt != null) {
            OffsetDateTime nextAllowed = lastRequestedAt.plus(cooldown);
            if (nextAllowed.isAfter(now)) {
                PendingUnblockRequestLookup existing = findLatestPendingRequest(key)
                    .or(() -> findLatestRequest(key))
                    .orElse(null);
                return new UnblockRequestDecisionLookup(existing, false, Duration.between(now, nextAllowed));
            }
        }

        upsertBlacklistEntry(key, now);
        PendingUnblockRequestLookup saved = upsertPendingRequest(key, reason, channelId, now);
        uiEventStreamService.publishSidebarUnblockChanged("runtime_unblock_request_created");
        return new UnblockRequestDecisionLookup(saved, true, Duration.ZERO);
    }

    @Transactional(readOnly = true)
    public PendingUnblockSummaryLookup pendingSummary(int limit) {
        long count = countPendingRequests();
        int safeLimit = Math.max(0, limit);
        List<PendingUnblockRequestLookup> recent = safeLimit > 0 ? loadRecentPendingRequests(safeLimit) : List.of();
        return new PendingUnblockSummaryLookup(count, recent);
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expireOldPendingRequests() {
        if (!integrationTransportMode.isRabbitMqMode()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime threshold = now.minusDays(30);
        List<Long> expiredIds = botJdbcTemplate.query(
            """
                SELECT id
                FROM client_unblock_requests
                WHERE status = 'pending' AND created_at < ?
                """,
            (rs, rowNum) -> rs.getLong("id"),
            threshold.toString()
        );
        if (expiredIds.isEmpty()) {
            return;
        }
        int updated = botJdbcTemplate.update(
            """
                UPDATE client_unblock_requests
                SET status = 'expired',
                    decided_at = ?,
                    decision_comment = ?
                WHERE status = 'pending' AND created_at < ?
                """,
            now.toString(),
            "Auto-expired by spring-panel scheduler",
            threshold.toString()
        );
        if (updated > 0) {
            uiEventStreamService.publishSidebarUnblockChanged("runtime_unblock_requests_expired");
        }
    }

    private Optional<BlacklistEntry> findBlacklistEntry(String userId) {
        return Optional.ofNullable(jdbcTemplate.query(
            """
                SELECT user_id, is_blacklisted, unblock_requested, unblock_requested_at
                FROM client_blacklist
                WHERE user_id = ?
                """,
            rs -> rs.next() ? mapBlacklistEntry(rs) : null,
            userId
        ));
    }

    private void upsertBlacklistEntry(String userId, OffsetDateTime now) {
        int updated = jdbcTemplate.update(
            """
                UPDATE client_blacklist
                SET is_blacklisted = 1,
                    unblock_requested = 1,
                    unblock_requested_at = ?
                WHERE user_id = ?
                """,
            now.toString(),
            userId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                """
                    INSERT INTO client_blacklist(user_id, is_blacklisted, unblock_requested, unblock_requested_at)
                    VALUES (?, 1, 1, ?)
                    """,
                userId,
                now.toString()
            );
        }
    }

    private PendingUnblockRequestLookup upsertPendingRequest(String userId,
                                                             String reason,
                                                             Long channelId,
                                                             OffsetDateTime now) {
        PendingUnblockRequestLookup existing = findLatestPendingRequest(userId).orElse(null);
        String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : null;
        if (existing != null && existing.id() != null) {
            botJdbcTemplate.update(
                """
                    UPDATE client_unblock_requests
                    SET channel_id = ?, reason = ?, created_at = ?, status = 'pending', decided_at = NULL, decided_by = NULL, decision_comment = NULL
                    WHERE id = ?
                    """,
                channelId,
                normalizedReason,
                now.toString(),
                existing.id()
            );
            return new PendingUnblockRequestLookup(existing.id(), userId, channelId, normalizedReason, now, "pending");
        }
        botJdbcTemplate.update(
            """
                INSERT INTO client_unblock_requests(user_id, channel_id, reason, created_at, status, decided_at, decided_by, decision_comment)
                VALUES (?, ?, ?, ?, 'pending', NULL, NULL, NULL)
                """,
            userId,
            channelId,
            normalizedReason,
            now.toString()
        );
        return findLatestPendingRequest(userId)
            .orElse(new PendingUnblockRequestLookup(null, userId, channelId, normalizedReason, now, "pending"));
    }

    private long countPendingRequests() {
        Long count = botJdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM client_unblock_requests WHERE status = 'pending'",
            Long.class
        );
        return count != null ? count : 0L;
    }

    private List<PendingUnblockRequestLookup> loadRecentPendingRequests(int limit) {
        return botJdbcTemplate.query(
            """
                SELECT id, user_id, channel_id, reason, created_at, status
                FROM client_unblock_requests
                WHERE status = 'pending'
                ORDER BY created_at DESC
                LIMIT ?
                """,
            (rs, rowNum) -> mapPendingRequest(rs),
            limit
        );
    }

    private Optional<PendingUnblockRequestLookup> findLatestPendingRequest(String userId) {
        return Optional.ofNullable(botJdbcTemplate.query(
            """
                SELECT id, user_id, channel_id, reason, created_at, status
                FROM client_unblock_requests
                WHERE user_id = ? AND status = 'pending'
                ORDER BY id DESC
                LIMIT 1
                """,
            rs -> rs.next() ? mapPendingRequest(rs) : null,
            userId
        ));
    }

    private Optional<PendingUnblockRequestLookup> findLatestRequest(String userId) {
        return Optional.ofNullable(botJdbcTemplate.query(
            """
                SELECT id, user_id, channel_id, reason, created_at, status
                FROM client_unblock_requests
                WHERE user_id = ?
                ORDER BY id DESC
                LIMIT 1
                """,
            rs -> rs.next() ? mapPendingRequest(rs) : null,
            userId
        ));
    }

    private BlacklistEntry mapBlacklistEntry(ResultSet rs) throws SQLException {
        return new BlacklistEntry(
            rs.getString("user_id"),
            rs.getInt("is_blacklisted") != 0,
            rs.getInt("unblock_requested") != 0,
            parseOffsetDateTime(rs.getString("unblock_requested_at"))
        );
    }

    private PendingUnblockRequestLookup mapPendingRequest(ResultSet rs) throws SQLException {
        long rawChannelId = rs.getLong("channel_id");
        Long channelId = rs.wasNull() ? null : rawChannelId;
        return new PendingUnblockRequestLookup(
            rs.getLong("id"),
            rs.getString("user_id"),
            channelId,
            rs.getString("reason"),
            parseOffsetDateTime(rs.getString("created_at")),
            rs.getString("status")
        );
    }

    private OffsetDateTime parseOffsetDateTime(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rawValue.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    public record ResolvedBlacklistStatusLookup(String matchedUserId,
                                                boolean blacklisted,
                                                boolean unblockRequested) {
    }

    public record PendingUnblockRequestLookup(Long id,
                                              String userId,
                                              Long channelId,
                                              String reason,
                                              OffsetDateTime createdAt,
                                              String status) {
    }

    public record UnblockRequestDecisionLookup(PendingUnblockRequestLookup request,
                                               boolean created,
                                               Duration retryAfter) {
    }

    public record PendingUnblockSummaryLookup(long pendingCount,
                                              List<PendingUnblockRequestLookup> recentRequests) {
    }

    private record BlacklistEntry(String userId,
                                  boolean blacklisted,
                                  boolean unblockRequested,
                                  OffsetDateTime unblockRequestedAt) {
    }
}
