package com.example.panel.repository;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import com.example.panel.entity.IikoApiMonitor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Repository
public class IikoApiMonitorRepository {

    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();

    private static final RowMapper<IikoApiMonitor> ROW_MAPPER = (rs, rowNum) -> {
        IikoApiMonitor item = new IikoApiMonitor();
        item.setId(rs.getLong("id"));
        item.setMonitorName(rs.getString("monitor_name"));
        item.setBaseUrl(rs.getString("base_url"));
        item.setApiLogin(rs.getString("api_login"));
        item.setRequestType(rs.getString("request_type"));
        item.setRequestConfigJson(rs.getString("request_config_json"));
        item.setEnabled(rs.getBoolean("enabled"));
        item.setLocationsSyncEnabled(readBooleanColumn(rs, "locations_sync_enabled", false));
        item.setLastStatus(rs.getString("last_status"));
        Object lastHttpStatus = rs.getObject("last_http_status");
        item.setLastHttpStatus(lastHttpStatus == null ? null : rs.getInt("last_http_status"));
        item.setLastErrorMessage(rs.getString("last_error_message"));
        Object lastDurationMs = rs.getObject("last_duration_ms");
        item.setLastDurationMs(lastDurationMs == null ? null : rs.getLong("last_duration_ms"));
        item.setLastCheckedAt(parseOffsetDateTime(rs.getString("last_checked_at")));
        item.setLastTokenCheckedAt(parseOffsetDateTime(rs.getString("last_token_checked_at")));
        item.setLastResponseExcerpt(rs.getString("last_response_excerpt"));
        item.setLastResponseSummaryJson(rs.getString("last_response_summary_json"));
        Object consecutiveFailures = rs.getObject("consecutive_failures");
        item.setConsecutiveFailures(consecutiveFailures == null ? 0 : rs.getInt("consecutive_failures"));
        item.setCreatedAt(parseOffsetDateTime(rs.getString("created_at")));
        item.setUpdatedAt(parseOffsetDateTime(rs.getString("updated_at")));
        return item;
    };

    private final JdbcTemplate jdbcTemplate;

    public IikoApiMonitorRepository(@Qualifier("monitoringJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<IikoApiMonitor> findAllByOrderByMonitorNameAscIdAsc() {
        return jdbcTemplate.query("SELECT * FROM iiko_api_monitors ORDER BY monitor_name ASC, id ASC", ROW_MAPPER);
    }

    public Optional<IikoApiMonitor> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM iiko_api_monitors WHERE id = ? LIMIT 1",
                ROW_MAPPER,
                id
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM iiko_api_monitors WHERE id = ?",
            Integer.class,
            id
        );
        return count != null && count > 0;
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM iiko_api_monitors WHERE id = ?", id);
    }

    public IikoApiMonitor save(IikoApiMonitor item) {
        if (item.getId() == null) {
            return insert(item);
        }
        update(item);
        return item;
    }

    public List<IikoApiMonitor> saveAll(Iterable<IikoApiMonitor> items) {
        List<IikoApiMonitor> saved = new ArrayList<>();
        for (IikoApiMonitor item : items) {
            saved.add(save(item));
        }
        return saved;
    }

    private IikoApiMonitor insert(IikoApiMonitor item) {
        Long key = jdbcTemplate.execute((ConnectionCallback<Long>) connection -> {
            try (PreparedStatement ps = prepareInsertStatement(connection)) {
                bindCommon(ps, item);
                ps.executeUpdate();
                return JdbcGeneratedKeySupport.extractGeneratedKey(ps, connection);
            }
        });
        if (key != null) {
            item.setId(key);
        }
        return item;
    }

    private void update(IikoApiMonitor item) {
        jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                """
                UPDATE iiko_api_monitors
                   SET monitor_name = ?,
                       base_url = ?,
                       api_login = ?,
                       request_type = ?,
                       request_config_json = ?,
                       enabled = ?,
                       locations_sync_enabled = ?,
                       last_status = ?,
                       last_http_status = ?,
                       last_error_message = ?,
                       last_duration_ms = ?,
                       last_checked_at = ?,
                       last_token_checked_at = ?,
                       last_response_excerpt = ?,
                       last_response_summary_json = ?,
                       consecutive_failures = ?,
                       created_at = ?,
                       updated_at = ?
                 WHERE id = ?
                """
            )) {
                bindCommon(ps, item);
                ps.setLong(19, item.getId());
                return ps.executeUpdate();
            }
        });
    }

    private void bindCommon(PreparedStatement ps, IikoApiMonitor item) throws SQLException {
        ps.setString(1, item.getMonitorName());
        ps.setString(2, item.getBaseUrl());
        ps.setString(3, item.getApiLogin());
        ps.setString(4, item.getRequestType());
        ps.setString(5, item.getRequestConfigJson());
        ps.setBoolean(6, Boolean.TRUE.equals(item.getEnabled()));
        ps.setBoolean(7, Boolean.TRUE.equals(item.getLocationsSyncEnabled()));
        ps.setString(8, item.getLastStatus());
        ps.setObject(9, item.getLastHttpStatus());
        ps.setString(10, item.getLastErrorMessage());
        ps.setObject(11, item.getLastDurationMs());
        bindOffsetDateTime(ps, 12, item.getLastCheckedAt());
        bindOffsetDateTime(ps, 13, item.getLastTokenCheckedAt());
        ps.setString(14, item.getLastResponseExcerpt());
        ps.setString(15, item.getLastResponseSummaryJson());
        ps.setInt(16, item.getConsecutiveFailures() == null ? 0 : item.getConsecutiveFailures());
        bindOffsetDateTime(ps, 17, item.getCreatedAt());
        bindOffsetDateTime(ps, 18, item.getUpdatedAt());
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
            INSERT INTO iiko_api_monitors (
                monitor_name, base_url, api_login, request_type, request_config_json,
                enabled, locations_sync_enabled, last_status, last_http_status, last_error_message, last_duration_ms,
                last_checked_at, last_token_checked_at, last_response_excerpt, last_response_summary_json,
                consecutive_failures, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            Statement.RETURN_GENERATED_KEYS
        );
    }

    private static OffsetDateTime parseOffsetDateTime(String value) {
        return DATE_TIME_CONVERTER.convertToEntityAttribute(value);
    }

    private static boolean readBooleanColumn(ResultSet rs, String columnName, boolean defaultValue) {
        try {
            return rs.getBoolean(columnName);
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}
