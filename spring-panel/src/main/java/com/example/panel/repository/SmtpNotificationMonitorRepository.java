package com.example.panel.repository;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import com.example.panel.entity.SmtpNotificationMonitor;
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
public class SmtpNotificationMonitorRepository {

    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();

    private static final RowMapper<SmtpNotificationMonitor> ROW_MAPPER = (rs, rowNum) -> {
        SmtpNotificationMonitor item = new SmtpNotificationMonitor();
        item.setId(rs.getLong("id"));
        item.setMonitorName(rs.getString("monitor_name"));
        item.setRelayHost(rs.getString("relay_host"));
        item.setRelayPort((Integer) rs.getObject("relay_port"));
        item.setProtocolMode(rs.getString("protocol_mode"));
        item.setConnectTimeoutMs((Integer) rs.getObject("connect_timeout_ms"));
        item.setEnabled(rs.getBoolean("enabled"));
        item.setLastStatus(rs.getString("last_status"));
        item.setLastSummary(rs.getString("last_summary"));
        item.setLastErrorMessage(rs.getString("last_error_message"));
        item.setLastBanner(rs.getString("last_banner"));
        item.setLastTlsProtocol(rs.getString("last_tls_protocol"));
        item.setLastTlsCipherSuite(rs.getString("last_tls_cipher_suite"));
        item.setLastConnectedAt(parseOffsetDateTime(rs.getString("last_connected_at")));
        item.setLastCheckedAt(parseOffsetDateTime(rs.getString("last_checked_at")));
        item.setCreatedAt(parseOffsetDateTime(rs.getString("created_at")));
        item.setUpdatedAt(parseOffsetDateTime(rs.getString("updated_at")));
        return item;
    };

    private final JdbcTemplate jdbcTemplate;

    public SmtpNotificationMonitorRepository(@Qualifier("monitoringRuntimeJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SmtpNotificationMonitor> findAllByOrderByMonitorNameAscIdAsc() {
        return jdbcTemplate.query(
            "SELECT * FROM smtp_notification_monitors ORDER BY monitor_name ASC, id ASC",
            ROW_MAPPER
        );
    }

    public Optional<SmtpNotificationMonitor> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM smtp_notification_monitors WHERE id = ? LIMIT 1",
                ROW_MAPPER,
                id
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<SmtpNotificationMonitor> findByMonitorName(String monitorName) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM smtp_notification_monitors WHERE monitor_name = ? LIMIT 1",
                ROW_MAPPER,
                monitorName
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<SmtpNotificationMonitor> findByRelayTarget(String relayHost,
                                                               Integer relayPort,
                                                               String protocolMode) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                SELECT * FROM smtp_notification_monitors
                 WHERE relay_host = ?
                   AND relay_port = ?
                   AND protocol_mode = ?
                 LIMIT 1
                """,
                ROW_MAPPER,
                relayHost,
                relayPort,
                protocolMode
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM smtp_notification_monitors WHERE id = ?",
            Integer.class,
            id
        );
        return count != null && count > 0;
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM smtp_notification_monitors WHERE id = ?", id);
    }

    public SmtpNotificationMonitor save(SmtpNotificationMonitor item) {
        if (item.getId() == null) {
            return insert(item);
        }
        update(item);
        return item;
    }

    private SmtpNotificationMonitor insert(SmtpNotificationMonitor item) {
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

    private void update(SmtpNotificationMonitor item) {
        jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                """
                UPDATE smtp_notification_monitors
                   SET monitor_name = ?,
                       relay_host = ?,
                       relay_port = ?,
                       protocol_mode = ?,
                       connect_timeout_ms = ?,
                       enabled = ?,
                       last_status = ?,
                       last_summary = ?,
                       last_error_message = ?,
                       last_banner = ?,
                       last_tls_protocol = ?,
                       last_tls_cipher_suite = ?,
                       last_connected_at = ?,
                       last_checked_at = ?,
                       created_at = ?,
                       updated_at = ?
                 WHERE id = ?
                """
            )) {
                bindCommon(ps, item);
                ps.setLong(17, item.getId());
                return ps.executeUpdate();
            }
        });
    }

    private PreparedStatement prepareInsertStatement(Connection connection) throws SQLException {
        return connection.prepareStatement(
            """
            INSERT INTO smtp_notification_monitors (
                monitor_name, relay_host, relay_port, protocol_mode, connect_timeout_ms,
                enabled, last_status, last_summary, last_error_message, last_banner,
                last_tls_protocol, last_tls_cipher_suite, last_connected_at, last_checked_at,
                created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            Statement.RETURN_GENERATED_KEYS
        );
    }

    private void bindCommon(PreparedStatement ps, SmtpNotificationMonitor item) throws SQLException {
        ps.setString(1, item.getMonitorName());
        ps.setString(2, item.getRelayHost());
        ps.setInt(3, item.getRelayPort() != null ? item.getRelayPort() : 25);
        ps.setString(4, item.getProtocolMode());
        ps.setInt(5, item.getConnectTimeoutMs() != null ? item.getConnectTimeoutMs() : 5_000);
        ps.setBoolean(6, Boolean.TRUE.equals(item.getEnabled()));
        ps.setString(7, item.getLastStatus());
        ps.setString(8, item.getLastSummary());
        ps.setString(9, item.getLastErrorMessage());
        ps.setString(10, item.getLastBanner());
        ps.setString(11, item.getLastTlsProtocol());
        ps.setString(12, item.getLastTlsCipherSuite());
        bindOffsetDateTime(ps, 13, item.getLastConnectedAt());
        bindOffsetDateTime(ps, 14, item.getLastCheckedAt());
        bindOffsetDateTime(ps, 15, item.getCreatedAt());
        bindOffsetDateTime(ps, 16, item.getUpdatedAt());
    }

    private void bindOffsetDateTime(PreparedStatement ps, int index, OffsetDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
            return;
        }
        ps.setObject(index, value);
    }
    private static OffsetDateTime parseOffsetDateTime(String value) {
        return DATE_TIME_CONVERTER.convertToEntityAttribute(value);
    }

}
