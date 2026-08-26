package com.example.panel.service;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeWorkload;

import com.example.panel.config.BotProcessProperties;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 110)
@RuntimeWorkload(
    id = "legacy-bot-shard-consolidation",
    roles = {RuntimeRole.MIGRATOR},
    replicaPolicy = RuntimeReplicaPolicy.SINGLETON
)public class LegacyBotShardConsolidationService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyBotShardConsolidationService.class);
    private static final long IMPORT_LOCK_ID = 1018302L;
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern SHARD_FILE_NAME = Pattern.compile("^bot-(\\d+)\\.db$", Pattern.CASE_INSENSITIVE);

    private final DataSource dataSource;
    private final PanelDatabaseRuntimeMode runtimeMode;
    private final BotProcessProperties botProcessProperties;

    public LegacyBotShardConsolidationService(DataSource dataSource,
                                              PanelDatabaseRuntimeMode runtimeMode,
                                              BotProcessProperties botProcessProperties) {
        this.dataSource = dataSource;
        this.runtimeMode = runtimeMode;
        this.botProcessProperties = botProcessProperties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!"postgresql".equalsIgnoreCase(runtimeMode.modeLabel())) {
            return;
        }

        Path shardDirectory = botProcessProperties.resolveDatabaseDir();
        List<Path> shardFiles = findShardFiles(shardDirectory);
        if (shardFiles.isEmpty()) {
            log.debug("No legacy bot shard files found in {}", shardDirectory);
            return;
        }

        try (Connection target = dataSource.getConnection()) {
            if (!tryAcquireImportLock(target)) {
                log.info("Skipping legacy bot shard consolidation because another backend instance owns the import lock.");
                return;
            }
            try {
                long importedRows = 0L;
                int processedFiles = 0;
                for (Path shardFile : shardFiles) {
                    ImportMarker marker = loadImportMarker(target, shardFile);
                    if (marker != null) {
                        warnIfShardChangedAfterImport(shardFile, marker);
                        continue;
                    }
                    long imported = importShard(target, shardFile);
                    importedRows += imported;
                    processedFiles++;
                }
                if (processedFiles > 0) {
                    log.info(
                        "[MIGRATION] Legacy bot shard consolidation completed: {} file(s), {} row(s) merged into canonical PostgreSQL tables.",
                        processedFiles,
                        importedRows
                    );
                }
            } finally {
                releaseImportLock(target);
            }
        }
    }

    List<Path> findShardFiles(Path shardDirectory) throws IOException {
        if (shardDirectory == null || !Files.isDirectory(shardDirectory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(shardDirectory)) {
            return paths
                .filter(Files::isRegularFile)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .filter(path -> shardChannelId(path) != null)
                .sorted((left, right) -> {
                    Long leftId = shardChannelId(left);
                    Long rightId = shardChannelId(right);
                    if (leftId == null && rightId == null) {
                        return left.compareTo(right);
                    }
                    if (leftId == null) {
                        return 1;
                    }
                    if (rightId == null) {
                        return -1;
                    }
                    return Long.compare(leftId, rightId);
                })
                .toList();
        }
    }

    private boolean tryAcquireImportLock(Connection target) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, IMPORT_LOCK_ID);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private void releaseImportLock(Connection target) {
        try (PreparedStatement statement = target.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, IMPORT_LOCK_ID);
            statement.execute();
        } catch (SQLException ex) {
            log.debug("Unable to release legacy bot shard advisory lock: {}", ex.getMessage());
        }
    }

    private ImportMarker loadImportMarker(Connection target, Path shardFile) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement("""
            SELECT source_size, source_modified_at
              FROM legacy_bot_shard_imports
             WHERE source_path = ?
             LIMIT 1
            """)) {
            statement.setString(1, shardFile.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new ImportMarker(
                    rs.getLong("source_size"),
                    rs.getObject("source_modified_at", OffsetDateTime.class)
                );
            }
        }
    }

    private void warnIfShardChangedAfterImport(Path shardFile, ImportMarker marker) {
        long currentSize = fileSize(shardFile);
        OffsetDateTime currentModifiedAt = fileModifiedAt(shardFile);
        boolean changed = marker.sourceSize() != currentSize
            || !Objects.equals(
            marker.sourceModifiedAt() != null ? marker.sourceModifiedAt().toInstant() : null,
            currentModifiedAt != null ? currentModifiedAt.toInstant() : null
        );
        if (changed) {
            log.warn(
                "Legacy bot shard {} changed after it had already been consolidated. Skipping automatic re-import to avoid duplicate canonical records.",
                shardFile
            );
        }
    }

    private long importShard(Connection target, Path shardFile) throws SQLException {
        boolean previousAutoCommit = target.getAutoCommit();
        target.setAutoCommit(false);
        try (Connection source = DriverManager.getConnection("jdbc:sqlite:" + shardFile.toAbsolutePath().normalize())) {
            long importedRows = 0L;
            importedRows += importBotUsers(source, target);
            importedRows += importBotChatHistory(source, target);
            importedRows += importApplications(source, target);
            recordImport(target, shardFile, importedRows);
            target.commit();
            return importedRows;
        } catch (SQLException ex) {
            target.rollback();
            throw ex;
        } finally {
            target.setAutoCommit(previousAutoCommit);
        }
    }

    private long importBotUsers(Connection source, Connection target) throws SQLException {
        if (!tableExists(source, "bot_users")) {
            return 0L;
        }
        Set<String> columns = loadSqliteColumns(source, "bot_users");
        String sql = """
            INSERT INTO bot_users(user_id, username, first_name, last_name, registered_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (user_id) DO UPDATE SET
                username = COALESCE(bot_users.username, EXCLUDED.username),
                first_name = COALESCE(bot_users.first_name, EXCLUDED.first_name),
                last_name = COALESCE(bot_users.last_name, EXCLUDED.last_name),
                registered_at = CASE
                    WHEN bot_users.registered_at IS NULL THEN EXCLUDED.registered_at
                    WHEN EXCLUDED.registered_at IS NULL THEN bot_users.registered_at
                    ELSE LEAST(bot_users.registered_at, EXCLUDED.registered_at)
                END
            """;
        try (PreparedStatement read = source.prepareStatement("""
                SELECT %s AS user_id,
                       %s AS username,
                       %s AS first_name,
                       %s AS last_name,
                       %s AS registered_at
                  FROM "bot_users"
                 WHERE %s IS NOT NULL
                """.formatted(
            sourceColumn(columns, "user_id"),
            sourceColumn(columns, "username"),
            sourceColumn(columns, "first_name"),
            sourceColumn(columns, "last_name"),
            sourceColumn(columns, "registered_at"),
            sourceColumn(columns, "user_id")
        ));
             ResultSet rows = read.executeQuery();
             PreparedStatement insert = target.prepareStatement(sql)) {
            long imported = 0L;
            while (rows.next()) {
                Long userId = asLong(rows.getObject("user_id"));
                if (userId == null) {
                    continue;
                }
                insert.setLong(1, userId);
                insert.setString(2, trimToNull(rows.getString("username")));
                insert.setString(3, trimToNull(rows.getString("first_name")));
                insert.setString(4, trimToNull(rows.getString("last_name")));
                bindOffsetDateTime(insert, 5, asOffsetDateTime(rows.getObject("registered_at")));
                imported += Math.max(insert.executeUpdate(), 0);
            }
            return imported;
        }
    }

    private long importBotChatHistory(Connection source, Connection target) throws SQLException {
        if (!tableExists(source, "bot_chat_history")) {
            return 0L;
        }
        Set<String> columns = loadSqliteColumns(source, "bot_chat_history");
        try (PreparedStatement read = source.prepareStatement("""
                SELECT %s AS user_id,
                       %s AS message_id,
                       %s AS ticket_id,
                       %s AS message,
                       %s AS message_type,
                       %s AS attachment_path,
                       %s AS timestamp
                  FROM "bot_chat_history"
                 WHERE %s IS NOT NULL
                """.formatted(
            sourceColumn(columns, "user_id"),
            sourceColumn(columns, "message_id"),
            sourceColumn(columns, "ticket_id"),
            sourceColumn(columns, "text"),
            sourceColumn(columns, "message_type"),
            sourceColumn(columns, "attachment_path"),
            sourceColumn(columns, "timestamp"),
            sourceColumn(columns, "user_id")
        ));
             ResultSet rows = read.executeQuery();
             PreparedStatement ensureUser = target.prepareStatement("""
                 INSERT INTO bot_users(user_id)
                 VALUES (?)
                 ON CONFLICT (user_id) DO NOTHING
                 """);
             PreparedStatement insert = target.prepareStatement("""
                 INSERT INTO bot_chat_history(user_id, message, timestamp, message_id, message_type, ticket_id, attachment_path)
                 VALUES (?, ?, ?, ?, ?, ?, ?)
                 """)) {
            long imported = 0L;
            while (rows.next()) {
                Long userId = asLong(rows.getObject("user_id"));
                if (userId == null) {
                    continue;
                }
                ensureUser.setLong(1, userId);
                ensureUser.executeUpdate();

                insert.setLong(1, userId);
                insert.setString(2, trimToNull(rows.getString("message")));
                bindOffsetDateTime(insert, 3, asOffsetDateTime(rows.getObject("timestamp")));
                bindLong(insert, 4, asLong(rows.getObject("message_id")));
                insert.setString(5, trimToNull(rows.getString("message_type")));
                insert.setString(6, trimToNull(rows.getString("ticket_id")));
                insert.setString(7, trimToNull(rows.getString("attachment_path")));
                imported += Math.max(insert.executeUpdate(), 0);
            }
            return imported;
        }
    }

    private long importApplications(Connection source, Connection target) throws SQLException {
        if (!tableExists(source, "applications")) {
            return 0L;
        }
        Set<String> columns = loadSqliteColumns(source, "applications");
        try (PreparedStatement read = source.prepareStatement("""
                SELECT %s AS user_id,
                       %s AS problem_description,
                       %s AS photo_path,
                       %s AS status,
                       %s AS created_at,
                       %s AS b24_contact_id,
                       %s AS b24_deal_id
                  FROM "applications"
                 WHERE %s IS NOT NULL
                """.formatted(
            sourceColumn(columns, "user_id"),
            sourceColumn(columns, "problem_description"),
            sourceColumn(columns, "photo_path"),
            sourceColumn(columns, "status"),
            sourceColumn(columns, "created_at"),
            sourceColumn(columns, "b24_contact_id"),
            sourceColumn(columns, "b24_deal_id"),
            sourceColumn(columns, "user_id")
        ));
             ResultSet rows = read.executeQuery();
             PreparedStatement ensureUser = target.prepareStatement("""
                 INSERT INTO bot_users(user_id)
                 VALUES (?)
                 ON CONFLICT (user_id) DO NOTHING
                 """);
             PreparedStatement insert = target.prepareStatement("""
                 INSERT INTO applications(user_id, problem_description, photo_path, status, created_at, b24_contact_id, b24_deal_id)
                 VALUES (?, ?, ?, ?, ?, ?, ?)
                 """)) {
            long imported = 0L;
            while (rows.next()) {
                Long userId = asLong(rows.getObject("user_id"));
                if (userId == null) {
                    continue;
                }
                ensureUser.setLong(1, userId);
                ensureUser.executeUpdate();

                insert.setLong(1, userId);
                insert.setString(2, trimToNull(rows.getString("problem_description")));
                insert.setString(3, trimToNull(rows.getString("photo_path")));
                insert.setString(4, trimToNull(rows.getString("status")));
                bindOffsetDateTime(insert, 5, asOffsetDateTime(rows.getObject("created_at")));
                bindLong(insert, 6, asLong(rows.getObject("b24_contact_id")));
                bindLong(insert, 7, asLong(rows.getObject("b24_deal_id")));
                imported += Math.max(insert.executeUpdate(), 0);
            }
            return imported;
        }
    }

    private void recordImport(Connection target, Path shardFile, long importedRows) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement("""
            INSERT INTO legacy_bot_shard_imports(source_path, source_size, source_modified_at, imported_rows)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (source_path) DO NOTHING
            """)) {
            statement.setString(1, shardFile.toString());
            statement.setLong(2, Math.max(0L, fileSize(shardFile)));
            bindOffsetDateTime(statement, 3, fileModifiedAt(shardFile));
            statement.setLong(4, importedRows);
            statement.executeUpdate();
        }
    }

    private boolean tableExists(Connection source, String table) throws SQLException {
        try (PreparedStatement statement = source.prepareStatement("""
            SELECT 1
              FROM sqlite_master
             WHERE type = 'table'
               AND lower(name) = lower(?)
             LIMIT 1
            """)) {
            statement.setString(1, table);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Set<String> loadSqliteColumns(Connection source, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement statement = source.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + quoteIdentifier(table) + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name").toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(columns);
    }

    private String sourceColumn(Set<String> columns, String column) {
        String normalized = column.toLowerCase(Locale.ROOT);
        return columns.contains(normalized) ? quoteIdentifier(column) : "NULL";
    }

    private void bindOffsetDateTime(PreparedStatement statement, int index, OffsetDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
            return;
        }
        statement.setObject(index, value);
    }

    private void bindLong(PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
            return;
        }
        statement.setLong(index, value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String raw = value.toString().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private OffsetDateTime asOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
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
            return local.atOffset(ZoneId.systemDefault().getRules().getOffset(local));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(raw).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(raw).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ex) {
            return -1L;
        }
    }

    private OffsetDateTime fileModifiedAt(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant().atOffset(ZoneOffset.UTC);
        } catch (IOException ex) {
            return null;
        }
    }

    private String quoteIdentifier(String identifier) {
        if (identifier == null || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("Unsafe SQL identifier: " + identifier);
        }
        return "\"" + identifier + "\"";
    }

    private Long shardChannelId(Path path) {
        if (path == null || path.getFileName() == null) {
            return null;
        }
        java.util.regex.Matcher matcher = SHARD_FILE_NAME.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record ImportMarker(long sourceSize, OffsetDateTime sourceModifiedAt) {
    }
}
