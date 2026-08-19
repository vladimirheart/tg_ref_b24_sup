package com.example.panel.repository;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

@Repository
public class RmsRefreshQueueRepository {

    public static final String KIND_LICENSE = "license";
    public static final String KIND_NETWORK = "network";
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_RUNNING = "running";

    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();
    private static final int[] BUSY_RETRY_DELAYS_MS = {150, 350, 750, 1_500};

    private final JdbcTemplate jdbcTemplate;

    public RmsRefreshQueueRepository(@Qualifier("monitoringRuntimeJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RefreshQueueEntry enqueue(String queueKind,
                                     Long monitorId,
                                     boolean withNotifications,
                                     OffsetDateTime requestedAt) {
        OffsetDateTime safeRequestedAt = requestedAt != null ? requestedAt : OffsetDateTime.now(java.time.ZoneOffset.UTC);
        Long key = runWithBusyRetry(() -> jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {
            try (PreparedStatement ps = prepareInsertStatement(connection)) {
                ps.setString(1, queueKind);
                if (monitorId != null) {
                    ps.setLong(2, monitorId);
                } else {
                    ps.setObject(2, null);
                }
                ps.setBoolean(3, withNotifications);
                ps.setString(4, STATUS_QUEUED);
                bindOffsetDateTime(ps, 5, safeRequestedAt);
                ps.executeUpdate();
                return JdbcGeneratedKeySupport.extractGeneratedKey(ps, connection);
            }
        }));
        long id = key != null ? key : -1L;
        return new RefreshQueueEntry(id, queueKind, monitorId, withNotifications, STATUS_QUEUED, safeRequestedAt);
    }

    public void markRunning(long id) {
        runWithBusyRetry(() -> jdbcTemplate.update(
            "UPDATE rms_refresh_queue SET status = ? WHERE id = ?",
            STATUS_RUNNING,
            id
        ));
    }

    public void delete(long id) {
        runWithBusyRetry(() -> jdbcTemplate.update(
            "DELETE FROM rms_refresh_queue WHERE id = ?",
            id
        ));
    }

    public Optional<RefreshQueueEntry> findNextActive(String queueKind) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                SELECT id, queue_kind, monitor_id, with_notifications, status, requested_at
                  FROM rms_refresh_queue
                 WHERE queue_kind = ?
                   AND status IN (?, ?)
                 ORDER BY
                   CASE status WHEN ? THEN 0 ELSE 1 END,
                   requested_at ASC,
                   id ASC
                 LIMIT 1
                """,
                (rs, rowNum) -> new RefreshQueueEntry(
                    rs.getLong("id"),
                    rs.getString("queue_kind"),
                    readLongColumn(rs, "monitor_id"),
                    rs.getBoolean("with_notifications"),
                    rs.getString("status"),
                    parseOffsetDateTime(rs.getString("requested_at"))
                ),
                queueKind,
                STATUS_RUNNING,
                STATUS_QUEUED,
                STATUS_RUNNING
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean hasQueued(String queueKind) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rms_refresh_queue WHERE queue_kind = ? AND status = ?",
            Integer.class,
            queueKind,
            STATUS_QUEUED
        );
        return count != null && count > 0;
    }

    public List<RefreshQueueEntry> findAllActive() {
        return jdbcTemplate.query(
            """
            SELECT id, queue_kind, monitor_id, with_notifications, status, requested_at
              FROM rms_refresh_queue
             WHERE status IN (?, ?)
             ORDER BY queue_kind ASC,
                      CASE status WHEN ? THEN 0 ELSE 1 END,
                      requested_at ASC,
                      id ASC
            """,
            (rs, rowNum) -> new RefreshQueueEntry(
                rs.getLong("id"),
                rs.getString("queue_kind"),
                readLongColumn(rs, "monitor_id"),
                rs.getBoolean("with_notifications"),
                rs.getString("status"),
                parseOffsetDateTime(rs.getString("requested_at"))
            ),
            STATUS_RUNNING,
            STATUS_QUEUED,
            STATUS_RUNNING
        );
    }

    private void runWithBusyRetry(Runnable action) {
        runWithBusyRetry(() -> {
            action.run();
            return null;
        });
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

    private static OffsetDateTime parseOffsetDateTime(String value) {
        return DATE_TIME_CONVERTER.convertToEntityAttribute(value);
    }

    private static Long readLongColumn(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
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
            return;
        }

        // SQLite stores queue timestamps as ISO-8601 text.
        ps.setString(index, value.toString());
    }

    private PreparedStatement prepareInsertStatement(Connection connection) throws java.sql.SQLException {
        return connection.prepareStatement(
            """
            INSERT INTO rms_refresh_queue (
                queue_kind,
                monitor_id,
                with_notifications,
                status,
                requested_at
            ) VALUES (?, ?, ?, ?, ?)
            """,
            Statement.RETURN_GENERATED_KEYS
        );
    }

    public record RefreshQueueEntry(long id,
                                    String queueKind,
                                    Long monitorId,
                                    boolean withNotifications,
                                    String status,
                                    OffsetDateTime requestedAt) {
    }
}
