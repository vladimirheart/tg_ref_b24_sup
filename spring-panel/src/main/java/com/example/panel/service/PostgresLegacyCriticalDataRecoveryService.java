package com.example.panel.service;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeWorkload;

import com.example.panel.config.LegacySqliteCompatibilitySettings;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Second-stage recovery for legacy tables that cannot safely be handled by the
 * generic importer when SQLite TEXT timestamps are moved to PostgreSQL typed
 * timestamp columns. The operation is additive and idempotent.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 150)
@RuntimeWorkload(
    id = "postgres-legacy-critical-data-recovery",
    roles = {RuntimeRole.MIGRATOR},
    replicaPolicy = RuntimeReplicaPolicy.SINGLETON
)public class PostgresLegacyCriticalDataRecoveryService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PostgresLegacyCriticalDataRecoveryService.class);
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final List<String> CRITICAL_TABLES = List.of(
        "messages",
        "chat_history",
        "client_avatar_history",
        // These tables use PostgreSQL-only identity/timestamp semantics that the generic importer cannot preserve.
        "notifications",
        "web_form_sessions",
        "chat_attachment_metadata"
    );
    private static final List<DateTimeFormatter> LOCAL_DATE_TIME_FORMATTERS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
    );

    private final DataSource dataSource;
    private final Environment environment;
    private final PanelDatabaseRuntimeMode runtimeMode;
    private final LegacySqliteCompatibilitySettings compatibilitySettings;

    public PostgresLegacyCriticalDataRecoveryService(DataSource dataSource,
                                                      Environment environment,
                                                      PanelDatabaseRuntimeMode runtimeMode,
                                                      LegacySqliteCompatibilitySettings compatibilitySettings) {
        this.dataSource = dataSource;
        this.environment = environment;
        this.runtimeMode = runtimeMode;
        this.compatibilitySettings = compatibilitySettings;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!"postgresql".equalsIgnoreCase(runtimeMode.modeLabel())) {
            return;
        }
        if (!compatibilitySettings.isAutoImportEnabled()) {
            log.debug("Legacy SQLite critical recovery is disabled.");
            return;
        }

        Path workspaceRoot = locateWorkspaceRoot(Paths.get("").toAbsolutePath().normalize());
        Path source = resolvePanelRuntimeSource(workspaceRoot);
        if (source == null) {
            log.warn("[MIGRATION] Critical legacy recovery skipped: panel_runtime.db was not found.");
            return;
        }

        long sourceSize = Files.size(source);
        long importedTotal = 0L;
        long skippedTotal = 0L;
        int processedTables = 0;

        try (Connection target = dataSource.getConnection();
             Connection sqlite = java.sql.DriverManager.getConnection("jdbc:sqlite:" + source.toAbsolutePath().normalize())) {
            boolean previousAutoCommit = target.getAutoCommit();
            target.setAutoCommit(false);
            try {
                for (String table : CRITICAL_TABLES) {
                    if (!sourceTableExists(sqlite, table) || !targetTableExists(target, table)) {
                        continue;
                    }
                    if (recoveryAlreadyCompleted(target, source, sourceSize, table)) {
                        continue;
                    }

                    CopyResult result = copyTableRowSafely(sqlite, target, table);
                    recordRecovery(target, source, sourceSize, table, result);
                    target.commit();

                    importedTotal += result.importedRows();
                    skippedTotal += result.skippedRows();
                    processedTables++;
                    log.info(
                        "[MIGRATION] Critical legacy table {} recovered from {}: {} inserted, {} skipped.",
                        table,
                        source,
                        result.importedRows(),
                        result.skippedRows()
                    );
                }

                resetSequenceBackedColumns(target);
                target.commit();
            } catch (Exception ex) {
                target.rollback();
                throw ex;
            } finally {
                target.setAutoCommit(previousAutoCommit);
            }
        }

        if (processedTables > 0) {
            log.info(
                "[MIGRATION] Critical SQLite recovery completed: {} table(s), {} row(s) inserted, {} row(s) skipped.",
                processedTables,
                importedTotal,
                skippedTotal
            );
        }
    }

    private CopyResult copyTableRowSafely(Connection source, Connection target, String table) throws SQLException {
        Map<String, TargetColumn> targetColumns = loadTargetColumns(target, table);
        List<String> sourceColumns = loadSqliteColumns(source, table);
        List<ColumnMapping> mappings = new ArrayList<>();
        for (String sourceColumn : sourceColumns) {
            TargetColumn targetColumn = targetColumns.get(sourceColumn.toLowerCase(Locale.ROOT));
            if (targetColumn != null && safe(sourceColumn) && safe(targetColumn.name())) {
                mappings.add(new ColumnMapping(sourceColumn, targetColumn));
            }
        }
        if (mappings.isEmpty()) {
            return new CopyResult(0, 0);
        }

        String sourceSql = "SELECT " + String.join(", ", mappings.stream()
            .map(mapping -> quote(mapping.source()))
            .toList()) + " FROM " + quote(table);
        boolean overridingIdentity = mappings.stream().anyMatch(mapping -> mapping.target().identity());
        String insertSql = "INSERT INTO " + quote(table)
            + " (" + String.join(", ", mappings.stream().map(mapping -> quote(mapping.target().name())).toList()) + ")"
            + (overridingIdentity ? " OVERRIDING SYSTEM VALUE" : "")
            + " VALUES (" + String.join(", ", mappings.stream().map(ignored -> "?").toList()) + ")"
            + " ON CONFLICT DO NOTHING";

        long imported = 0L;
        long skipped = 0L;
        int loggedFailures = 0;
        try (Statement read = source.createStatement();
             ResultSet rows = read.executeQuery(sourceSql);
             PreparedStatement insert = target.prepareStatement(insertSql)) {
            while (rows.next()) {
                Savepoint rowSavepoint = target.setSavepoint();
                try {
                    for (int i = 0; i < mappings.size(); i++) {
                        bindValue(insert, i + 1, rows.getObject(i + 1), mappings.get(i).target());
                    }
                    int affected = insert.executeUpdate();
                    if (affected > 0) {
                        imported += affected;
                    }
                    target.releaseSavepoint(rowSavepoint);
                } catch (Exception ex) {
                    target.rollback(rowSavepoint);
                    skipped++;
                    if (loggedFailures < 5) {
                        log.warn("[MIGRATION] Skipping one {} row during PostgreSQL recovery: {}", table, ex.getMessage());
                        loggedFailures++;
                    }
                }
            }
        }
        if (skipped > loggedFailures) {
            log.warn("[MIGRATION] {} additional {} row(s) were skipped; duplicate/conflicting rows remain untouched.",
                skipped - loggedFailures, table);
        }
        return new CopyResult(imported, skipped);
    }

    private void bindValue(PreparedStatement statement, int index, Object value, TargetColumn target) throws SQLException {
        if (value == null) {
            statement.setNull(index, target.jdbcType());
            return;
        }

        String type = target.typeName().toLowerCase(Locale.ROOT);
        if (target.jdbcType() == Types.BOOLEAN || "bool".equals(type) || "boolean".equals(type)) {
            statement.setBoolean(index, asBoolean(value));
            return;
        }
        if (target.jdbcType() == Types.TIMESTAMP_WITH_TIMEZONE || "timestamptz".equals(type)) {
            OffsetDateTime parsed = asOffsetDateTime(value);
            if (parsed == null) {
                if (target.nullable()) {
                    statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
                    return;
                }
                throw new SQLException("Cannot parse required timestamp value '" + value + "' for " + target.name());
            }
            statement.setObject(index, parsed);
            return;
        }
        if (target.jdbcType() == Types.TIMESTAMP || "timestamp".equals(type)) {
            LocalDateTime parsed = asLocalDateTime(value);
            if (parsed == null) {
                if (target.nullable()) {
                    statement.setNull(index, Types.TIMESTAMP);
                    return;
                }
                throw new SQLException("Cannot parse required timestamp value '" + value + "' for " + target.name());
            }
            statement.setObject(index, parsed);
            return;
        }
        if (target.jdbcType() == Types.DATE) {
            try {
                statement.setObject(index, LocalDate.parse(value.toString().trim()));
            } catch (Exception ex) {
                if (target.nullable()) {
                    statement.setNull(index, Types.DATE);
                } else {
                    throw new SQLException("Cannot parse required date value '" + value + "' for " + target.name(), ex);
                }
            }
            return;
        }
        if (target.jdbcType() == Types.BIGINT) {
            statement.setLong(index, value instanceof Number number
                ? number.longValue()
                : Long.parseLong(value.toString().trim()));
            return;
        }
        if (target.jdbcType() == Types.INTEGER || target.jdbcType() == Types.SMALLINT) {
            statement.setInt(index, value instanceof Number number
                ? number.intValue()
                : Integer.parseInt(value.toString().trim()));
            return;
        }
        if (target.jdbcType() == Types.BINARY || target.jdbcType() == Types.VARBINARY || target.jdbcType() == Types.LONGVARBINARY) {
            if (value instanceof byte[] bytes) {
                statement.setBytes(index, bytes);
            } else {
                statement.setBytes(index, value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return;
        }
        if (value instanceof byte[] bytes) {
            statement.setBytes(index, bytes);
        } else {
            statement.setObject(index, value);
        }
    }

    private OffsetDateTime asOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof Number number) {
            return epochToOffsetDateTime(number.longValue());
        }

        String raw = value.toString().trim();
        if (raw.isEmpty()) {
            return null;
        }
        if (raw.matches("^-?\\d{9,17}$")) {
            try {
                return epochToOffsetDateTime(Long.parseLong(raw));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        String normalized = raw.replace(' ', 'T');
        if (normalized.matches(".*[+-]\\d{2}$")) {
            normalized += ":00";
        } else if (normalized.matches(".*[+-]\\d{4}$")) {
            normalized = normalized.substring(0, normalized.length() - 2)
                + ":" + normalized.substring(normalized.length() - 2);
        }
        try {
            return OffsetDateTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(raw).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        LocalDateTime local = parseLocalDateTime(raw);
        if (local != null) {
            ZoneOffset offset = ZoneId.systemDefault().getRules().getOffset(local);
            return local.atOffset(offset);
        }
        return null;
    }

    private LocalDateTime asLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof Number number) {
            return LocalDateTime.ofInstant(epochToOffsetDateTime(number.longValue()).toInstant(), ZoneId.systemDefault());
        }
        String raw = value.toString().trim();
        if (raw.matches("^-?\\d{9,17}$")) {
            try {
                return LocalDateTime.ofInstant(epochToOffsetDateTime(Long.parseLong(raw)).toInstant(), ZoneId.systemDefault());
            } catch (Exception ignored) {
                return null;
            }
        }
        LocalDateTime parsed = parseLocalDateTime(raw);
        if (parsed != null) {
            return parsed;
        }
        try {
            return LocalDate.parse(raw).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private LocalDateTime parseLocalDateTime(String raw) {
        String normalized = raw.replace('T', ' ');
        for (DateTimeFormatter formatter : LOCAL_DATE_TIME_FORMATTERS) {
            try {
                String candidate = formatter == DateTimeFormatter.ISO_LOCAL_DATE_TIME
                    ? raw.replace(' ', 'T')
                    : normalized;
                return LocalDateTime.parse(candidate, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private OffsetDateTime epochToOffsetDateTime(long value) {
        Instant instant = Math.abs(value) < 100_000_000_000L
            ? Instant.ofEpochSecond(value)
            : Instant.ofEpochMilli(value);
        return instant.atOffset(ZoneOffset.UTC);
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = value.toString().trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized) || "true".equals(normalized) || "t".equals(normalized)
            || "yes".equals(normalized) || "on".equals(normalized);
    }

    private Map<String, TargetColumn> loadTargetColumns(Connection target, String table) throws SQLException {
        Map<String, TargetColumn> columns = new LinkedHashMap<>();
        try (PreparedStatement statement = target.prepareStatement("""
            SELECT column_name, data_type, udt_name, is_identity, is_nullable
              FROM information_schema.columns
             WHERE table_schema = current_schema()
               AND lower(table_name) = lower(?)
             ORDER BY ordinal_position
            """)) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("column_name");
                    String dataType = rs.getString("data_type");
                    String udtName = rs.getString("udt_name");
                    columns.put(name.toLowerCase(Locale.ROOT), new TargetColumn(
                        name,
                        jdbcTypeFor(dataType, udtName),
                        udtName,
                        "YES".equalsIgnoreCase(rs.getString("is_identity")),
                        "YES".equalsIgnoreCase(rs.getString("is_nullable"))
                    ));
                }
            }
        }
        return columns;
    }

    private int jdbcTypeFor(String dataType, String udtName) {
        String type = (udtName != null ? udtName : dataType).toLowerCase(Locale.ROOT);
        return switch (type) {
            case "bool", "boolean" -> Types.BOOLEAN;
            case "int2", "smallint" -> Types.SMALLINT;
            case "int4", "integer" -> Types.INTEGER;
            case "int8", "bigint" -> Types.BIGINT;
            case "float4", "real" -> Types.REAL;
            case "float8", "double precision" -> Types.DOUBLE;
            case "numeric", "decimal" -> Types.NUMERIC;
            case "timestamp" -> Types.TIMESTAMP;
            case "timestamptz", "timestamp with time zone" -> Types.TIMESTAMP_WITH_TIMEZONE;
            case "date" -> Types.DATE;
            case "bytea" -> Types.VARBINARY;
            default -> Types.VARCHAR;
        };
    }

    private List<String> loadSqliteColumns(Connection source, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement statement = source.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + quote(table) + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    private boolean sourceTableExists(Connection source, String table) throws SQLException {
        try (PreparedStatement statement = source.prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND lower(name)=lower(?) LIMIT 1"
        )) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean targetTableExists(Connection target, String table) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement(
            "SELECT 1 FROM information_schema.tables WHERE table_schema=current_schema() AND lower(table_name)=lower(?) LIMIT 1"
        )) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean recoveryAlreadyCompleted(Connection target, Path source, long sourceSize, String table) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement("""
            SELECT 1
              FROM legacy_sqlite_recovery
             WHERE source_path = ?
               AND table_name = ?
               AND source_size = ?
             LIMIT 1
            """)) {
            statement.setString(1, source.toAbsolutePath().normalize().toString());
            statement.setString(2, table);
            statement.setLong(3, sourceSize);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void recordRecovery(Connection target, Path source, long sourceSize, String table, CopyResult result) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement("""
            INSERT INTO legacy_sqlite_recovery(source_path, table_name, source_size, imported_rows, skipped_rows, completed_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (source_path, table_name) DO UPDATE
               SET source_size = EXCLUDED.source_size,
                   imported_rows = EXCLUDED.imported_rows,
                   skipped_rows = EXCLUDED.skipped_rows,
                   completed_at = CURRENT_TIMESTAMP
            """)) {
            statement.setString(1, source.toAbsolutePath().normalize().toString());
            statement.setString(2, table);
            statement.setLong(3, sourceSize);
            statement.setLong(4, result.importedRows());
            statement.setLong(5, result.skippedRows());
            statement.executeUpdate();
        }
    }

    private void resetSequenceBackedColumns(Connection target) throws SQLException {
        List<SequenceColumn> sequenceColumns = new ArrayList<>();
        try (PreparedStatement columns = target.prepareStatement("""
            SELECT table_name, column_name
              FROM information_schema.columns
             WHERE table_schema = current_schema()
               AND data_type IN ('smallint', 'integer', 'bigint')
             ORDER BY table_name, ordinal_position
            """); ResultSet rs = columns.executeQuery()) {
            while (rs.next()) {
                String table = rs.getString("table_name");
                String column = rs.getString("column_name");
                if (!safe(table) || !safe(column)) {
                    continue;
                }
                try (PreparedStatement sequence = target.prepareStatement("SELECT pg_get_serial_sequence(?, ?)")) {
                    sequence.setString(1, target.getSchema() + "." + table);
                    sequence.setString(2, column);
                    try (ResultSet sequenceRs = sequence.executeQuery()) {
                        if (sequenceRs.next() && StringUtils.hasText(sequenceRs.getString(1))) {
                            sequenceColumns.add(new SequenceColumn(table, column, sequenceRs.getString(1)));
                        }
                    }
                }
            }
        }

        for (SequenceColumn column : sequenceColumns) {
            String maxSql = "SELECT MAX(" + quote(column.column()) + ") FROM " + quote(column.table());
            Long maxValue;
            try (Statement statement = target.createStatement(); ResultSet rs = statement.executeQuery(maxSql)) {
                maxValue = rs.next() ? (Long) rs.getObject(1, Long.class) : null;
            }
            long value = maxValue != null ? Math.max(1L, maxValue) : 1L;
            boolean called = maxValue != null;
            try (PreparedStatement reset = target.prepareStatement("SELECT setval(?::regclass, ?, ?)")) {
                reset.setString(1, column.sequenceName());
                reset.setLong(2, value);
                reset.setBoolean(3, called);
                reset.execute();
            }
        }
    }

    private Path resolvePanelRuntimeSource(Path workspaceRoot) {
        for (String key : List.of("APP_DB_PANEL_RUNTIME", "APP_DB_TICKETS")) {
            String configured = environment.getProperty(key);
            if (!StringUtils.hasText(configured)) {
                continue;
            }
            Path path = Paths.get(configured.trim());
            if (!path.isAbsolute()) {
                path = workspaceRoot.resolve(path);
            }
            path = path.normalize();
            if (isUsable(path)) {
                return path;
            }
        }

        Path panelHome = workspaceRoot.resolve("spring-panel");
        return List.of(
                panelHome.resolve("panel_runtime.db"),
                workspaceRoot.resolve("panel_runtime.db"),
                workspaceRoot.resolve("java-bot").resolve("panel_runtime.db")
            ).stream()
            .filter(this::isUsable)
            .max(Comparator.comparingLong(this::fileSize))
            .orElse(null);
    }

    private boolean isUsable(Path path) {
        return path != null && Files.isRegularFile(path) && fileSize(path) > 0L;
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ex) {
            return -1L;
        }
    }

    private Path locateWorkspaceRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.isDirectory(current.resolve(".git")) || Files.isDirectory(current.resolve("spring-panel"))) {
                return current;
            }
            current = current.getParent();
        }
        return start;
    }

    private boolean safe(String identifier) {
        return identifier != null && SAFE_IDENTIFIER.matcher(identifier).matches();
    }

    private String quote(String identifier) {
        if (!safe(identifier)) {
            throw new IllegalArgumentException("Unsafe SQL identifier: " + identifier);
        }
        return "\"" + identifier + "\"";
    }

    private record TargetColumn(String name, int jdbcType, String typeName, boolean identity, boolean nullable) {
    }

    private record ColumnMapping(String source, TargetColumn target) {
    }

    private record CopyResult(long importedRows, long skippedRows) {
    }

    private record SequenceColumn(String table, String column, String sequenceName) {
    }
}
