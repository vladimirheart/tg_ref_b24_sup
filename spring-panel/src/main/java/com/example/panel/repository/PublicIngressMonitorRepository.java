package com.example.panel.repository;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import com.example.panel.entity.PublicIngressMonitor;
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
public class PublicIngressMonitorRepository {

    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();
    private static final int[] BUSY_RETRY_DELAYS_MS = {150, 350, 750, 1_500};

    private static final RowMapper<PublicIngressMonitor> ROW_MAPPER = (rs, rowNum) -> {
        PublicIngressMonitor item = new PublicIngressMonitor();
        item.setId(rs.getLong("id"));
        item.setMonitorName(rs.getString("monitor_name"));
        item.setEndpointUrl(rs.getString("endpoint_url"));
        item.setScheme(rs.getString("scheme"));
        item.setHost(rs.getString("host"));
        item.setPort(rs.getInt("port"));
        item.setExpectedHttpStatus((Integer) rs.getObject("expected_http_status"));
        item.setEnabled(rs.getBoolean("enabled"));
        item.setLastStatus(rs.getString("last_status"));
        item.setLastSummary(rs.getString("last_summary"));
        item.setLastErrorMessage(rs.getString("last_error_message"));
        item.setLastDnsResolvedAt(parseOffsetDateTime(rs.getString("last_dns_resolved_at")));
        item.setLastDnsAddresses(rs.getString("last_dns_addresses"));
        item.setLastHttpStatus((Integer) rs.getObject("last_http_status"));
        item.setLastHttpDurationMs(readLong(rs, "last_http_duration_ms"));
        item.setLastHttpCheckedAt(parseOffsetDateTime(rs.getString("last_http_checked_at")));
        item.setLastTlsCheckedAt(parseOffsetDateTime(rs.getString("last_tls_checked_at")));
        item.setLastTlsExpiresAt(parseOffsetDateTime(rs.getString("last_tls_expires_at")));
        item.setLastTlsDaysLeft((Integer) rs.getObject("last_tls_days_left"));
        item.setLastCheckedAt(parseOffsetDateTime(rs.getString("last_checked_at")));
        item.setCreatedAt(parseOffsetDateTime(rs.getString("created_at")));
        item.setUpdatedAt(parseOffsetDateTime(rs.getString("updated_at")));
        return item;
    };

    private final JdbcTemplate jdbcTemplate;

    public PublicIngressMonitorRepository(@Qualifier("monitoringRuntimeJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PublicIngressMonitor> findAllByOrderByMonitorNameAscIdAsc() {
        return jdbcTemplate.query(
            "SELECT * FROM public_ingress_monitors ORDER BY monitor_name ASC, id ASC",
            ROW_MAPPER
        );
    }

    public Optional<PublicIngressMonitor> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM public_ingress_monitors WHERE id = ? LIMIT 1",
                ROW_MAPPER,
                id
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<PublicIngressMonitor> findByMonitorName(String monitorName) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM public_ingress_monitors WHERE monitor_name = ? LIMIT 1",
                ROW_MAPPER,
                monitorName
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<PublicIngressMonitor> findByEndpointUrl(String endpointUrl) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM public_ingress_monitors WHERE endpoint_url = ? LIMIT 1",
                ROW_MAPPER,
                endpointUrl
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM public_ingress_monitors WHERE id = ?",
            Integer.class,
            id
        );
        return count != null && count > 0;
    }

    public void deleteById(Long id) {
        runWithBusyRetry(() -> jdbcTemplate.update("DELETE FROM public_ingress_monitors WHERE id = ?", id));
    }

    public PublicIngressMonitor save(PublicIngressMonitor item) {
        if (item.getId() == null) {
            return insert(item);
        }
        update(item);
        return item;
    }

    private PublicIngressMonitor insert(PublicIngressMonitor item) {
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

    private void update(PublicIngressMonitor item) {
        runWithBusyRetry(() -> jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                """
                UPDATE public_ingress_monitors
                   SET monitor_name = ?,
                       endpoint_url = ?,
                       scheme = ?,
                       host = ?,
                       port = ?,
                       expected_http_status = ?,
                       enabled = ?,
                       last_status = ?,
                       last_summary = ?,
                       last_error_message = ?,
                       last_dns_resolved_at = ?,
                       last_dns_addresses = ?,
                       last_http_status = ?,
                       last_http_duration_ms = ?,
                       last_http_checked_at = ?,
                       last_tls_checked_at = ?,
                       last_tls_expires_at = ?,
                       last_tls_days_left = ?,
                       last_checked_at = ?,
                       created_at = ?,
                       updated_at = ?
                 WHERE id = ?
                """
            )) {
                bindCommon(ps, item);
                ps.setLong(22, item.getId());
                return ps.executeUpdate();
            }
        }));
    }

    private PreparedStatement prepareInsertStatement(Connection connection) throws SQLException {
        return connection.prepareStatement(
            """
            INSERT INTO public_ingress_monitors (
                monitor_name, endpoint_url, scheme, host, port, expected_http_status,
                enabled, last_status, last_summary, last_error_message, last_dns_resolved_at,
                last_dns_addresses, last_http_status, last_http_duration_ms, last_http_checked_at,
                last_tls_checked_at, last_tls_expires_at, last_tls_days_left, last_checked_at,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            Statement.RETURN_GENERATED_KEYS
        );
    }

    private void bindCommon(PreparedStatement ps, PublicIngressMonitor item) throws SQLException {
        ps.setString(1, item.getMonitorName());
        ps.setString(2, item.getEndpointUrl());
        ps.setString(3, item.getScheme());
        ps.setString(4, item.getHost());
        ps.setInt(5, item.getPort() != null ? item.getPort() : 443);
        if (item.getExpectedHttpStatus() != null) {
            ps.setInt(6, item.getExpectedHttpStatus());
        } else {
            ps.setNull(6, Types.INTEGER);
        }
        ps.setBoolean(7, Boolean.TRUE.equals(item.getEnabled()));
        ps.setString(8, item.getLastStatus());
        ps.setString(9, item.getLastSummary());
        ps.setString(10, item.getLastErrorMessage());
        bindOffsetDateTime(ps, 11, item.getLastDnsResolvedAt());
        ps.setString(12, item.getLastDnsAddresses());
        if (item.getLastHttpStatus() != null) {
            ps.setInt(13, item.getLastHttpStatus());
        } else {
            ps.setNull(13, Types.INTEGER);
        }
        if (item.getLastHttpDurationMs() != null) {
            ps.setLong(14, item.getLastHttpDurationMs());
        } else {
            ps.setNull(14, Types.BIGINT);
        }
        bindOffsetDateTime(ps, 15, item.getLastHttpCheckedAt());
        bindOffsetDateTime(ps, 16, item.getLastTlsCheckedAt());
        bindOffsetDateTime(ps, 17, item.getLastTlsExpiresAt());
        if (item.getLastTlsDaysLeft() != null) {
            ps.setInt(18, item.getLastTlsDaysLeft());
        } else {
            ps.setNull(18, Types.INTEGER);
        }
        bindOffsetDateTime(ps, 19, item.getLastCheckedAt());
        bindOffsetDateTime(ps, 20, item.getCreatedAt());
        bindOffsetDateTime(ps, 21, item.getUpdatedAt());
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
