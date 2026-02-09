package com.example.panel.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BlacklistHistoryService {

    private final JdbcTemplate jdbcTemplate;

    public BlacklistHistoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordEvent(String userId, String action, String reason, String actor, OffsetDateTime at) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(action)) {
            return;
        }
        OffsetDateTime timestamp = at != null ? at : OffsetDateTime.now();
        jdbcTemplate.update(
                """
                    INSERT INTO client_blacklist_history(user_id, action, reason, actor, created_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                userId,
                action,
                StringUtils.hasText(reason) ? reason.trim() : null,
                StringUtils.hasText(actor) ? actor.trim() : null,
                timestamp.toString()
        );
    }

    public Optional<Duration> calculateDurationFromLastBlock(String userId, OffsetDateTime fallbackEnd) {
        if (!StringUtils.hasText(userId)) {
            return Optional.empty();
        }
        OffsetDateTime blockedAt = loadLastBlockedAt(userId).orElseGet(() -> loadFallbackBlockedAt(userId).orElse(null));
        if (blockedAt == null) {
            return Optional.empty();
        }
        OffsetDateTime end = loadFirstUnblockedAfter(userId, blockedAt).orElseGet(() -> fallbackEnd != null
                ? fallbackEnd
                : OffsetDateTime.now());
        if (end.isBefore(blockedAt)) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(blockedAt, end));
    }

    public String formatDuration(Duration duration) {
        if (duration == null) {
            return null;
        }
        long totalMinutes = Math.max(duration.toMinutes(), 0);
        long days = totalMinutes / (60 * 24);
        long hours = (totalMinutes % (60 * 24)) / 60;
        long minutes = totalMinutes % 60;
        return String.format("%d дн. %d ч. %d мин.", days, hours, minutes);
    }

    private Optional<OffsetDateTime> loadLastBlockedAt(String userId) {
        return jdbcTemplate.query(
                """
                    SELECT created_at
                    FROM client_blacklist_history
                    WHERE user_id = ? AND action = 'blocked'
                    ORDER BY created_at DESC
                    LIMIT 1
                    """,
                rs -> rs.next() ? parseOffsetDateTime(rs.getString("created_at")) : null,
                userId
        ).stream().findFirst();
    }

    private Optional<OffsetDateTime> loadFirstUnblockedAfter(String userId, OffsetDateTime after) {
        if (after == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                """
                    SELECT created_at
                    FROM client_blacklist_history
                    WHERE user_id = ? AND action = 'unblocked' AND created_at >= ?
                    ORDER BY created_at ASC
                    LIMIT 1
                    """,
                rs -> rs.next() ? parseOffsetDateTime(rs.getString("created_at")) : null,
                userId,
                after.toString()
        ).stream().findFirst();
    }

    private Optional<OffsetDateTime> loadFallbackBlockedAt(String userId) {
        return jdbcTemplate.query(
                """
                    SELECT added_at
                    FROM client_blacklist
                    WHERE user_id = ?
                    """,
                rs -> rs.next() ? parseOffsetDateTime(rs.getString("added_at")) : null,
                userId
        ).stream().findFirst();
    }

    private OffsetDateTime parseOffsetDateTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw);
        } catch (Exception ex) {
            return null;
        }
    }
}
