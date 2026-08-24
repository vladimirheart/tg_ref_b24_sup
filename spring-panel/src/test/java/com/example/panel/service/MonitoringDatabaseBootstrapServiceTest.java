package com.example.panel.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.panel.config.MonitoringSqliteDataSourceProperties;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.config.SqliteDataSourceProperties;
import com.example.panel.repository.IikoApiMonitorRepository;
import com.example.panel.repository.RmsLicenseMonitorRepository;
import com.example.panel.repository.SslCertificateMonitorRepository;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MonitoringDatabaseBootstrapServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void sqliteBootstrapMigratesRecentHistoryDeletesLegacyCopyAndCreatesRetentionIndex() {
        Path primaryPath = tempDir.resolve("bot_database.db");
        Path monitoringPath = tempDir.resolve("monitoring.db");
        JdbcTemplate primary = sqliteJdbc(primaryPath);
        JdbcTemplate monitoring = sqliteJdbc(monitoringPath);

        primary.execute("""
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
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        primary.update(
            "INSERT INTO monitoring_check_history(monitor_kind, monitor_id, check_kind, status, summary, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            "rms", 1L, "network", "up", "fresh", now.minusDays(2).toString()
        );
        primary.update(
            "INSERT INTO monitoring_check_history(monitor_kind, monitor_id, check_kind, status, summary, created_at) VALUES (?, ?, ?, ?, ?, ?)",
            "rms", 2L, "license", "ok", "expired", now.minusDays(45).toString()
        );

        SqliteDataSourceProperties primaryProperties = new SqliteDataSourceProperties();
        primaryProperties.setPath(primaryPath.toString());
        MonitoringSqliteDataSourceProperties monitoringProperties = new MonitoringSqliteDataSourceProperties();
        monitoringProperties.setPath(monitoringPath.toString());
        PanelDatabaseRuntimeMode runtimeMode = mock(PanelDatabaseRuntimeMode.class);
        when(runtimeMode.isSqliteMode()).thenReturn(true);

        MonitoringDatabaseBootstrapService service = new MonitoringDatabaseBootstrapService(
            primary,
            monitoring,
            mock(IikoApiMonitorRepository.class),
            mock(SslCertificateMonitorRepository.class),
            mock(RmsLicenseMonitorRepository.class),
            mock(MonitoringCredentialsCryptoService.class),
            primaryProperties,
            monitoringProperties,
            runtimeMode
        );

        service.run(null);

        assertEquals(0L, primary.queryForObject("SELECT COUNT(*) FROM monitoring_check_history", Long.class));
        assertEquals(1L, monitoring.queryForObject("SELECT COUNT(*) FROM monitoring_check_history", Long.class));
        assertEquals("fresh", monitoring.queryForObject("SELECT summary FROM monitoring_check_history", String.class));
        List<Map<String, Object>> indexes = monitoring.queryForList("PRAGMA index_list(monitoring_check_history)");
        assertTrue(indexes.stream().anyMatch(index ->
            "idx_monitoring_check_history_created_at".equals(String.valueOf(index.get("name")))
        ));
    }

    private JdbcTemplate sqliteJdbc(Path path) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + path.toAbsolutePath().normalize());
        return new JdbcTemplate(dataSource);
    }
}