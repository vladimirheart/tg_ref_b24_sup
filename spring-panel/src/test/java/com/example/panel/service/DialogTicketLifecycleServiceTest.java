package com.example.panel.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DialogTicketLifecycleServiceTest {

    private JdbcTemplate jdbcTemplate;
    private DialogTicketLifecycleService service;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = Files.createTempFile("dialog-ticket-lifecycle-", ".db");
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + dbFile.toAbsolutePath());
        jdbcTemplate = new JdbcTemplate(dataSource);
        service = new DialogTicketLifecycleService(jdbcTemplate, new DialogResponsibilityService(jdbcTemplate));
        createSchema();
    }

    @Test
    void resolvesTicketSetsCategoriesAndCreatesPendingFeedbackRequest() {
        jdbcTemplate.update("""
                INSERT INTO tickets(ticket_id, status, user_id, channel_id, closed_count, reopen_count)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                "T-100", "pending", 77L, 5L, 0, 0
        );

        DialogService.ResolveResult result = service.resolveTicket("T-100", "operator", List.of("billing", "billing", "payments"));

        assertThat(result.updated()).isTrue();
        assertThat(result.exists()).isTrue();
        assertThat(result.error()).isNull();
        Map<String, Object> ticket = jdbcTemplate.queryForMap(
                "SELECT status, resolved_by, closed_count FROM tickets WHERE ticket_id = ?",
                "T-100"
        );
        assertThat(ticket.get("status")).isEqualTo("resolved");
        assertThat(ticket.get("resolved_by")).isEqualTo("operator");
        assertThat(((Number) ticket.get("closed_count")).longValue()).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForList(
                "SELECT category FROM ticket_categories WHERE ticket_id = ? ORDER BY category",
                String.class,
                "T-100"
        )).containsExactly("billing", "payments");
        Map<String, Object> pendingFeedback = jdbcTemplate.queryForMap(
                "SELECT source FROM pending_feedback_requests WHERE ticket_id = ?",
                "T-100"
        );
        assertThat(pendingFeedback.get("source")).isEqualTo("operator_close");
    }

    @Test
    void reopensResolvedTicketAndAssignsResponsible() {
        jdbcTemplate.update("""
                INSERT INTO tickets(ticket_id, status, resolved_by, user_id, channel_id, closed_count, reopen_count)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                "T-200", "resolved", "Авто-система", 88L, 6L, 1, 0
        );

        DialogService.ResolveResult result = service.reopenTicket("T-200", "operator");

        assertThat(result.updated()).isTrue();
        Map<String, Object> ticket = jdbcTemplate.queryForMap(
                "SELECT status, resolved_at, resolved_by, reopen_count FROM tickets WHERE ticket_id = ?",
                "T-200"
        );
        assertThat(ticket.get("status")).isEqualTo("pending");
        assertThat(ticket.get("resolved_at")).isNull();
        assertThat(ticket.get("resolved_by")).isNull();
        assertThat(((Number) ticket.get("reopen_count")).longValue()).isEqualTo(1L);
        Map<String, Object> responsible = jdbcTemplate.queryForMap(
                "SELECT responsible, assigned_by FROM ticket_responsibles WHERE ticket_id = ?",
                "T-200"
        );
        assertThat(responsible.get("responsible")).isEqualTo("operator");
        assertThat(responsible.get("assigned_by")).isEqualTo("operator");
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE tickets (
                    ticket_id TEXT PRIMARY KEY,
                    status TEXT,
                    resolved_at TEXT,
                    resolved_by TEXT,
                    user_id INTEGER,
                    channel_id INTEGER,
                    closed_count INTEGER,
                    reopen_count INTEGER,
                    last_reopen_at TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE ticket_categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ticket_id TEXT,
                    category TEXT,
                    created_at TEXT,
                    updated_at TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE pending_feedback_requests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER,
                    channel_id INTEGER,
                    ticket_id TEXT,
                    source TEXT,
                    created_at TEXT,
                    expires_at TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE ticket_responsibles (
                    ticket_id TEXT PRIMARY KEY,
                    responsible TEXT,
                    assigned_by TEXT,
                    last_read_at TEXT
                )
                """);
    }
}
