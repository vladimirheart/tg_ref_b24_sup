package com.example.panel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LegacyMonitoringHistoryCompactionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void compactionCopiesMissingFreshRowsThenDeletesAndVacuumsLegacySource() throws Exception {
        Path source = tempDir.resolve("bot_database.db");
        OffsetDateTime now = OffsetDateTime.of(2026, 8, 24, 10, 52, 0, 123_456_800, ZoneOffset.UTC);
        OffsetDateTime existingFresh = now.minusDays(2);
        OffsetDateTime missingFresh = now.minusDays(1);
        OffsetDateTime expired = now.minusDays(45);

        try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + source)) {
            createSqliteHistoryTable(sqlite);
            insertSqliteRow(sqlite, "rms", 1L, "network", "up", "existing", existingFresh.toString());
            insertSqliteRow(sqlite, "rms", 2L, "license", "warning", "missing", missingFresh.toString());
            insertSqliteRow(sqlite, "rms", 3L, "license", "ok", "expired", expired.toString());
        }

        try (Connection target = DriverManager.getConnection(
            "jdbc:h2:mem:legacyMonitoringCompaction;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        )) {
            createTargetHistoryTable(target);
            insertTargetRow(target, "rms", 1L, "network", "up", "existing", existingFresh);

            LegacyMonitoringHistoryCompactionService service =
                new LegacyMonitoringHistoryCompactionService(null, null, null, null, null, null);
            LegacyMonitoringHistoryCompactionService.CompactionResult result =
                service.compactSource(target, source, now.minusDays(30));

            assertTrue(result.compacted());
            assertEquals(3, result.sourceRows());
            assertEquals(1, result.copiedFresh());
            assertEquals(1, result.alreadyPresentFresh());
            assertEquals(1, result.expiredDiscarded());
            assertEquals(3, result.deletedSource());
            assertTrue(result.vacuumed());
            assertEquals(2L, count(target, "monitoring_check_history"));
        }

        try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + source)) {
            assertEquals(0L, count(sqlite, "monitoring_check_history"));
        }
    }

    @Test
    void compactionRefusesDeleteWhenCurrentWindowRowCannotBeVerified() throws Exception {
        Path source = tempDir.resolve("monitoring.db");
        try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + source)) {
            createSqliteHistoryTable(sqlite);
            insertSqliteRow(sqlite, "rms", 1L, "network", "up", "bad timestamp", "not-a-date");
        }

        try (Connection target = DriverManager.getConnection(
            "jdbc:h2:mem:legacyMonitoringCompactionInvalid;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        )) {
            createTargetHistoryTable(target);
            LegacyMonitoringHistoryCompactionService service =
                new LegacyMonitoringHistoryCompactionService(null, null, null, null, null, null);

            LegacyMonitoringHistoryCompactionService.CompactionResult result =
                service.compactSource(target, source, OffsetDateTime.now(ZoneOffset.UTC).minusDays(30));

            assertFalse(result.compacted());
            assertEquals(0, result.deletedSource());
            assertEquals(0L, count(target, "monitoring_check_history"));
        }

        try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + source)) {
            assertEquals(1L, count(sqlite, "monitoring_check_history"));
        }
    }

    private void createSqliteHistoryTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE monitoring_check_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    monitor_kind TEXT NOT NULL,
                    monitor_id INTEGER NOT NULL,
                    check_kind TEXT NOT NULL,
                    status TEXT,
                    summary TEXT,
                    details_excerpt TEXT,
                    http_status INTEGER,
                    duration_ms INTEGER,
                    created_at TEXT NOT NULL
                )
                """);
        }
    }

    private void createTargetHistoryTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE monitoring_check_history (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    monitor_kind VARCHAR NOT NULL,
                    monitor_id BIGINT NOT NULL,
                    check_kind VARCHAR NOT NULL,
                    status VARCHAR,
                    summary VARCHAR,
                    details_excerpt VARCHAR,
                    http_status INTEGER,
                    duration_ms BIGINT,
                    created_at TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        }
    }

    private void insertSqliteRow(Connection connection,
                                 String monitorKind,
                                 long monitorId,
                                 String checkKind,
                                 String status,
                                 String summary,
                                 String createdAt) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO monitoring_check_history(
                monitor_kind, monitor_id, check_kind, status, summary, details_excerpt,
                http_status, duration_ms, created_at
            ) VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, ?)
            """)) {
            statement.setString(1, monitorKind);
            statement.setLong(2, monitorId);
            statement.setString(3, checkKind);
            statement.setString(4, status);
            statement.setString(5, summary);
            statement.setString(6, createdAt);
            statement.executeUpdate();
        }
    }

    private void insertTargetRow(Connection connection,
                                 String monitorKind,
                                 long monitorId,
                                 String checkKind,
                                 String status,
                                 String summary,
                                 OffsetDateTime createdAt) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO monitoring_check_history(
                monitor_kind, monitor_id, check_kind, status, summary, details_excerpt,
                http_status, duration_ms, created_at
            ) VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, ?)
            """)) {
            statement.setString(1, monitorKind);
            statement.setLong(2, monitorId);
            statement.setString(3, checkKind);
            statement.setString(4, status);
            statement.setString(5, summary);
            statement.setObject(6, createdAt);
            statement.executeUpdate();
        }
    }

    private long count(Connection connection, String table) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}