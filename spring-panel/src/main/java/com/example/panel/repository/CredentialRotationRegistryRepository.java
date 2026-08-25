package com.example.panel.repository;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import com.example.panel.entity.CredentialRotationRegistryEntry;
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
public class CredentialRotationRegistryRepository {

    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();
    private static final int[] BUSY_RETRY_DELAYS_MS = {150, 350, 750, 1_500};

    private static final RowMapper<CredentialRotationRegistryEntry> ROW_MAPPER = (rs, rowNum) -> {
        CredentialRotationRegistryEntry item = new CredentialRotationRegistryEntry();
        item.setId(rs.getLong("id"));
        item.setEntryKey(rs.getString("entry_key"));
        item.setIntegrationKind(rs.getString("integration_kind"));
        item.setCredentialKind(rs.getString("credential_kind"));
        item.setDisplayName(rs.getString("display_name"));
        item.setSourceType(rs.getString("source_type"));
        item.setSourceRef(rs.getString("source_ref"));
        item.setOwnerName(rs.getString("owner_name"));
        item.setNote(rs.getString("note"));
        item.setSourcePresent(rs.getBoolean("source_present"));
        item.setSecretPresent(rs.getBoolean("secret_present"));
        item.setLastStatus(rs.getString("last_status"));
        item.setStatusLevel(rs.getString("status_level"));
        item.setStatusReason(rs.getString("status_reason"));
        item.setExpiresAt(parseOffsetDateTime(rs.getString("expires_at")));
        item.setRotatedAt(parseOffsetDateTime(rs.getString("rotated_at")));
        item.setRotationIntervalDays((Integer) rs.getObject("rotation_interval_days"));
        item.setNextRotationDueAt(parseOffsetDateTime(rs.getString("next_rotation_due_at")));
        item.setLastSeenAt(parseOffsetDateTime(rs.getString("last_seen_at")));
        item.setLastCheckedAt(parseOffsetDateTime(rs.getString("last_checked_at")));
        item.setCreatedAt(parseOffsetDateTime(rs.getString("created_at")));
        item.setUpdatedAt(parseOffsetDateTime(rs.getString("updated_at")));
        return item;
    };

    private final JdbcTemplate jdbcTemplate;

    public CredentialRotationRegistryRepository(@Qualifier("monitoringRuntimeJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CredentialRotationRegistryEntry> findAllByOrderByDisplayNameAscIdAsc() {
        return jdbcTemplate.query(
            "SELECT * FROM credential_rotation_registry ORDER BY display_name ASC, id ASC",
            ROW_MAPPER
        );
    }

    public Optional<CredentialRotationRegistryEntry> findById(Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM credential_rotation_registry WHERE id = ? LIMIT 1",
                ROW_MAPPER,
                id
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<CredentialRotationRegistryEntry> findByEntryKey(String entryKey) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT * FROM credential_rotation_registry WHERE entry_key = ? LIMIT 1",
                ROW_MAPPER,
                entryKey
            ));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public boolean existsById(Long id) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM credential_rotation_registry WHERE id = ?",
            Integer.class,
            id
        );
        return count != null && count > 0;
    }

    public CredentialRotationRegistryEntry save(CredentialRotationRegistryEntry item) {
        if (item.getId() == null) {
            return insert(item);
        }
        update(item);
        return item;
    }

    private CredentialRotationRegistryEntry insert(CredentialRotationRegistryEntry item) {
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

    private void update(CredentialRotationRegistryEntry item) {
        runWithBusyRetry(() -> jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                """
                UPDATE credential_rotation_registry
                   SET entry_key = ?,
                       integration_kind = ?,
                       credential_kind = ?,
                       display_name = ?,
                       source_type = ?,
                       source_ref = ?,
                       owner_name = ?,
                       note = ?,
                       source_present = ?,
                       secret_present = ?,
                       last_status = ?,
                       status_level = ?,
                       status_reason = ?,
                       expires_at = ?,
                       rotated_at = ?,
                       rotation_interval_days = ?,
                       next_rotation_due_at = ?,
                       last_seen_at = ?,
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
            INSERT INTO credential_rotation_registry (
                entry_key, integration_kind, credential_kind, display_name, source_type, source_ref,
                owner_name, note, source_present, secret_present, last_status, status_level,
                status_reason, expires_at, rotated_at, rotation_interval_days, next_rotation_due_at,
                last_seen_at, last_checked_at, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            Statement.RETURN_GENERATED_KEYS
        );
    }

    private void bindCommon(PreparedStatement ps, CredentialRotationRegistryEntry item) throws SQLException {
        ps.setString(1, item.getEntryKey());
        ps.setString(2, item.getIntegrationKind());
        ps.setString(3, item.getCredentialKind());
        ps.setString(4, item.getDisplayName());
        ps.setString(5, item.getSourceType());
        ps.setString(6, item.getSourceRef());
        ps.setString(7, item.getOwnerName());
        ps.setString(8, item.getNote());
        ps.setBoolean(9, Boolean.TRUE.equals(item.getSourcePresent()));
        ps.setBoolean(10, Boolean.TRUE.equals(item.getSecretPresent()));
        ps.setString(11, item.getLastStatus());
        ps.setString(12, item.getStatusLevel());
        ps.setString(13, item.getStatusReason());
        bindOffsetDateTime(ps, 14, item.getExpiresAt());
        bindOffsetDateTime(ps, 15, item.getRotatedAt());
        if (item.getRotationIntervalDays() != null) {
            ps.setInt(16, item.getRotationIntervalDays());
        } else {
            ps.setNull(16, Types.INTEGER);
        }
        bindOffsetDateTime(ps, 17, item.getNextRotationDueAt());
        bindOffsetDateTime(ps, 18, item.getLastSeenAt());
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
