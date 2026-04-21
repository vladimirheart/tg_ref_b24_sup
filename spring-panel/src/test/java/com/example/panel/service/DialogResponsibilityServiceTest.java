package com.example.panel.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DialogResponsibilityServiceTest {

    private JdbcTemplate jdbcTemplate;
    private DialogResponsibilityService service;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = Files.createTempFile("dialog-responsibility-", ".db");
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + dbFile.toAbsolutePath());
        jdbcTemplate = new JdbcTemplate(dataSource);
        service = new DialogResponsibilityService(jdbcTemplate);
        createSchema();
    }

    @Test
    void assignsAndMarksDialogAsRead() {
        jdbcTemplate.update(
                "INSERT INTO chat_history(ticket_id, sender, timestamp) VALUES (?, ?, ?)",
                "T-100", "client", "2026-04-21T10:15:00Z"
        );

        service.markDialogAsRead("T-100", "operator");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT responsible, assigned_by, last_read_at FROM ticket_responsibles WHERE ticket_id = ?",
                "T-100"
        );
        assertThat(row.get("responsible")).isEqualTo("operator");
        assertThat(row.get("assigned_by")).isEqualTo("operator");
        assertThat(row.get("last_read_at")).isEqualTo("2026-04-21T10:15:00Z");
    }

    @Test
    void redirectsExistingResponsible() {
        jdbcTemplate.update(
                "INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at) VALUES (?, ?, ?, ?)",
                "T-200", "old-operator", "lead", "2026-04-20T09:00:00Z"
        );

        service.assignResponsibleIfMissingOrRedirected("T-200", "new-operator", "lead");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT responsible, assigned_by, last_read_at FROM ticket_responsibles WHERE ticket_id = ?",
                "T-200"
        );
        assertThat(row.get("responsible")).isEqualTo("new-operator");
        assertThat(row.get("assigned_by")).isEqualTo("lead");
        assertThat(row.get("last_read_at")).isEqualTo("2026-04-20T09:00:00Z");
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE ticket_responsibles (
                    ticket_id TEXT PRIMARY KEY,
                    responsible TEXT,
                    assigned_by TEXT,
                    last_read_at TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE chat_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ticket_id TEXT,
                    sender TEXT,
                    timestamp TEXT
                )
                """);
    }
}
