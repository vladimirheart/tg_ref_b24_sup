package com.example.panel.service;

import com.example.panel.config.BotSqliteDataSourceProperties;
import com.example.panel.config.LegacySqliteCompatibilitySettings;
import com.example.panel.config.MonitoringSqliteDataSourceProperties;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.config.SqliteDataSourceProperties;
import com.example.panel.converter.LenientOffsetDateTimeConverter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Order(Ordered.HIGHEST_PRECEDENCE + 120)
public class LegacyMonitoringHistoryCompactionService implements ApplicationRunner {

    static final int RETENTION_DAYS = 30;
    private static final Logger log = LoggerFactory.getLogger(LegacyMonitoringHistoryCompactionService.class);
    private static final LenientOffsetDateTimeConverter DATE_TIME_CONVERTER = new LenientOffsetDateTimeConverter();

    private final DataSource dataSource;
    private final PanelDatabaseRuntimeMode databaseRuntimeMode;
    private final LegacySqliteCompatibilitySettings compatibilitySettings;
    private final SqliteDataSourceProperties primaryProperties;
    private final MonitoringSqliteDataSourceProperties monitoringProperties;
    private final BotSqliteDataSourceProperties botProperties;

    public LegacyMonitoringHistoryCompactionService(DataSource dataSource,
                                                    PanelDatabaseRuntimeMode databaseRuntimeMode,
                                                    LegacySqliteCompatibilitySettings compatibilitySettings,
                                                    SqliteDataSourceProperties primaryProperties,
                                                    MonitoringSqliteDataSourceProperties monitoringProperties,
                                                    BotSqliteDataSourceProperties botProperties) {
        this.dataSource = dataSource;
        this.databaseRuntimeMode = databaseRuntimeMode;
        this.compatibilitySettings = compatibilitySettings;
        this.primaryProperties = primaryProperties;
        this.monitoringProperties = monitoringProperties;
        this.botProperties = botProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"postgresql".equalsIgnoreCase(databaseRuntimeMode.modeLabel())) {
            return;
        }
        if (!compatibilitySettings.isAutoImportEnabled()) {
            log.debug("Legacy monitoring history compaction skipped because SQLite auto-import is disabled.");
            return;
        }
        if (!compatibilitySettings.isMonitoringHistoryCompactionEnabled()) {
            log.debug(
                "Legacy monitoring history compaction is disabled. Set {}=true for a one-time verified cleanup.",
                LegacySqliteCompatibilitySettings.MONITORING_HISTORY_COMPACT_ENV
            );
            return;
        }

        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(RETENTION_DAYS);
        Set<Path> sources = resolveExistingSources();
        if (sources.isEmpty()) {
            log.info("Legacy monitoring history compaction found no SQLite source files.");
            return;
        }

