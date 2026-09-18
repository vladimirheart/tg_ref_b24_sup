package com.example.panel.repository;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import com.example.panel.entity.SslCertificateMonitor;
import org.springframework.beans.factory.annotation.Qualifier;
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
import java.util.Optional;

@Repository
public class SslCertificateMonitorRepository {

    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();

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

    public SslCertificateMonitorRepository(@Qualifier("monitoringRuntimeJdbcTemplate") JdbcTemplate jdbcTemplate) {
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
        jdbcTemplate.update("DELETE FROM ssl_certificate_monitors WHERE id = ?", id);
    }

    public SslCertificateMonitor save(SslCertificateMonitor item) {
        if (item.getId() == null) {
            return insert(item);
        }
        update(item);
        return item;
    }

    private SslCertificateMonitor insert(SslCertificateMonitor item) {
        Long key = jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {
            try (PreparedStatement ps = prepareInsertStatement(connection)) {
                bindCommon(ps, item);
                ps.executeUpdate();
                return JdbcGeneratedKeySupport.extractGeneratedKey(ps);
            }
        });
        if (key != null) {
            item.setId(key);
        }
        return item;
    }

    private void update(SslCertificateMonitor item) {
        jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
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
        });
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
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
            return;
        }
        ps.setObject(index, value);
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
