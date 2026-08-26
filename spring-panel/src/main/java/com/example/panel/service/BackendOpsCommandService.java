package com.example.panel.service;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BackendOpsCommandService {

    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCEEDED = "succeeded";
    public static final String STATUS_FAILED = "failed";

    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER =
        new LenientOffsetDateTimeConverter();

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BackendOpsCommandService(JdbcTemplate jdbcTemplate,
                                    ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Enqueues one command per command type. The nullable active_key is unique, so
     * web replicas and worker schedulers can race without creating duplicate active work.
     */
    public EnqueueResult enqueueExclusive(String commandType,
                                          String scopeKey,
                                          Map<String, Object> payload,
                                          String requestedBy) {
        String type = requireText(commandType, "commandType");
        String scope = normalize(scopeKey, "global");
        Optional<CommandSnapshot> active = findActiveByType(type);
        if (active.isPresent()) {
            return new EnqueueResult(false, active.get());
        }

        String commandId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            jdbcTemplate.update("""
                    INSERT INTO backend_ops_command (
                        command_id,
                        command_type,
                        scope_key,
                        active_key,
                        payload_json,
                        status,
                        requested_by,
                        requested_at,
                        available_at,
                        progress_percent,
                        updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                commandId,
                type,
                scope,
                type,
                toJson(payload == null ? Map.of() : payload),
                STATUS_QUEUED,
                normalize(requestedBy, "panel"),
                timestamp(now),
                timestamp(now),
                0,
                timestamp(now)
            );
        } catch (DataIntegrityViolationException race) {
            CommandSnapshot winner = findActiveByType(type)
                .orElseThrow(() -> race);
            return new EnqueueResult(false, winner);
        }

        return new EnqueueResult(
            true,
            findById(commandId).orElseThrow()
        );
    }

    public Optional<CommandSnapshot> findById(String commandId) {
        if (!StringUtils.hasText(commandId)) {
            return Optional.empty();
        }
        List<CommandSnapshot> rows = jdbcTemplate.query("""
                SELECT command_id,
                       command_type,
                       scope_key,
                       payload_json,
                       status,
                       requested_by,
                       requested_at,
                       available_at,
                       claimed_by,
                       claimed_at,
                       heartbeat_at,
                       completed_at,
                       progress_percent,
                       progress_message,
                       result_json,
                       last_error,
                       attempt_count,
                       updated_at
                  FROM backend_ops_command
                 WHERE command_id = ?
                """,
            this::mapSnapshot,
            commandId.trim()
        );
        return rows.stream().findFirst();
    }

    public Optional<CommandSnapshot> findActiveByType(String commandType) {
        return findFirst("""
                SELECT command_id,
                       command_type,
                       scope_key,
                       payload_json,
                       status,
                       requested_by,
                       requested_at,
                       available_at,
                       claimed_by,
                       claimed_at,
                       heartbeat_at,
                       completed_at,
                       progress_percent,
                       progress_message,
                       result_json,
                       last_error,
                       attempt_count,
                       updated_at
                  FROM backend_ops_command
                 WHERE command_type = ?
                   AND active_key IS NOT NULL
                   AND status IN ('queued', 'running')
                 ORDER BY requested_at DESC, command_id DESC
                 LIMIT 1
                """,
            requireText(commandType, "commandType")
        );
    }

    public Optional<CommandSnapshot> findLatestByType(String commandType) {
        return findFirst("""
                SELECT command_id,
                       command_type,
                       scope_key,
                       payload_json,
                       status,
                       requested_by,
                       requested_at,
                       available_at,
                       claimed_by,
                       claimed_at,
                       heartbeat_at,
                       completed_at,
                       progress_percent,
                       progress_message,
                       result_json,
                       last_error,
                       attempt_count,
                       updated_at
                  FROM backend_ops_command
                 WHERE command_type = ?
                 ORDER BY requested_at DESC, command_id DESC
                 LIMIT 1
                """,
            requireText(commandType, "commandType")
        );
    }

    public Optional<CommandSnapshot> findLatestSucceededByType(String commandType) {
        return findFirst("""
                SELECT command_id,
                       command_type,
                       scope_key,
                       payload_json,
                       status,
                       requested_by,
                       requested_at,
                       available_at,
                       claimed_by,
                       claimed_at,
                       heartbeat_at,
                       completed_at,
                       progress_percent,
                       progress_message,
                       result_json,
                       last_error,
                       attempt_count,
                       updated_at
                  FROM backend_ops_command
                 WHERE command_type = ?
                   AND status = 'succeeded'
                 ORDER BY completed_at DESC, requested_at DESC, command_id DESC
                 LIMIT 1
                """,
            requireText(commandType, "commandType")
        );
    }

    /**
     * Claims one queued command with compare-and-set UPDATE semantics.
     * Different worker replicas may query the same candidate, but only one can
     * move it from queued to running.
     */
    public Optional<CommandSnapshot> claimNext(String instanceId,
                                               Duration staleClaimTimeout) {
        recoverStaleClaims(staleClaimTimeout);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<String> candidates = jdbcTemplate.query("""
                SELECT command_id
                  FROM backend_ops_command
                 WHERE status = 'queued'
                   AND available_at <= ?
                 ORDER BY requested_at ASC, command_id ASC
                 LIMIT 20
                """,
            (rs, rowNum) -> rs.getString("command_id"),
            timestamp(now)
        );

        String owner = normalize(instanceId, "worker");
        for (String commandId : candidates) {
            int claimed = jdbcTemplate.update("""
                    UPDATE backend_ops_command
                       SET status = 'running',
                           claimed_by = ?,
                           claimed_at = ?,
                           heartbeat_at = ?,
                           attempt_count = attempt_count + 1,
                           progress_percent = CASE WHEN progress_percent < 1 THEN 1 ELSE progress_percent END,
                           progress_message = CASE
                               WHEN progress_message IS NULL OR trim(progress_message) = ''
                               THEN 'Команда принята worker'
                               ELSE progress_message
                           END,
                           updated_at = ?
                     WHERE command_id = ?
                       AND status = 'queued'
                       AND available_at <= ?
                    """,
                owner,
                timestamp(now),
                timestamp(now),
                timestamp(now),
                commandId,
                timestamp(now)
            );
            if (claimed == 1) {
                return findById(commandId);
            }
        }
        return Optional.empty();
    }

    public int recoverStaleClaims(Duration staleClaimTimeout) {
        Duration timeout = staleClaimTimeout == null
            || staleClaimTimeout.isZero()
            || staleClaimTimeout.isNegative()
            ? Duration.ofHours(2)
            : staleClaimTimeout;
        OffsetDateTime threshold = OffsetDateTime.now(ZoneOffset.UTC).minus(timeout);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return jdbcTemplate.update("""
                UPDATE backend_ops_command
                   SET status = 'queued',
                       claimed_by = NULL,
                       claimed_at = NULL,
                       heartbeat_at = NULL,
                       progress_message = 'Команда возвращена в очередь после stale claim',
                       available_at = ?,
                       updated_at = ?
                 WHERE status = 'running'
                   AND (
                        (heartbeat_at IS NOT NULL AND heartbeat_at < ?)
                        OR
                        (heartbeat_at IS NULL AND claimed_at IS NOT NULL AND claimed_at < ?)
                   )
                """,
            timestamp(now),
            timestamp(now),
            timestamp(threshold),
            timestamp(threshold)
        );
    }

    public void heartbeat(String commandId) {
        if (!StringUtils.hasText(commandId)) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                UPDATE backend_ops_command
                   SET heartbeat_at = ?,
                       updated_at = ?
                 WHERE command_id = ?
                   AND status = 'running'
                """,
            timestamp(now),
            timestamp(now),
            commandId.trim()
        );
    }

    public void updateProgress(String commandId,
                               int progressPercent,
                               String progressMessage) {
        if (!StringUtils.hasText(commandId)) {
            return;
        }
        int safeProgress = Math.max(0, Math.min(99, progressPercent));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                UPDATE backend_ops_command
                   SET progress_percent = ?,
                       progress_message = ?,
                       heartbeat_at = ?,
                       updated_at = ?
                 WHERE command_id = ?
                   AND status = 'running'
                """,
            safeProgress,
            normalize(progressMessage, null),
            timestamp(now),
            timestamp(now),
            commandId.trim()
        );
    }

    public void markSucceeded(String commandId,
                              Object result) {
        finish(
            commandId,
            STATUS_SUCCEEDED,
            toJson(result == null ? Map.of() : result),
            null
        );
    }

    public void markFailed(String commandId,
                           Throwable error) {
        String message = error == null
            ? "Backend ops command failed"
            : normalize(error.getMessage(), error.getClass().getSimpleName());
        finish(commandId, STATUS_FAILED, null, truncate(message, 4000));
    }

    public <T> T readResult(CommandSnapshot command,
                            Class<T> resultType) {
        if (command == null
            || resultType == null
            || !StringUtils.hasText(command.resultJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(command.resultJson(), resultType);
        } catch (Exception ex) {
            return null;
        }
    }

    private void finish(String commandId,
                        String status,
                        String resultJson,
                        String lastError) {
        if (!StringUtils.hasText(commandId)) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update("""
                UPDATE backend_ops_command
                   SET status = ?,
                       active_key = NULL,
                       completed_at = ?,
                       heartbeat_at = ?,
                       progress_percent = ?,
                       progress_message = ?,
                       result_json = ?,
                       last_error = ?,
                       updated_at = ?
                 WHERE command_id = ?
                   AND status = 'running'
                """,
            status,
            timestamp(now),
            timestamp(now),
            STATUS_SUCCEEDED.equals(status) ? 100 : 99,
            STATUS_SUCCEEDED.equals(status)
                ? "Команда выполнена"
                : "Команда завершилась ошибкой",
            resultJson,
            lastError,
            timestamp(now),
            commandId.trim()
        );
    }

    private Optional<CommandSnapshot> findFirst(String sql,
                                                Object... args) {
        List<CommandSnapshot> rows = jdbcTemplate.query(
            sql,
            this::mapSnapshot,
            args
        );
        return rows.stream().findFirst();
    }

    private CommandSnapshot mapSnapshot(ResultSet rs,
                                        int rowNum) throws SQLException {
        return new CommandSnapshot(
            rs.getString("command_id"),
            rs.getString("command_type"),
            rs.getString("scope_key"),
            readJsonMap(rs.getString("payload_json")),
            rs.getString("status"),
            rs.getString("requested_by"),
            readTime(rs, "requested_at"),
            readTime(rs, "available_at"),
            rs.getString("claimed_by"),
            readTime(rs, "claimed_at"),
            readTime(rs, "heartbeat_at"),
            readTime(rs, "completed_at"),
            rs.getInt("progress_percent"),
            rs.getString("progress_message"),
            rs.getString("result_json"),
            rs.getString("last_error"),
            rs.getInt("attempt_count"),
            readTime(rs, "updated_at")
        );
    }

    private Map<String, Object> readJsonMap(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(
                rawValue,
                new TypeReference<LinkedHashMap<String, Object>>() {
                }
            );
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(
                "Unable to serialize backend ops command payload.",
                ex
            );
        }
    }

    private OffsetDateTime readTime(ResultSet rs,
                                    String columnName) throws SQLException {
        Object rawValue = rs.getObject(columnName);
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (rawValue instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (rawValue instanceof java.util.Date date) {
            return date.toInstant().atOffset(ZoneOffset.UTC);
        }
        return DATE_TIME_CONVERTER.convertToEntityAttribute(
            String.valueOf(rawValue)
        );
    }

    private Timestamp timestamp(OffsetDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant());
    }

    private String requireText(String value,
                               String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(
                "Backend ops command requires " + fieldName + "."
            );
        }
        return value.trim();
    }

    private String normalize(String value,
                             String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return value.trim();
    }

    private String truncate(String value,
                            int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record EnqueueResult(boolean created,
                                CommandSnapshot command) {
    }

    public record CommandSnapshot(
        String commandId,
        String commandType,
        String scopeKey,
        Map<String, Object> payload,
        String status,
        String requestedBy,
        OffsetDateTime requestedAt,
        OffsetDateTime availableAt,
        String claimedBy,
        OffsetDateTime claimedAt,
        OffsetDateTime heartbeatAt,
        OffsetDateTime completedAt,
        int progressPercent,
        String progressMessage,
        String resultJson,
        String lastError,
        int attemptCount,
        OffsetDateTime updatedAt
    ) {
        public boolean queued() {
            return STATUS_QUEUED.equalsIgnoreCase(status);
        }

        public boolean running() {
            return STATUS_RUNNING.equalsIgnoreCase(status);
        }

        public boolean succeeded() {
            return STATUS_SUCCEEDED.equalsIgnoreCase(status);
        }

        public boolean failed() {
            return STATUS_FAILED.equalsIgnoreCase(status);
        }

        public boolean active() {
            return queued() || running();
        }
    }
}
