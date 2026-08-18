package com.example.panel.repository;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import com.example.panel.entity.SslCertificateMonitor;
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
public class SslCertificateMonitorRepository {

    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();
    private static final int[] BUSY_RETRY_DELAYS_MS = {150, 350, 750, 1_500};

    private static final RowMapper<SslCertificateMonitor> ROW_MAPPER = (rs, rowNum) -> {
        SslCertificateMonitor item = new SslCertificateMonitor();
        item.setId(rs.getLong("id"));
        item.setSiteName(rs.getString("site_name"));
        item.setEndpointUrl(rs.getString("endpoint_url"));
        item.setHost(rs.getString("host"));
        item.setPort(rs.getInt("port"));
        item.setEnabled(rs.getBoolean("enabled"));
        item.setMonitorStatus(rs.getString("monitor_status"));
        item.setErrorMessage(rs.getString("error_message"));

        Object daysLeft = rs.getObject("days_left");
        item.setDaysLeft(daysLeft == null ? null : rs.getInt("days_left"));

        item.setExpiresAt(parseOffsetDateTime(rs.getString("expires_at")));
        item.setLastCheckedAt(parseOffsetDateTime(rs.getString("last_checked_at")));
        item.setLastNotifiedAt(parseOffsetDateTime(rs.getString("last_notified_at")));
        item.setCreatedAt(parseOffsetDateTime(rs.getString("created_at")));
        item.setUpdatedAt(parseOffsetDateTime(rs.getString("updated_at")));
        return item;
    };

    private final JdbcTemplate jdbcTemplate;

    public SslCertificateMonitorRepository(@Qualifier("monitoringJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SslCertificateMonitor> findAllByOrderBySiteNameAscIdAsc() {
        return jdbcTemplate.query(
            "SELECT * FROM ssl_certificate_monitors ORDER BY site_name ASC, id ASC",
            ROW_MAPPER
        );
    }

    public List<SslCertificateMonitor> findByEnabledTrueOrderBySiteNameAscIdAsc() {
        return jdbcTemplate.query(
            "SELECT * FROM ssl_certificate_monitors WHERE enabled = TRUE ORDER BY site_name ASC, id ASC",
            ROW_MAPPER
        );
    }

    public Optional<SslCertificateMonitor> findByEndpointUrl(String endpointUrl) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM ssl_certificate_monitors WHERE endpoint_url = ? LIMIT 1",
                ROW_MAPPER,
                endpointUrl
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<SslCertificateMonitor> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM ssl_certificate_monitors WHERE id = ? LIMIT 1",
                ROW_MAPPER,
                id
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ssl_certificate_monitors WHERE id = ?",
            Integer.class,
            id
        );
        return count != null && count > 0;
    }

    public void deleteById(Long id) {
        runWithBusyRetry(() -> jdbcTemplate.update("DELETE FROM ssl_certificate_monitors WHERE id = ?", id));
    }

    public SslCertificateMonitor save(SslCertificateMonitor item) {
        if (item.getId() == null) {
            return insert(item);
        }
        update(item);
        return item;
    }

    private SslCertificateMonitor insert(SslCertificateMonitor item) {
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

    private void update(SslCertificateMonitor item) {
        runWithBusyRetry(() -> jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                """
                UPDATE ssl_certificate_monitors
                   SET site_name = ?,
                       endpoint_url = ?,
                       host = ?,
                       port = ?,
                       enabled = ?,
                       monitor_status = ?,
                       error_message = ?,
                       days_left = ?,
                       expires_at = ?,
                       last_checked_at = ?,
                       last_notified_at = ?,
                       created_at = ?,
                       updated_at = ?
                 WHERE id = ?
                """
            )) {
                bindCommon(ps, item);
                ps.setLong(14, item.getId());
                return ps.executeUpdate();
            }
        }));
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

    private void bindCommon(PreparedStatement ps, SslCertificateMonitor item) throws SQLException {
        ps.setString(1, item.getSiteName());
        ps.setString(2, item.getEndpointUrl());
        ps.setString(3, item.getHost());
        ps.setInt(4, item.getPort() != null ? item.getPort() : 443);
        ps.setBoolean(5, Boolean.TRUE.equals(item.getEnabled()));
        ps.setString(6, item.getMonitorStatus());
        ps.setString(7, item.getErrorMessage());
        if (item.getDaysLeft() != null) {
            ps.setInt(8, item.getDaysLeft());
        } else {
            ps.setObject(8, null);
        }
        bindOffsetDateTime(ps, 9, item.getExpiresAt());
        bindOffsetDateTime(ps, 10, item.getLastCheckedAt());
        bindOffsetDateTime(ps, 11, item.getLastNotifiedAt());
        bindOffsetDateTime(ps, 12, item.getCreatedAt());
        bindOffsetDateTime(ps, 13, item.getUpdatedAt());
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

    private PreparedStatement prepareInsertStatement(Connection connection) throws SQLException {
        return connection.prepareStatement(
            """
            INSERT INTO ssl_certificate_monitors (
                site_name, endpoint_url, host, port, enabled,
                monitor_status, error_message, days_left, expires_at,
                last_checked_at, last_notified_at, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            Statement.RETURN_GENERATED_KEYS
        );
    }

    private static OffsetDateTime parseOffsetDateTime(String value) {
        return DATE_TIME_CONVERTER.convertToEntityAttribute(value);
    }
}
