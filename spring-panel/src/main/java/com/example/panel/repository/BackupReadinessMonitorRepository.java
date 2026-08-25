package com.example.panel.repository;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import com.example.panel.entity.BackupReadinessMonitor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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
public class BackupReadinessMonitorRepository {

    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();
    private static final int[] BUSY_RETRY_DELAYS_MS = {150, 350, 750, 1_500};

    private static final RowMapper<BackupReadinessMonitor> ROW_MAPPER = (rs, rowNum) -> {
        BackupReadinessMonitor item = new BackupReadinessMonitor();
        item.setId(rs.getLong("id"));
        item.setMonitorName(rs.getString("monitor_name"));
        item.setBackupKind(rs.getString("backup_kind"));
        item.setPathPattern(rs.getString("path_pattern"));
        item.setEnabled(rs.getBoolean("enabled"));
        item.setFreshnessThresholdHours(rs.getInt("freshness_threshold_hours"));
        item.setRestoreThresholdDays(rs.getInt("restore_threshold_days"));
        item.setLastStatus(rs.getString("last_status"));
        item.setLastSummary(rs.getString("last_summary"));
        item.setLastErrorMessage(rs.getString("last_error_message"));
        item.setLastBackupAt(parseOffsetDateTime(rs.getString("last_backup_at")));
        item.setLastBackupSizeBytes(readLong(rs, "last_backup_size_bytes"));
        item.setLastBackupPath(rs.getString("last_backup_path"));
        item.setLastRestoreVerifiedAt(parseOffsetDateTime(rs.getString("last_restore_verified_at")));
        item.setLastRestoreNote(rs.getString("last_restore_note"));
        item.setLastCheckedAt(parseOffsetDateTime(rs.getString("last_checked_at")));
        item.setCreatedAt(parseOffsetDateTime(rs.getString("created_at")));
        item.setUpdatedAt(parseOffsetDateTime(rs.getString("updated_at")));
        return item;
    };

    private final JdbcTemplate jdbcTemplate;

    public BackupReadinessMonitorRepository(@Qualifier("monitoringRuntimeJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BackupReadinessMonitor> findAllByOrderByMonitorNameAscIdAsc() {
        return jdbcTemplate.query(
            "SELECT * FROM backup_readiness_monitors ORDER BY monitor_name ASC, id ASC",
            ROW_MAPPER
        );
    }

    public Optional<BackupReadinessMonitor> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM backup_readiness_monitors WHERE id = ? LIMIT 1",
                ROW_MAPPER,
                id
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<BackupReadinessMonitor> findByMonitorName(String monitorName) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM backup_readiness_monitors WHERE monitor_name = ? LIMIT 1",
                ROW_MAPPER,
                monitorName
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM backup_readiness_monitors WHERE id = ?",
            Integer.class,
            id
        );
        return count != null && count > 0;
    }

    public void deleteById(Long id) {
        runWithBusyRetry(() -> jdbcTemplate.update("DELETE FROM backup_readiness_monitors WHERE id = ?", id));
    }

    public BackupReadinessMonitor save(BackupReadinessMonitor item) {
        if (item.getId() == null) {
            return insert(item);
        }
        update(item);
        return item;
    }

    private BackupReadinessMonitor insert(BackupReadinessMonitor item) {
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

    private void update(BackupReadinessMonitor item) {
        runWithBusyRetry(() -> jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                """
                UPDATE backup_readiness_monitors
                   SET monitor_name = ?,
                       backup_kind = ?,
                       path_pattern = ?,
                       enabled = ?,
                       freshness_threshold_hours = ?,
                       restore_threshold_days = ?,
                       last_status = ?,
                       last_summary = ?,
                       last_error_message = ?,
                       last_backup_at = ?,
                       last_backup_size_bytes = ?,
                       last_backup_path = ?,
                       last_restore_verified_at = ?,
                       last_restore_note = ?,
                       last_checked_at = ?,
                       created_at = ?,
                       updated_at = ?
                 WHERE id = ?
                """
            )) {
                bindCommon(ps, item);
                ps.setLong(18, item.getId());
                return ps.executeUpdate();
            }
        }));
    }

    private PreparedStatement prepareInsertStatement(Connection connection) throws SQLException {
        return connection.prepareStatement(
            """
            INSERT INTO backup_readiness_monitors (
                monitor_name, backup_kind, path_pattern, enabled,
                freshness_threshold_hours, restore_threshold_days, last_status,
                last_summary, last_error_message, last_backup_at, last_backup_size_bytes,
                last_backup_path, last_restore_verified_at, last_restore_note,
                last_checked_at, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            Statement.RETURN_GENERATED_KEYS
        );
    }

    private void bindCommon(PreparedStatement ps, BackupReadinessMonitor item) throws SQLException {
        ps.setString(1, item.getMonitorName());
        ps.setString(2, item.getBackupKind());
        ps.setString(3, item.getPathPattern());
        ps.setBoolean(4, Boolean.TRUE.equals(item.getEnabled()));
        ps.setInt(5, item.getFreshnessThresholdHours() != null ? item.getFreshnessThresholdHours() : 24);
        ps.setInt(6, item.getRestoreThresholdDays() != null ? item.getRestoreThresholdDays() : 14);
        ps.setString(7, item.getLastStatus());
        ps.setString(8, item.getLastSummary());
        ps.setString(9, item.getLastErrorMessage());
        bindOffsetDateTime(ps, 10, item.getLastBackupAt());
        if (item.getLastBackupSizeBytes() != null) {
            ps.setLong(11, item.getLastBackupSizeBytes());
        } else {
            ps.setNull(11, Types.BIGINT);
        }
        ps.setString(12, item.getLastBackupPath());
        bindOffsetDateTime(ps, 13, item.getLastRestoreVerifiedAt());
        ps.setString(14, item.getLastRestoreNote());
        bindOffsetDateTime(ps, 15, item.getLastCheckedAt());
        bindOffsetDateTime(ps, 16, item.getCreatedAt());
        bindOffsetDateTime(ps, 17, item.getUpdatedAt());
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

    private static OffsetDateTime parseOffsetDateTime(String value) {
        return DATE_TIME_CONVERTER.convertToEntityAttribute(value);
    }

    private static Long readLong(java.sql.ResultSet rs, String columnName) {
        try {
            Object value = rs.getObject(columnName);
            return value instanceof Number number ? number.longValue() : null;
        } catch (Exception ignored) {
            return null;
        }
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
}