        try (Connection target = dataSource.getConnection()) {
            if (!targetTableExists(target)) {
                log.warn("Legacy monitoring history compaction skipped because PostgreSQL monitoring_check_history is missing.");
                return;
            }
            for (Path source : sources) {
                try {
                    CompactionResult result = compactSource(target, source, cutoff);
                    if (result.sourceRows() > 0 || result.vacuumed()) {
                        log.info(
                            "Legacy monitoring history compaction: source={}, sourceRows={}, copiedFresh={}, alreadyPresentFresh={}, expiredDiscarded={}, deletedSource={}, vacuumed={}",
                            source,
                            result.sourceRows(),
                            result.copiedFresh(),
                            result.alreadyPresentFresh(),
                            result.expiredDiscarded(),
                            result.deletedSource(),
                            result.vacuumed()
                        );
                    }
                } catch (Exception ex) {
                    log.warn(
                        "Legacy monitoring history compaction failed for {}. Source rows were preserved when verification was incomplete: {}",
                        source,
                        ex.getMessage(),
                        ex
                    );
                }
            }
        } catch (SQLException ex) {
            log.warn("Legacy monitoring history compaction could not access canonical PostgreSQL: {}", ex.getMessage(), ex);
        }
    }

    CompactionResult compactSource(Connection target, Path source, OffsetDateTime cutoff) throws SQLException {
        if (source == null || !Files.isRegularFile(source)) {
            return CompactionResult.skipped();
        }
        String sqliteUrl = "jdbc:sqlite:" + source.toAbsolutePath().normalize();
        try (Connection sqlite = java.sql.DriverManager.getConnection(sqliteUrl)) {
            if (!sourceTableExists(sqlite)) {
                return CompactionResult.skipped();
            }

            Set<HistoryFingerprint> targetRows = loadTargetFingerprints(target, cutoff);
            int sourceRows = 0;
            int copiedFresh = 0;
            int alreadyPresentFresh = 0;
            int expiredDiscarded = 0;
            int unverifiableRows = 0;

            boolean previousAutoCommit = target.getAutoCommit();
            target.setAutoCommit(false);
            try (Statement read = sqlite.createStatement();
                 ResultSet rows = read.executeQuery("""
                     SELECT monitor_kind, monitor_id, check_kind, status, summary,
                            details_excerpt, http_status, duration_ms, created_at
                       FROM monitoring_check_history
                      ORDER BY id ASC
                     """);
                 PreparedStatement insert = target.prepareStatement("""
                     INSERT INTO monitoring_check_history (
                         monitor_kind, monitor_id, check_kind, status, summary,
                         details_excerpt, http_status, duration_ms, created_at
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
                while (rows.next()) {
                    sourceRows++;
                    HistoryRow row = readHistoryRow(rows);
                    if (row.createdAt() == null) {
                        unverifiableRows++;
                        continue;
                    }
                    if (row.createdAt().isBefore(cutoff)) {
                        expiredDiscarded++;
                        continue;
                    }
                    if (!row.isValid()) {
                        unverifiableRows++;
                        continue;
                    }
                    HistoryFingerprint fingerprint = row.fingerprint();
                    if (containsEquivalentFingerprint(targetRows, fingerprint)) {
                        alreadyPresentFresh++;
                        continue;
                    }
                    bindInsert(insert, row);
                    insert.addBatch();
                    targetRows.add(fingerprint);
                    copiedFresh++;
                }
                if (copiedFresh > 0) {
                    insert.executeBatch();
                }
                target.commit();
            } catch (Exception ex) {
                target.rollback();
                if (ex instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw new SQLException("Unable to verify/copy legacy monitoring history", ex);
            } finally {
                target.setAutoCommit(previousAutoCommit);
            }

            if (unverifiableRows > 0) {
                log.warn(
                    "Legacy monitoring history source {} has {} row(s) with invalid/unparseable current-window data; refusing destructive cleanup.",
                    source,
                    unverifiableRows
                );
                return new CompactionResult(
                    sourceRows,
                    copiedFresh,
                    alreadyPresentFresh,
                    expiredDiscarded,
                    0,
                    false,
                    false
                );
            }

            int deletedSource;
            try (Statement cleanup = sqlite.createStatement()) {
                deletedSource = cleanup.executeUpdate("DELETE FROM monitoring_check_history");
            }
            boolean vacuumed = vacuum(sqlite, source);
            return new CompactionResult(
                sourceRows,
                copiedFresh,
                alreadyPresentFresh,
                expiredDiscarded,
                deletedSource,
                vacuumed,
                true
            );
        }
    }

    private Set<Path> resolveExistingSources() {
        LinkedHashSet<Path> result = new LinkedHashSet<>();
        addExistingConfiguredPath(result, primaryProperties.getPath());
        addExistingConfiguredPath(result, monitoringProperties.getPath());
        addExistingConfiguredPath(result, botProperties.getPath());
        addExistingConfiguredPath(result, "panel_runtime.db");
        addExistingConfiguredPath(result, "monitoring.db");
        addExistingConfiguredPath(result, "bot_runtime.db");
        addExistingConfiguredPath(result, "bot_database.db");
        return result;
    }

    private void addExistingConfiguredPath(Set<Path> result, String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return;
        }
        Path configured = Paths.get(rawPath.trim());
        if (configured.isAbsolute()) {
            Path normalized = configured.normalize();
            if (Files.isRegularFile(normalized)) {
                result.add(normalized);
            }
            return;
        }
        Path working = Paths.get("").toAbsolutePath().normalize();
        Path probe = working;
        while (probe != null) {
            Path direct = probe.resolve(configured).normalize();
            if (Files.isRegularFile(direct)) {
                result.add(direct);
                return;
            }
            Path springPanel = probe.resolve("spring-panel").resolve(configured).normalize();
            if (Files.isRegularFile(springPanel)) {
                result.add(springPanel);
                return;
            }
            probe = probe.getParent();
        }
    }

    private boolean targetTableExists(Connection target) throws SQLException {
        try (PreparedStatement statement = target.prepareStatement(
            "SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'monitoring_check_history' LIMIT 1"
        ); ResultSet rs = statement.executeQuery()) {
            return rs.next();
        }
    }

    private boolean sourceTableExists(Connection sqlite) throws SQLException {
        try (PreparedStatement statement = sqlite.prepareStatement(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'monitoring_check_history' LIMIT 1"
        ); ResultSet rs = statement.executeQuery()) {
            return rs.next();
        }
    }

    private Set<HistoryFingerprint> loadTargetFingerprints(Connection target, OffsetDateTime cutoff) throws SQLException {
        LinkedHashSet<HistoryFingerprint> result = new LinkedHashSet<>();
        try (PreparedStatement statement = target.prepareStatement("""
            SELECT monitor_kind, monitor_id, check_kind, status, summary,
                   details_excerpt, http_status, duration_ms, created_at
              FROM monitoring_check_history
             WHERE created_at >= ?
            """)) {
            statement.setObject(1, cutoff);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    HistoryRow row = readHistoryRow(rows);
                    if (row.createdAt() != null && row.isValid()) {
                        result.add(row.fingerprint());
                    }
                }
            }
        }
        return result;
    }

    private boolean containsEquivalentFingerprint(Set<HistoryFingerprint> targetRows,
                                                  HistoryFingerprint candidate) {
        if (targetRows.contains(candidate)) {
            return true;
        }
        Instant createdAt = candidate.createdAt();
        if (createdAt == null) {
            return false;
        }
        // PostgreSQL TIMESTAMP WITH TIME ZONE has microsecond resolution. JDBC databases may
        // round or truncate the original nanoseconds when persisting the same event, so accept
        // the two adjacent microsecond representations as the same content fingerprint.
        return targetRows.contains(candidate.withCreatedAt(createdAt.minus(1, ChronoUnit.MICROS)))
            || targetRows.contains(candidate.withCreatedAt(createdAt.plus(1, ChronoUnit.MICROS)));
    }
    private HistoryRow readHistoryRow(ResultSet rs) throws SQLException {
        return new HistoryRow(
            rs.getString("monitor_kind"),
            nullableLong(rs, "monitor_id"),
            rs.getString("check_kind"),
            rs.getString("status"),
            rs.getString("summary"),
            rs.getString("details_excerpt"),
            nullableInteger(rs, "http_status"),
            nullableLong(rs, "duration_ms"),
            readOffsetDateTime(rs.getObject("created_at"))
        );
    }

    private void bindInsert(PreparedStatement statement, HistoryRow row) throws SQLException {
        statement.setString(1, row.monitorKind());
        statement.setLong(2, row.monitorId());
        statement.setString(3, row.checkKind());
        statement.setString(4, row.status());
        statement.setString(5, row.summary());
        statement.setString(6, row.detailsExcerpt());
        if (row.httpStatus() == null) {
            statement.setObject(7, null);
        } else {
            statement.setInt(7, row.httpStatus());
        }
        if (row.durationMs() == null) {
            statement.setObject(8, null);
        } else {
            statement.setLong(8, row.durationMs());
        }
        statement.setObject(9, row.createdAt());
    }

    private boolean vacuum(Connection sqlite, Path source) {
        try (Statement statement = sqlite.createStatement()) {
            statement.execute("VACUUM");
            return true;
        } catch (SQLException ex) {
            log.warn("Legacy SQLite VACUUM failed for {}: {}", source, ex.getMessage());
            return false;
        }
    }

    private OffsetDateTime readOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime dateTime) {
            return dateTime.withOffsetSameInstant(ZoneOffset.UTC);
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atOffset(ZoneOffset.UTC);
        }
        OffsetDateTime parsed = DATE_TIME_CONVERTER.convertToEntityAttribute(String.valueOf(value));
        return parsed == null ? null : parsed.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value instanceof Number number ? number.intValue() : null;
    }

    record HistoryRow(String monitorKind,
                      Long monitorId,
                      String checkKind,
                      String status,
                      String summary,
                      String detailsExcerpt,
                      Integer httpStatus,
                      Long durationMs,
                      OffsetDateTime createdAt) {
        boolean isValid() {
            return StringUtils.hasText(monitorKind)
                && monitorId != null
                && StringUtils.hasText(checkKind)
                && createdAt != null;
        }

        HistoryFingerprint fingerprint() {
            return new HistoryFingerprint(
                monitorKind,
                monitorId,
                checkKind,
                status,
                summary,
                detailsExcerpt,
                httpStatus,
                durationMs,
                createdAt == null ? null : createdAt.toInstant().truncatedTo(ChronoUnit.MICROS)
            );
        }
    }

    record HistoryFingerprint(String monitorKind,
                              Long monitorId,
                              String checkKind,
                              String status,
                              String summary,
                              String detailsExcerpt,
                              Integer httpStatus,
                              Long durationMs,
                              Instant createdAt) {
        HistoryFingerprint withCreatedAt(Instant value) {
            return new HistoryFingerprint(
                monitorKind,
                monitorId,
                checkKind,
                status,
                summary,
                detailsExcerpt,
                httpStatus,
                durationMs,
                value
            );
        }
    }

    record CompactionResult(int sourceRows,
                            int copiedFresh,
                            int alreadyPresentFresh,
                            int expiredDiscarded,
                            int deletedSource,
                            boolean vacuumed,
                            boolean compacted) {
        static CompactionResult skipped() {
            return new CompactionResult(0, 0, 0, 0, 0, false, false);
        }
    }
}