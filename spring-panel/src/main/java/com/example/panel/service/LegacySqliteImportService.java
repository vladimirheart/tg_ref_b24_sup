package com.example.panel.service;

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
import java.sql.DatabaseMetaData;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One-time compatibility importer for installations that used SQLite before
 * PostgreSQL became the default local runtime.
 *
 * <p>The importer is intentionally conservative:</p>
 * <ul>
 *     <li>it only runs in PostgreSQL mode;</li>
 *     <li>source SQLite files are opened read-only from the application point of view;</li>
 *     <li>only tables that already exist in the Flyway-managed PostgreSQL schema are copied;</li>
 *     <li>only columns that exist in both source and target are copied;</li>
 *     <li>PostgreSQL conflicts are ignored, so an already populated target is not overwritten;</li>
 *     <li>each source file is recorded in legacy_sqlite_imports after processing.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class LegacySqliteImportService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacySqliteImportService.class);
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private static final Set<String> SKIPPED_TABLES = Set.of(
        "flyway_schema_history",
        "spring_session",
        "spring_session_attributes",
        "legacy_sqlite_imports",
        "sqlite_sequence"
    );

    private static final List<String> PRIORITY_TABLES = List.of(
        "roles",
        "users",
        "user_authorities",
        "channels",
        "tickets",
        "messages",
        "chat_history",
        "feedbacks",
        "client_statuses",
        "client_usernames",
        "client_phones",
        "client_blacklist",
        "client_blacklist_history",
        "settings_parameters",
        "app_settings",
        "ticket_attributes",
        "ticket_spans",
        "pending_feedback_requests",
        "tasks",
        "task_seq",
        "ticket_active",
        "task_links",
        "task_people",
        "task_comments",
        "task_history",
        "notifications",
        "channel_notifications",
        "web_form_sessions",
        "knowledge_articles",
        "knowledge_article_files",
        "it_equipment_catalog",
        "objects",
        "object_passports",
        "rms_license_monitors",
        "ssl_certificate_monitors",
        "iiko_api_monitors",
        "monitoring_check_history",
        "rms_refresh_queue"
    );

    private static final List<SourceGroup> SOURCE_GROUPS = List.of(
        new SourceGroup("panel-runtime", List.of("APP_DB_PANEL_RUNTIME", "APP_DB_TICKETS"), List.of("panel_runtime.db", "tickets.db")),
        new SourceGroup("panel-identity", List.of("APP_DB_PANEL_IDENTITY", "APP_DB_USERS"), List.of("panel_identity.db", "users.db")),
        new SourceGroup("monitoring", List.of("APP_DB_MONITORING"), List.of("monitoring.db")),
        new SourceGroup("bot-runtime", List.of("APP_DB_BOT_RUNTIME", "APP_DB_BOT"), List.of("bot_runtime.db", "bot_database.db")),
        new SourceGroup("clients", List.of("APP_DB_CLIENTS"), List.of("clients.db")),
        new SourceGroup("knowledge", List.of("APP_DB_KNOWLEDGE"), List.of("knowledge_base.db")),
        new SourceGroup("objects", List.of("APP_DB_OBJECTS", "APP_DB_OBJECT_PASSPORTS"), List.of("objects.db", "object_passports.db"))
    );

    private static final Map<String, Map<String, String>> COLUMN_ALIASES = Map.of(
        "app_settings", Map.of("key", "setting_key"),
        "notifications", Map.of("user", "user_identity"),
        "it_equipment_catalog", Map.of("item_type", "equipment_type")
    );

    private final DataSource dataSource;
    private final Environment environment;
    private final PanelDatabaseRuntimeMode runtimeMode;
    private final LegacySqliteCompatibilitySettings compatibilitySettings;

    public LegacySqliteImportService(DataSource dataSource,
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
            log.debug("Legacy SQLite compatibility import is disabled.");
            return;
        }

        Path workspaceRoot = locateWorkspaceRoot(Paths.get("").toAbsolutePath().normalize());
        List<LegacySource> sources = locateSources(workspaceRoot);
        if (sources.isEmpty()) {
            log.debug("No legacy SQLite database files found for PostgreSQL import.");
            return;
        }

        long importedTotal = 0L;
        int processedSources = 0;
        try (Connection target = dataSource.getConnection()) {
            for (LegacySource source : sources) {
                if (wasImported(target, source.path())) {
                    continue;
                }
                ImportResult result = importSource(target, source);
                recordImport(target, source, result.importedRows());
                importedTotal += result.importedRows();
                processedSources++;
                if (!result.failedTables().isEmpty()) {
                    log.warn(
                        "Legacy SQLite source {} imported with {} skipped table(s): {}",
                        source.path(),
                        result.failedTables().size(),
                        String.join(", ", result.failedTables())
                    );
                }
            }
            resetIdentitySequences(target);
        }

        if (processedSources > 0) {
            log.info(
                "[MIGRATION] Legacy SQLite import completed: {} source file(s), {} row(s) copied into PostgreSQL.",
                processedSources,
                importedTotal
            );
        }
    }

    private ImportResult importSource(Connection target, LegacySource source) throws SQLException {
        boolean previousAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        long importedRows = 0L;
        List<String> failedTables = new ArrayList<>();

        String sqliteUrl = "jdbc:sqlite:" + source.path().toAbsolutePath().normalize();
        try (Connection sqlite = java.sql.DriverManager.getConnection(sqliteUrl)) {
            List<String> tables = loadSqliteTables(sqlite);
            tables.sort(tableComparator());

            for (String table : tables) {
                String normalizedTable = table.toLowerCase(Locale.ROOT);
                if (SKIPPED_TABLES.contains(normalizedTable) || !isSafeIdentifier(table)) {
                    continue;
                }
                if (!targetTableExists(target, table)) {
                    continue;
                }

                Savepoint savepoint = target.setSavepoint();
                try {
                    long copied = copyTable(sqlite, target, table);
                    importedRows += copied;
                    target.releaseSavepoint(savepoint);
                    if (copied > 0) {
                        log.debug("Imported {} row(s) from {} table {}", copied, source.label(), table);
                    }
                } catch (Exception ex) {
                    target.rollback(savepoint);
                    failedTables.add(table);
                    log.debug("Skipping legacy table {} from {}: {}", table, source.path(), ex.getMessage());
                }
            }
            target.commit();
            return new ImportResult(importedRows, failedTables);
        } catch (SQLException ex) {
            target.rollback();
            throw ex;
        } finally {
            target.setAutoCommit(previousAutoCommit);
        }
    }

    private long copyTable(Connection source, Connection target, String table) throws SQLException {
        Map<String, TargetColumn> targetColumns = loadTargetColumns(target, table);
        if (targetColumns.isEmpty()) {
            return 0L;
        }

        List<String> sourceColumns = loadSqliteColumns(source, table);
        if (sourceColumns.isEmpty()) {
            return 0L;
        }

        Map<String, String> aliases = COLUMN_ALIASES.getOrDefault(table.toLowerCase(Locale.ROOT), Map.of());
        List<ColumnMapping> mappings = new ArrayList<>();
        Set<String> mappedTargets = new HashSet<>();
        for (String sourceColumn : sourceColumns) {
            String sourceKey = sourceColumn.toLowerCase(Locale.ROOT);
            String targetName = aliases.getOrDefault(sourceKey, sourceColumn);
            TargetColumn targetColumn = targetColumns.get(targetName.toLowerCase(Locale.ROOT));
            if (targetColumn == null || mappedTargets.contains(targetColumn.name().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!isSafeIdentifier(sourceColumn) || !isSafeIdentifier(targetColumn.name())) {
                continue;
            }
            mappings.add(new ColumnMapping(sourceColumn, targetColumn));
            mappedTargets.add(targetColumn.name().toLowerCase(Locale.ROOT));
        }
        if (mappings.isEmpty()) {
            return 0L;
        }

        String sourceSelect = "SELECT " + joinQuotedSourceColumns(mappings) + " FROM " + quoteIdentifier(table);
        boolean overridesIdentity = mappings.stream().anyMatch(mapping -> mapping.target().identity());
        String targetInsert = "INSERT INTO " + quoteIdentifier(table)
            + " (" + joinQuotedTargetColumns(mappings) + ")"
            + (overridesIdentity ? " OVERRIDING SYSTEM VALUE" : "")
            + " VALUES (" + placeholders(mappings.size()) + ") ON CONFLICT DO NOTHING";

        long copied = 0L;
        try (Statement read = source.createStatement();
             ResultSet rows = read.executeQuery(sourceSelect);
             PreparedStatement insert = target.prepareStatement(targetInsert)) {
            int batchSize = 0;
            while (rows.next()) {
                for (int i = 0; i < mappings.size(); i++) {
                    Object value = rows.getObject(i + 1);
                    bindValue(insert, i + 1, value, mappings.get(i).target());
                }
                insert.addBatch();
                batchSize++;
                if (batchSize >= 250) {
                    copied += countBatchResult(insert.executeBatch());
                    batchSize = 0;
                }
            }
            if (batchSize > 0) {
                copied += countBatchResult(insert.executeBatch());
            }
        }
        return copied;
    }

    private void bindValue(PreparedStatement statement,
                           int index,
                           Object value,
                           TargetColumn target) throws SQLException {
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
            if (parsed != null) {
                statement.setObject(index, parsed);
            } else {
                statement.setObject(index, value);
            }
            return;
        }
        if (target.jdbcType() == Types.TIMESTAMP || "timestamp".equals(type)) {
            LocalDateTime parsed = asLocalDateTime(value);
            if (parsed != null) {
                statement.setObject(index, parsed);
            } else {
                statement.setObject(index, value);
            }
            return;
        }
        if (target.jdbcType() == Types.BIGINT) {
            if (value instanceof Number number) {
                statement.setLong(index, number.longValue());
            } else {
                statement.setLong(index, Long.parseLong(value.toString().trim()));
            }
            return;
        }
        if (target.jdbcType() == Types.INTEGER || target.jdbcType() == Types.SMALLINT) {
            if (value instanceof Number number) {
                statement.setInt(index, number.intValue());
            } else {
                statement.setInt(index, Integer.parseInt(value.toString().trim()));
            }
            return;
        }
        if (target.jdbcType() == Types.BINARY
            || target.jdbcType() == Types.VARBINARY
            || target.jdbcType() == Types.LONGVARBINARY) {
            if (value instanceof byte[] bytes) {
                statement.setBytes(index, bytes);
            } else {
                statement.setBytes(index, value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return;
        }
        if (value instanceof byte[] bytes) {
            statement.setBytes(index, bytes);
            return;
        }
        statement.setObject(index, value);
    }

    private Map<String, TargetColumn> loadTargetColumns(Connection target, String table) throws SQLException {
        Map<String, TargetColumn> result = new LinkedHashMap<>();
        String sql = """
            SELECT column_name, data_type, udt_name, is_identity
              FROM information_schema.columns
             WHERE table_schema = current_schema()
               AND lower(table_name) = lower(?)
             ORDER BY ordinal_position
            """;
        try (PreparedStatement statement = target.prepareStatement(sql)) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("column_name");
                    String dataType = rs.getString("data_type");
                    String udtName = rs.getString("udt_name");
                    boolean identity = "YES".equalsIgnoreCase(rs.getString("is_identity"));
                    int jdbcType = jdbcTypeFor(dataType, udtName);
                    result.put(name.toLowerCase(Locale.ROOT), new TargetColumn(name, jdbcType, udtName, identity));
                }
            }
        }
        return result;
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

    private List<String> loadSqliteTables(Connection source) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (PreparedStatement statement = source.prepareStatement(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
        ); ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    private List<String> loadSqliteColumns(Connection source, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement statement = source.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + quoteIdentifier(table) + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    private boolean targetTableExists(Connection target, String table) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement(
            "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND lower(table_name) = lower(?))"
        )) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private boolean wasImported(Connection target, Path source) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement(
            "SELECT 1 FROM legacy_sqlite_imports WHERE source_path = ? LIMIT 1"
        )) {
            statement.setString(1, source.toAbsolutePath().normalize().toString());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void recordImport(Connection target, LegacySource source, long rows) throws SQLException {
        long size = fileSize(source.path());
        OffsetDateTime modifiedAt = null;
        try {
            modifiedAt = Files.getLastModifiedTime(source.path()).toInstant().atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            // File timestamp is diagnostic only.
        }
        try (PreparedStatement statement = target.prepareStatement("""
            INSERT INTO legacy_sqlite_imports(source_path, source_size, source_modified_at, imported_rows)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (source_path) DO NOTHING
            """)) {
            statement.setString(1, source.path().toAbsolutePath().normalize().toString());
            statement.setLong(2, Math.max(0L, size));
            if (modifiedAt != null) {
                statement.setObject(3, modifiedAt);
            } else {
                statement.setNull(3, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            statement.setLong(4, rows);
            statement.executeUpdate();
        }
    }

    private void resetIdentitySequences(Connection target) {
        String sql = """
            SELECT table_name, column_name
              FROM information_schema.columns
             WHERE table_schema = current_schema()
               AND is_identity = 'YES'
            """;
        try (Statement statement = target.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            List<IdentityColumn> identities = new ArrayList<>();
            while (rs.next()) {
                String table = rs.getString("table_name");
                String column = rs.getString("column_name");
                if (isSafeIdentifier(table) && isSafeIdentifier(column)) {
                    identities.add(new IdentityColumn(table, column));
                }
            }
            for (IdentityColumn identity : identities) {
                String reset = "SELECT setval(pg_get_serial_sequence('"
                    + identity.table() + "', '" + identity.column() + "'), "
                    + "COALESCE(MAX(" + quoteIdentifier(identity.column()) + "), 1), "
                    + "MAX(" + quoteIdentifier(identity.column()) + ") IS NOT NULL) FROM "
                    + quoteIdentifier(identity.table());
                try (Statement resetStatement = target.createStatement()) {
                    resetStatement.execute(reset);
                } catch (SQLException ex) {
                    log.debug("Unable to reset identity sequence for {}.{}: {}", identity.table(), identity.column(), ex.getMessage());
                }
            }
        } catch (SQLException ex) {
            log.debug("Unable to reset PostgreSQL identity sequences after SQLite import: {}", ex.getMessage());
        }
    }

    private List<LegacySource> locateSources(Path workspaceRoot) {
        Path panelHome = workspaceRoot.resolve("spring-panel").normalize();
        LinkedHashMap<Path, LegacySource> resolved = new LinkedHashMap<>();
        for (SourceGroup group : SOURCE_GROUPS) {
            Path source = resolveSource(group, workspaceRoot, panelHome);
            if (source == null) {
                continue;
            }
            Path normalized = source.toAbsolutePath().normalize();
            resolved.putIfAbsent(normalized, new LegacySource(group.label(), normalized));
        }
        return new ArrayList<>(resolved.values());
    }

    private Path resolveSource(SourceGroup group, Path workspaceRoot, Path panelHome) {
        for (String envKey : group.envKeys()) {
            String configured = environment.getProperty(envKey);
            if (!StringUtils.hasText(configured)) {
                continue;
            }
            Path candidate = normalizePath(configured, workspaceRoot);
            if (isUsableSqliteFile(candidate)) {
                return candidate;
            }
        }

        List<Path> candidates = new ArrayList<>();
        for (String fileName : group.fileNames()) {
            candidates.add(panelHome.resolve(fileName));
            candidates.add(workspaceRoot.resolve(fileName));
            candidates.add(workspaceRoot.resolve("data").resolve(fileName));
            candidates.add(panelHome.resolve("data").resolve(fileName));
        }
        return candidates.stream()
            .filter(this::isUsableSqliteFile)
            .max(Comparator.comparingLong(this::fileSize))
            .orElse(null);
    }

    private boolean isUsableSqliteFile(Path path) {
        return path != null && Files.isRegularFile(path) && fileSize(path) > 0L;
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (Exception ex) {
            return -1L;
        }
    }

    private Path normalizePath(String raw, Path workspaceRoot) {
        Path path = Paths.get(raw.trim());
        if (!path.isAbsolute()) {
            path = workspaceRoot.resolve(path);
        }
        return path.normalize();
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

    private Comparator<String> tableComparator() {
        Map<String, Integer> priority = new HashMap<>();
        for (int i = 0; i < PRIORITY_TABLES.size(); i++) {
            priority.put(PRIORITY_TABLES.get(i), i);
        }
        return Comparator
            .comparingInt((String table) -> priority.getOrDefault(table.toLowerCase(Locale.ROOT), Integer.MAX_VALUE))
            .thenComparing(String::compareToIgnoreCase);
    }

    private String joinQuotedSourceColumns(List<ColumnMapping> mappings) {
        return String.join(", ", mappings.stream().map(mapping -> quoteIdentifier(mapping.source())).toList());
    }

    private String joinQuotedTargetColumns(List<ColumnMapping> mappings) {
        return String.join(", ", mappings.stream().map(mapping -> quoteIdentifier(mapping.target().name())).toList());
    }

    private String placeholders(int count) {
        String[] values = new String[count];
        Arrays.fill(values, "?");
        return String.join(", ", values);
    }

    private long countBatchResult(int[] results) {
        long count = 0L;
        for (int result : results) {
            if (result > 0) {
                count += result;
            } else if (result == Statement.SUCCESS_NO_INFO) {
                count++;
            }
        }
        return count;
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = value.toString().trim().toLowerCase(Locale.ROOT);
        return normalized.equals("1") || normalized.equals("true") || normalized.equals("t")
            || normalized.equals("yes") || normalized.equals("on");
    }

    private OffsetDateTime asOffsetDateTime(Object value) {
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        String raw = value.toString().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException ignored) {
        }
        String normalized = raw.replace(' ', 'T');
        if (normalized.matches(".*[+-]\\d{2}$")) {
            normalized += ":00";
        } else if (normalized.matches(".*[+-]\\d{4}$")) {
            normalized = normalized.substring(0, normalized.length() - 2) + ":" + normalized.substring(normalized.length() - 2);
        }
        try {
            return OffsetDateTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
        }
        try {
            LocalDateTime local = LocalDateTime.parse(normalized);
            ZoneOffset offset = ZoneId.systemDefault().getRules().getOffset(local);
            return local.atOffset(offset);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(raw).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
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
        String raw = value.toString().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.replace(' ', 'T'));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(raw).atStartOfDay();
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private boolean isSafeIdentifier(String value) {
        return value != null && SAFE_IDENTIFIER.matcher(value).matches();
    }

    private String quoteIdentifier(String identifier) {
        if (!isSafeIdentifier(identifier)) {
            throw new IllegalArgumentException("Unsafe SQL identifier: " + identifier);
        }
        return "\"" + identifier + "\"";
    }

    private record SourceGroup(String label, List<String> envKeys, List<String> fileNames) {
    }

    private record LegacySource(String label, Path path) {
    }

    private record TargetColumn(String name, int jdbcType, String typeName, boolean identity) {
    }

    private record ColumnMapping(String source, TargetColumn target) {
    }

    private record ImportResult(long importedRows, List<String> failedTables) {
    }

    private record IdentityColumn(String table, String column) {
    }
}
