package com.example.panel.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MonitoringCheckHistoryRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void deleteOlderThanUsesPortableTimestampBindingForSqlite() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + tempDir.resolve("monitoring.db").toAbsolutePath().normalize());
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
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

        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30).withNano(0);
        OffsetDateTime actuallyExpiredButLexicallyLater = cutoff.minusHours(1)
            .withOffsetSameInstant(ZoneOffset.ofHours(2));
        OffsetDateTime actuallyFreshButLexicallyEarlier = cutoff.plusHours(1)
            .withOffsetSameInstant(ZoneOffset.ofHours(-2));
        jdbcTemplate.update(
            "INSERT INTO monitoring_check_history(monitor_kind, monitor_id, check_kind, created_at) VALUES (?, ?, ?, ?)",
            "rms", 1L, "network", actuallyExpiredButLexicallyLater.toString()
        );
        jdbcTemplate.update(
            "INSERT INTO monitoring_check_history(monitor_kind, monitor_id, check_kind, created_at) VALUES (?, ?, ?, ?)",
            "rms", 1L, "network", actuallyFreshButLexicallyEarlier.toString()
        );

        MonitoringCheckHistoryRepository repository = new MonitoringCheckHistoryRepository(jdbcTemplate);
        int deleted = repository.deleteOlderThan(cutoff);

        assertEquals(1, deleted);
        assertEquals(1, repository.findRecent("rms", 1L, 10).size());
    }
}