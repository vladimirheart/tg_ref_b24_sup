package com.example.panel.repository;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import com.example.panel.entity.ProviderDeliveryLedgerEntry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

@Repository
public class ProviderDeliveryLedgerRepository {

    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();
    private static final int[] BUSY_RETRY_DELAYS_MS = {150, 350, 750, 1_500};

    private static final RowMapper<ProviderDeliveryLedgerEntry> ROW_MAPPER = (rs, rowNum) -> {
        ProviderDeliveryLedgerEntry item = new ProviderDeliveryLedgerEntry();
        item.setId(rs.getLong("id"));
        item.setChannelId(readLong(rs, "channel_id"));
        item.setTicketId(rs.getString("ticket_id"));
        item.setPlatform(rs.getString("platform"));
        item.setProvider(rs.getString("provider"));
        item.setUserId(readLong(rs, "user_id"));
        item.setSenderKind(rs.getString("sender_kind"));
        item.setMessageKind(rs.getString("message_kind"));
        item.setDeliveryStatus(rs.getString("delivery_status"));
        item.setClassification(rs.getString("classification"));
        item.setSeverityLevel(rs.getString("severity_level"));
        item.setRetryState(rs.getString("retry_state"));
        item.setHttpStatus((Integer) rs.getObject("http_status"));
        item.setProviderErrorCode(rs.getString("provider_error_code"));
        item.setProviderMessage(rs.getString("provider_message"));
        item.setResponseExcerpt(rs.getString("response_excerpt"));
        item.setProviderMessageId(readLong(rs, "provider_message_id"));
        item.setReplyToMessageId(readLong(rs, "reply_to_message_id"));
        item.setDurationMs(readLong(rs, "duration_ms"));
        item.setAttemptedAt(readOffsetDateTime(rs, "attempted_at"));
        return item;
    };

    private final JdbcTemplate jdbcTemplate;

    public ProviderDeliveryLedgerRepository(@Qualifier("monitoringRuntimeJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProviderDeliveryLedgerEntry save(ProviderDeliveryLedgerEntry item) {
        Long key = runWithBusyRetry(() -> jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {
            try (PreparedStatement ps = prepareInsertStatement(connection)) {
                bindCommon(ps, item);
                ps.executeUpdate();
                return JdbcGeneratedKeySupport.extractGeneratedKey(ps, connection);
            }
        }));
        if (key != null) {
            item.setId(key);
        }
        return item;
    }

