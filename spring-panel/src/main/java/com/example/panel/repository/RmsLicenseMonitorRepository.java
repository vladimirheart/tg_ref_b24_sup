package com.example.panel.repository;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import com.example.panel.entity.RmsLicenseMonitor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class RmsLicenseMonitorRepository {

    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();

    private static final RowMapper<RmsLicenseMonitor> ROW_MAPPER = (rs, rowNum) -> {
        RmsLicenseMonitor item = new RmsLicenseMonitor();
        item.setId(rs.getLong("id"));
        item.setRmsAddress(rs.getString("rms_address"));
        item.setScheme(rs.getString("scheme"));
        item.setHost(rs.getString("host"));
        item.setPort(rs.getInt("port"));
        item.setAuthLogin(rs.getString("auth_login"));
        item.setAuthPassword(rs.getString("auth_password"));
        item.setEnabled(rs.getInt("enabled") != 0);
        item.setLicenseMonitoringEnabled(rs.getInt("license_monitoring_enabled") != 0);
        item.setNetworkMonitoringEnabled(rs.getInt("network_monitoring_enabled") != 0);
        item.setServerName(rs.getString("server_name"));
        item.setServerType(rs.getString("server_type"));
        item.setServerVersion(rs.getString("server_version"));
        item.setLicenseStatus(rs.getString("license_status"));
        item.setLicenseErrorMessage(rs.getString("license_error_message"));
        item.setLicenseDetailsJson(rs.getString("license_details_json"));
        item.setLicenseDebugExcerpt(readStringColumn(rs, "license_debug_excerpt"));
        item.setLicenseExpiresAt(parseOffsetDateTime(rs.getString("license_expires_at")));

        Object licenseDaysLeft = rs.getObject("license_days_left");
        item.setLicenseDaysLeft(licenseDaysLeft == null ? null : rs.getInt("license_days_left"));

        item.setLicenseLastCheckedAt(parseOffsetDateTime(rs.getString("license_last_checked_at")));
        item.setLicenseLastNotifiedAt(parseOffsetDateTime(rs.getString("license_last_notified_at")));
        item.setRmsStatus(rs.getString("rms_status"));
        item.setRmsStatusMessage(rs.getString("rms_status_message"));
        item.setPingOutput(rs.getString("ping_output"));
        item.setTracerouteSummary(rs.getString("traceroute_summary"));
        item.setTracerouteReport(rs.getString("traceroute_report"));
        item.setTracerouteCheckedAt(parseOffsetDateTime(rs.getString("traceroute_checked_at")));
        item.setRmsLastCheckedAt(parseOffsetDateTime(rs.getString("rms_last_checked_at")));
        item.setCreatedAt(parseOffsetDateTime(rs.getString("created_at")));
        item.setUpdatedAt(parseOffsetDateTime(rs.getString("updated_at")));
        return item;
    };

    private final JdbcTemplate jdbcTemplate;

    public RmsLicenseMonitorRepository(@Qualifier("monitoringJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RmsLicenseMonitor> findAll() {
        return jdbcTemplate.query("SELECT * FROM rms_license_monitors ORDER BY id ASC", ROW_MAPPER);
    }

    public List<RmsLicenseMonitor> findAllByOrderByRmsAddressAscIdAsc() {
        return jdbcTemplate.query("SELECT * FROM rms_license_monitors ORDER BY rms_address ASC, id ASC", ROW_MAPPER);
    }

    public Optional<RmsLicenseMonitor> findByRmsAddress(String rmsAddress) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM rms_license_monitors WHERE rms_address = ? LIMIT 1",
                ROW_MAPPER,
                rmsAddress
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<RmsLicenseMonitor> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM rms_license_monitors WHERE id = ? LIMIT 1",
                ROW_MAPPER,
                id
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM rms_license_monitors WHERE id = ?",
            Integer.class,
            id
        );
        return count != null && count > 0;
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM rms_license_monitors WHERE id = ?", id);
    }

    public RmsLicenseMonitor save(RmsLicenseMonitor item) {
        if (item.getId() == null) {
            return insert(item);
        }
        update(item);
        return item;
    }

    public List<RmsLicenseMonitor> saveAll(Iterable<RmsLicenseMonitor> items) {
        List<RmsLicenseMonitor> saved = new ArrayList<>();
        for (RmsLicenseMonitor item : items) {
            saved.add(save(item));
        }
        return saved;
    }

    private RmsLicenseMonitor insert(RmsLicenseMonitor item) {
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO rms_license_monitors (
                    rms_address, scheme, host, port, auth_login, auth_password, enabled,
                    license_monitoring_enabled, network_monitoring_enabled,
                    server_name, server_type, server_version,
                    license_status, license_error_message, license_details_json, license_expires_at, license_days_left,
                    license_last_checked_at, license_last_notified_at,
                    rms_status, rms_status_message, ping_output,
                    traceroute_summary, traceroute_report, traceroute_checked_at, license_debug_excerpt,
                    rms_last_checked_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """
            );
            bindCommon(ps, item);
            return ps;
        });
        Number key = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Number.class);
        if (key != null) {
            item.setId(key.longValue());
        }
        return item;
    }

    private void update(RmsLicenseMonitor item) {
        jdbcTemplate.update(
            """
            UPDATE rms_license_monitors
               SET rms_address = ?,
                   scheme = ?,
                   host = ?,
                   port = ?,
                   auth_login = ?,
                   auth_password = ?,
                   enabled = ?,
                   license_monitoring_enabled = ?,
                   network_monitoring_enabled = ?,
                   server_name = ?,
                   server_type = ?,
                   server_version = ?,
                   license_status = ?,
                   license_error_message = ?,
                   license_details_json = ?,
                   license_expires_at = ?,
                   license_days_left = ?,
                   license_last_checked_at = ?,
                   license_last_notified_at = ?,
                   rms_status = ?,
                   rms_status_message = ?,
                   ping_output = ?,
                   traceroute_summary = ?,
                   traceroute_report = ?,
                   traceroute_checked_at = ?,
                   license_debug_excerpt = ?,
                   rms_last_checked_at = ?,
                   created_at = ?,
                   updated_at = ?
             WHERE id = ?
            """,
            item.getRmsAddress(),
            item.getScheme(),
            item.getHost(),
            item.getPort(),
            item.getAuthLogin(),
            item.getAuthPassword(),
            toInt(item.getEnabled()),
            toInt(item.getLicenseMonitoringEnabled()),
            toInt(item.getNetworkMonitoringEnabled()),
            item.getServerName(),
            item.getServerType(),
            item.getServerVersion(),
            item.getLicenseStatus(),
            item.getLicenseErrorMessage(),
            item.getLicenseDetailsJson(),
            formatOffsetDateTime(item.getLicenseExpiresAt()),
            item.getLicenseDaysLeft(),
            formatOffsetDateTime(item.getLicenseLastCheckedAt()),
            formatOffsetDateTime(item.getLicenseLastNotifiedAt()),
            item.getRmsStatus(),
            item.getRmsStatusMessage(),
            item.getPingOutput(),
            item.getTracerouteSummary(),
            item.getTracerouteReport(),
            formatOffsetDateTime(item.getTracerouteCheckedAt()),
            item.getLicenseDebugExcerpt(),
            formatOffsetDateTime(item.getRmsLastCheckedAt()),
            formatOffsetDateTime(item.getCreatedAt()),
            formatOffsetDateTime(item.getUpdatedAt()),
            item.getId()
        );
    }

    private void bindCommon(PreparedStatement ps, RmsLicenseMonitor item) throws java.sql.SQLException {
        ps.setString(1, item.getRmsAddress());
        ps.setString(2, item.getScheme());
        ps.setString(3, item.getHost());
        ps.setInt(4, item.getPort() != null ? item.getPort() : 443);
        ps.setString(5, item.getAuthLogin());
        ps.setString(6, item.getAuthPassword());
        ps.setInt(7, toInt(item.getEnabled()));
        ps.setInt(8, toInt(item.getLicenseMonitoringEnabled()));
        ps.setInt(9, toInt(item.getNetworkMonitoringEnabled()));
        ps.setString(10, item.getServerName());
        ps.setString(11, item.getServerType());
        ps.setString(12, item.getServerVersion());
        ps.setString(13, item.getLicenseStatus());
        ps.setString(14, item.getLicenseErrorMessage());
        ps.setString(15, item.getLicenseDetailsJson());
        ps.setString(16, formatOffsetDateTime(item.getLicenseExpiresAt()));
        if (item.getLicenseDaysLeft() != null) {
            ps.setInt(17, item.getLicenseDaysLeft());
        } else {
            ps.setObject(17, null);
        }
        ps.setString(18, formatOffsetDateTime(item.getLicenseLastCheckedAt()));
        ps.setString(19, formatOffsetDateTime(item.getLicenseLastNotifiedAt()));
        ps.setString(20, item.getRmsStatus());
        ps.setString(21, item.getRmsStatusMessage());
        ps.setString(22, item.getPingOutput());
        ps.setString(23, item.getTracerouteSummary());
        ps.setString(24, item.getTracerouteReport());
        ps.setString(25, formatOffsetDateTime(item.getTracerouteCheckedAt()));
        ps.setString(26, item.getLicenseDebugExcerpt());
        ps.setString(27, formatOffsetDateTime(item.getRmsLastCheckedAt()));
        ps.setString(28, formatOffsetDateTime(item.getCreatedAt()));
        ps.setString(29, formatOffsetDateTime(item.getUpdatedAt()));
    }

    private static int toInt(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private static String formatOffsetDateTime(OffsetDateTime value) {
        return value != null ? value.toString() : null;
    }

    private static OffsetDateTime parseOffsetDateTime(String value) {
        return DATE_TIME_CONVERTER.convertToEntityAttribute(value);
    }

    private static String readStringColumn(java.sql.ResultSet rs, String columnName) {
        try {
            return rs.getString(columnName);
        } catch (Exception ignored) {
            return null;
        }
    }
}
