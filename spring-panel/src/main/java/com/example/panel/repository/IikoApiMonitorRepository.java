package com.example.panel.repository;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import com.example.panel.entity.IikoApiMonitor;
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
        item.setEnabled(rs.getInt("enabled") != 0);
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
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO iiko_api_monitors (
                    monitor_name, base_url, api_login, request_type, request_config_json,
                    enabled, last_status, last_http_status, last_error_message, last_duration_ms,
                    last_checked_at, last_token_checked_at, last_response_excerpt, last_response_summary_json,
                    consecutive_failures, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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

    private void update(IikoApiMonitor item) {
        jdbcTemplate.update(
            """
            UPDATE iiko_api_monitors
               SET monitor_name = ?,
                   base_url = ?,
                   api_login = ?,
                   request_type = ?,
                   request_config_json = ?,
                   enabled = ?,
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
            """,
            item.getMonitorName(),
            item.getBaseUrl(),
            item.getApiLogin(),
            item.getRequestType(),
            item.getRequestConfigJson(),
            toInt(item.getEnabled()),
            item.getLastStatus(),
            item.getLastHttpStatus(),
            item.getLastErrorMessage(),
            item.getLastDurationMs(),
            formatOffsetDateTime(item.getLastCheckedAt()),
            formatOffsetDateTime(item.getLastTokenCheckedAt()),
            item.getLastResponseExcerpt(),
            item.getLastResponseSummaryJson(),
            item.getConsecutiveFailures() == null ? 0 : item.getConsecutiveFailures(),
            formatOffsetDateTime(item.getCreatedAt()),
            formatOffsetDateTime(item.getUpdatedAt()),
            item.getId()
        );
    }

    private void bindCommon(PreparedStatement ps, IikoApiMonitor item) throws java.sql.SQLException {
        ps.setString(1, item.getMonitorName());
        ps.setString(2, item.getBaseUrl());
        ps.setString(3, item.getApiLogin());
        ps.setString(4, item.getRequestType());
        ps.setString(5, item.getRequestConfigJson());
        ps.setInt(6, toInt(item.getEnabled()));
        ps.setString(7, item.getLastStatus());
        ps.setObject(8, item.getLastHttpStatus());
        ps.setString(9, item.getLastErrorMessage());
        ps.setObject(10, item.getLastDurationMs());
        ps.setString(11, formatOffsetDateTime(item.getLastCheckedAt()));
        ps.setString(12, formatOffsetDateTime(item.getLastTokenCheckedAt()));
        ps.setString(13, item.getLastResponseExcerpt());
        ps.setString(14, item.getLastResponseSummaryJson());
        ps.setInt(15, item.getConsecutiveFailures() == null ? 0 : item.getConsecutiveFailures());
        ps.setString(16, formatOffsetDateTime(item.getCreatedAt()));
        ps.setString(17, formatOffsetDateTime(item.getUpdatedAt()));
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
}