    public List<ProviderDeliveryLedgerEntry> findRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return jdbcTemplate.query(
            """
            SELECT *
              FROM provider_delivery_ledger
             ORDER BY attempted_at DESC, id DESC
             LIMIT ?
            """,
            ROW_MAPPER,
            safeLimit
        );
    }

    public List<ProviderDeliveryLedgerEntry> findRecentByChannel(long channelId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.query(
            """
            SELECT *
              FROM provider_delivery_ledger
             WHERE channel_id = ?
             ORDER BY attempted_at DESC, id DESC
             LIMIT ?
            """,
            ROW_MAPPER,
            channelId,
            safeLimit
        );
    }

    public List<ChannelAttemptStats> summarizeByChannelSince(OffsetDateTime since) {
        OffsetDateTime effectiveSince = since != null ? since : OffsetDateTime.now().minusHours(24);
        return jdbcTemplate.query(
            """
            SELECT channel_id,
                   COUNT(*) AS total_attempts,
                   SUM(CASE WHEN delivery_status = 'success' THEN 1 ELSE 0 END) AS success_count,
                   SUM(CASE WHEN delivery_status <> 'success' THEN 1 ELSE 0 END) AS failure_count,
                   SUM(CASE WHEN severity_level = 'warning' THEN 1 ELSE 0 END) AS warning_count,
                   SUM(CASE WHEN severity_level = 'critical' THEN 1 ELSE 0 END) AS critical_count,
                   SUM(CASE WHEN classification = 'rate_limited' THEN 1 ELSE 0 END) AS rate_limited_count,
                   SUM(CASE WHEN retry_state = 'transient' THEN 1 ELSE 0 END) AS transient_failure_count,
                   SUM(CASE WHEN retry_state = 'terminal' THEN 1 ELSE 0 END) AS terminal_failure_count,
                   SUM(CASE WHEN classification = 'validation_error' THEN 1 ELSE 0 END) AS validation_error_count,
                   SUM(CASE WHEN classification = 'client_error' THEN 1 ELSE 0 END) AS client_error_count,
                   SUM(CASE WHEN classification = 'provider_error' THEN 1 ELSE 0 END) AS provider_error_count,
                   SUM(CASE WHEN classification = 'timeout' THEN 1 ELSE 0 END) AS timeout_count,
                   SUM(CASE WHEN classification = 'network_error' THEN 1 ELSE 0 END) AS network_error_count,
                   SUM(CASE WHEN classification = 'unknown_error' THEN 1 ELSE 0 END) AS unknown_error_count,
                   MAX(attempted_at) AS last_attempt_at,
                   MAX(CASE WHEN delivery_status = 'success' THEN attempted_at END) AS last_success_at,
                   MAX(CASE WHEN delivery_status <> 'success' THEN attempted_at END) AS last_failure_at
              FROM provider_delivery_ledger
             WHERE attempted_at >= ?
             GROUP BY channel_id
            """,
            (rs, rowNum) -> new ChannelAttemptStats(
                readLong(rs, "channel_id"),
                readLong(rs, "total_attempts"),
                readLong(rs, "success_count"),
                readLong(rs, "failure_count"),
                readLong(rs, "warning_count"),
                readLong(rs, "critical_count"),
                readLong(rs, "rate_limited_count"),
                readLong(rs, "transient_failure_count"),
                readLong(rs, "terminal_failure_count"),
                readLong(rs, "validation_error_count"),
                readLong(rs, "client_error_count"),
                readLong(rs, "provider_error_count"),
                readLong(rs, "timeout_count"),
                readLong(rs, "network_error_count"),
                readLong(rs, "unknown_error_count"),
                readOffsetDateTime(rs, "last_attempt_at"),
                readOffsetDateTime(rs, "last_success_at"),
                readOffsetDateTime(rs, "last_failure_at")
            ),
            effectiveSince
        );
    }

    private PreparedStatement prepareInsertStatement(Connection connection) throws SQLException {
        return connection.prepareStatement(
            """
            INSERT INTO provider_delivery_ledger (
                channel_id, ticket_id, platform, provider, user_id, sender_kind, message_kind,
                delivery_status, classification, severity_level, retry_state, http_status,
                provider_error_code, provider_message, response_excerpt, provider_message_id,
                reply_to_message_id, duration_ms, attempted_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            Statement.RETURN_GENERATED_KEYS
        );
    }

    private void bindCommon(PreparedStatement ps, ProviderDeliveryLedgerEntry item) throws SQLException {
        ps.setLong(1, item.getChannelId());
        ps.setString(2, item.getTicketId());
        ps.setString(3, item.getPlatform());
        ps.setString(4, item.getProvider());
        if (item.getUserId() != null) {
            ps.setLong(5, item.getUserId());
        } else {
            ps.setNull(5, Types.BIGINT);
        }
        ps.setString(6, item.getSenderKind());
        ps.setString(7, item.getMessageKind());
        ps.setString(8, item.getDeliveryStatus());
        ps.setString(9, item.getClassification());
        ps.setString(10, item.getSeverityLevel());
        ps.setString(11, item.getRetryState());
        if (item.getHttpStatus() != null) {
            ps.setInt(12, item.getHttpStatus());
        } else {
            ps.setNull(12, Types.INTEGER);
        }
        ps.setString(13, item.getProviderErrorCode());
        ps.setString(14, item.getProviderMessage());
        ps.setString(15, item.getResponseExcerpt());
        if (item.getProviderMessageId() != null) {
            ps.setLong(16, item.getProviderMessageId());
        } else {
            ps.setNull(16, Types.BIGINT);
        }
        if (item.getReplyToMessageId() != null) {
            ps.setLong(17, item.getReplyToMessageId());
        } else {
            ps.setNull(17, Types.BIGINT);
        }
        if (item.getDurationMs() != null) {
            ps.setLong(18, item.getDurationMs());
        } else {
            ps.setNull(18, Types.BIGINT);
        }
        bindOffsetDateTime(ps, 19, item.getAttemptedAt());
    }

    private void bindOffsetDateTime(PreparedStatement ps, int index, OffsetDateTime value) throws SQLException {
        String databaseProductName = ps.getConnection().getMetaData().getDatabaseProductName();
        boolean postgresql = databaseProductName != null
            && databaseProductName.toLowerCase(Locale.ROOT).contains("postgresql");
        if (value == null) {
            if (postgresql) {
                ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                ps.setNull(index, Types.VARCHAR);
            }
            return;
        }
        if (postgresql) {
            ps.setObject(index, value);
        } else {
            ps.setString(index, value.toString());
        }
    }

    private static OffsetDateTime readOffsetDateTime(java.sql.ResultSet rs, String columnName) {
        try {
            Object value = rs.getObject(columnName);
            if (value == null) {
                return null;
            }
            if (value instanceof OffsetDateTime dateTime) {
                return dateTime;
            }
            if (value instanceof Timestamp timestamp) {
                return timestamp.toInstant().atOffset(ZoneOffset.UTC);
            }
            if (value instanceof LocalDateTime localDateTime) {
                return localDateTime.atOffset(ZoneOffset.UTC);
            }
            return DATE_TIME_CONVERTER.convertToEntityAttribute(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Long readLong(java.sql.ResultSet rs, String columnName) {
        try {
            Object value = rs.getObject(columnName);
            return value instanceof Number number ? number.longValue() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private <T> T runWithBusyRetry(Supplier<T> action) {
        DataAccessException lastException = null;
        for (int attempt = 0; attempt <= BUSY_RETRY_DELAYS_MS.length; attempt++) {
            try {
                return action.get();
            } catch (DataAccessException ex) {
                if (!isBusyException(ex) || attempt == BUSY_RETRY_DELAYS_MS.length) {
                    throw ex;
                }
                lastException = ex;
                sleepBeforeRetry(BUSY_RETRY_DELAYS_MS[attempt]);
            }
        }
        throw lastException;
    }

    private boolean isBusyException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains("SQLITE_BUSY") || message.contains("SQLITE_BUSY_SNAPSHOT"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void sleepBeforeRetry(long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry SQLite write", interrupted);
        }
    }

    public record ChannelAttemptStats(Long channelId,
                                      Long totalAttempts,
                                      Long successCount,
                                      Long failureCount,
                                      Long warningCount,
                                      Long criticalCount,
                                      Long rateLimitedCount,
                                      Long transientFailureCount,
                                      Long terminalFailureCount,
                                      Long validationErrorCount,
                                      Long clientErrorCount,
                                      Long providerErrorCount,
                                      Long timeoutCount,
                                      Long networkErrorCount,
                                      Long unknownErrorCount,
                                      OffsetDateTime lastAttemptAt,
                                      OffsetDateTime lastSuccessAt,
                                      OffsetDateTime lastFailureAt) {
    }
}
