package com.example.panel.repository;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Supplier;

@Repository
public class MonitoringCheckHistoryRepository {

    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();
    private static final int[] BUSY_RETRY_DELAYS_MS = {150, 350, 750, 1_500};

    private static final RowMapper<HistoryEntry> ROW_MAPPER = (rs, rowNum) -> new HistoryEntry(
        rs.getLong("id"),
        rs.getString("monitor_kind"),
        rs.getLong("monitor_id"),
        rs.getString("check_kind"),
        rs.getString("status"),
        rs.getString("summary"),
        rs.getString("details_excerpt"),
        (Integer) rs.getObject("http_status"),
        readLong(rs, "duration_ms"),
        parseOffsetDateTime(rs.getString("created_at"))
    );

    private final JdbcTemplate jdbcTemplate;

    public MonitoringCheckHistoryRepository(@Qualifier("monitoringJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void record(String monitorKind,
                       long monitorId,
                       String checkKind,
                       String status,
                       String summary,
                       String detailsExcerpt,
                       Integer httpStatus,
                       Long durationMs,
                       OffsetDateTime createdAt) {
        runWithBusyRetry(() -> jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO monitoring_check_history (
                    monitor_kind, monitor_id, check_kind, status, summary,
                    details_excerpt, http_status, duration_ms, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
            );
            ps.setString(1, monitorKind);
            ps.setLong(2, monitorId);
            ps.setString(3, checkKind);
            ps.setString(4, status);
            ps.setString(5, summary);
            ps.setString(6, detailsExcerpt);
            if (httpStatus != null) {
                ps.setInt(7, httpStatus);
            } else {
                ps.setObject(7, null);
            }
            if (durationMs != null) {
                ps.setLong(8, durationMs);
            } else {
                ps.setObject(8, null);
            }
            ps.setString(9, createdAt != null ? createdAt.toString() : OffsetDateTime.now().toString());
            return ps;
        }));
    }

    public List<HistoryEntry> findRecent(String monitorKind, long monitorId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbcTemplate.query(
            """
            SELECT id, monitor_kind, monitor_id, check_kind, status, summary,
                   details_excerpt, http_status, duration_ms, created_at
              FROM monitoring_check_history
             WHERE monitor_kind = ?
               AND monitor_id = ?
             ORDER BY created_at DESC, id DESC
             LIMIT ?
            """,
            ROW_MAPPER,
            monitorKind,
            monitorId,
            safeLimit
        );
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

    public record HistoryEntry(Long id,
                               String monitorKind,
                               Long monitorId,
                               String checkKind,
                               String status,
                               String summary,
                               String detailsExcerpt,
                               Integer httpStatus,
                               Long durationMs,
                               OffsetDateTime createdAt) {
    }
}
