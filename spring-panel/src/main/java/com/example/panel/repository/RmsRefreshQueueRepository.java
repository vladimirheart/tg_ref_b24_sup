package com.example.panel.repository;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.Optional;

@Repository
public class RmsRefreshQueueRepository {

    public static final String KIND_LICENSE = "license";
    public static final String KIND_NETWORK = "network";
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_RUNNING = "running";

    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();

    private final JdbcTemplate jdbcTemplate;

    public RmsRefreshQueueRepository(@Qualifier("monitoringRuntimeJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RefreshQueueEntry enqueue(String queueKind,
                                     Long monitorId,
                                     boolean withNotifications,
                                     OffsetDateTime requestedAt) {
        OffsetDateTime safeRequestedAt = requestedAt != null ? requestedAt : OffsetDateTime.now(java.time.ZoneOffset.UTC);
        Long key = jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {
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
                return JdbcGeneratedKeySupport.extractGeneratedKey(ps);
            }
        });
        long id = key != null ? key : -1L;
        return new RefreshQueueEntry(id, queueKind, monitorId, withNotifications, STATUS_QUEUED, safeRequestedAt);
    }

    public void markRunning(long id) {
        jdbcTemplate.update(
            "UPDATE rms_refresh_queue SET status = ? WHERE id = ?",
            STATUS_RUNNING,
            id
        );
    }

    public void delete(long id) {
        jdbcTemplate.update(
            "DELETE FROM rms_refresh_queue WHERE id = ?",
            id
        );
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

    private static OffsetDateTime parseOffsetDateTime(String value) {
        return DATE_TIME_CONVERTER.convertToEntityAttribute(value);
    }

    private static Long readLongColumn(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private void bindOffsetDateTime(PreparedStatement ps, int index, OffsetDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
            return;
        }
        ps.setObject(index, value);
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
