package com.example.panel.service;

import com.example.panel.runtime.RuntimeWorkload;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.config.PanelIntegrationTransportMode;
import com.example.panel.converter.LenientOffsetDateTimeConverter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BotRuntimeBlacklistService {

    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();

    private final JdbcTemplate jdbcTemplate;
    private final PanelIntegrationTransportMode integrationTransportMode;
    private final UiEventStreamService uiEventStreamService;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public BotRuntimeBlacklistService(JdbcTemplate jdbcTemplate,
                                      PanelIntegrationTransportMode integrationTransportMode,
                                      UiEventStreamService uiEventStreamService,
                                      RuntimeCoordinationService runtimeCoordinationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.integrationTransportMode = integrationTransportMode;
        this.uiEventStreamService = uiEventStreamService;
        this.runtimeCoordinationService = runtimeCoordinationService;
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

    @Transactional
    public void expireOldPendingRequests() {
        if (!integrationTransportMode.isRabbitMqMode()) {
            return;
        }
        runtimeCoordinationService.runWithLease("bot-runtime-blacklist-expiry", Duration.ofMinutes(5), this::expireOldPendingRequestsInternal);
    }

    void expireOldPendingRequestsInternal() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime threshold = now.minusDays(30);
        Timestamp thresholdTimestamp = Timestamp.from(threshold.toInstant());
        List<Long> expiredIds = jdbcTemplate.query(
            """
                SELECT id
                FROM client_unblock_requests
                WHERE status = 'pending' AND created_at < ?
                """,
            (rs, rowNum) -> rs.getLong("id"),
            thresholdTimestamp
        );
        if (expiredIds.isEmpty()) {
            return;
        }
        int updated = jdbcTemplate.update(
            """
                UPDATE client_unblock_requests
                SET status = 'expired',
                    decided_at = ?,
                    decision_comment = ?
                WHERE status = 'pending' AND created_at < ?
                """,
            Timestamp.from(now.toInstant()),
            "Auto-expired by spring-panel scheduler",
            thresholdTimestamp
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
        Timestamp timestamp = Timestamp.from(now.toInstant());
        int updated = jdbcTemplate.update(
            """
                UPDATE client_blacklist
                SET is_blacklisted = TRUE,
                    unblock_requested = TRUE,
                    unblock_requested_at = ?
                WHERE user_id = ?
                """,
            timestamp,
            userId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                """
                    INSERT INTO client_blacklist(user_id, is_blacklisted, unblock_requested, unblock_requested_at)
                    VALUES (?, TRUE, TRUE, ?)
                    """,
                userId,
                timestamp
            );
        }
    }

    private PendingUnblockRequestLookup upsertPendingRequest(String userId,
                                                             String reason,
                                                             Long channelId,
                                                             OffsetDateTime now) {
        PendingUnblockRequestLookup existing = findLatestPendingRequest(userId).orElse(null);
        String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : null;
        Timestamp nowTimestamp = Timestamp.from(now.toInstant());
        if (existing != null && existing.id() != null) {
            jdbcTemplate.update(
                """
                    UPDATE client_unblock_requests
                    SET channel_id = ?, reason = ?, created_at = ?, status = 'pending', decided_at = NULL, decided_by = NULL, decision_comment = NULL
                    WHERE id = ?
                    """,
                channelId,
                normalizedReason,
                nowTimestamp,
                existing.id()
            );
            return new PendingUnblockRequestLookup(existing.id(), userId, channelId, normalizedReason, now, "pending");
        }
        jdbcTemplate.update(
            """
                INSERT INTO client_unblock_requests(user_id, channel_id, reason, created_at, status, decided_at, decided_by, decision_comment)
                VALUES (?, ?, ?, ?, 'pending', NULL, NULL, NULL)
                """,
            userId,
            channelId,
            normalizedReason,
            nowTimestamp
        );
        return findLatestPendingRequest(userId)
            .orElse(new PendingUnblockRequestLookup(null, userId, channelId, normalizedReason, now, "pending"));
    }

    private long countPendingRequests() {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM client_unblock_requests WHERE status = 'pending'",
            Long.class
        );
        return count != null ? count : 0L;
    }

    private List<PendingUnblockRequestLookup> loadRecentPendingRequests(int limit) {
        return jdbcTemplate.query(
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
        return Optional.ofNullable(jdbcTemplate.query(
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
        return Optional.ofNullable(jdbcTemplate.query(
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
            rs.getBoolean("is_blacklisted"),
            rs.getBoolean("unblock_requested"),
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
        return DATE_TIME_CONVERTER.convertToEntityAttribute(rawValue);
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
