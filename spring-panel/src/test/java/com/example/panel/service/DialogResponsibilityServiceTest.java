package com.example.panel.service;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DialogResponsibilityServiceTest {

    private JdbcTemplate jdbcTemplate;
    private DialogResponsibilityService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = PostgresqlJdbcTestSupport.freshJdbcTemplate("dialog_responsibility");
        service = new DialogResponsibilityService(jdbcTemplate, new LegacyTicketIdJdbcGuard(jdbcTemplate));
        createSchema();
    }

    @Test
    void assignResponsibleIfMissingCreatesOwner() {
        String responsible = service.assignResponsibleIfMissing("T-100", "operator");

        assertThat(responsible).isEqualTo("operator");
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT responsible, assigned_by, to_char(last_read_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') || 'T' || to_char(last_read_at AT TIME ZONE 'UTC', 'HH24:MI:SS') || 'Z' AS last_read_at FROM ticket_responsibles WHERE ticket_id = ?",
                "T-100"
        );
        assertThat(row.get("responsible")).isEqualTo("operator");
        assertThat(row.get("assigned_by")).isEqualTo("operator");
        assertThat(row.get("last_read_at")).isNull();
    }

    @Test
    void assignResponsibleIfMissingSkipsAiAgent() {
        String responsible = service.assignResponsibleIfMissing("T-100", "ai_agent");

        assertThat(responsible).isNull();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_responsibles WHERE ticket_id = ?",
                Integer.class,
                "T-100"
        );
        assertThat(count).isZero();
    }

    @Test
    void markDialogAsReadDoesNotAutoAssignUnassignedDialog() {
        jdbcTemplate.update(
                "INSERT INTO chat_history(ticket_id, sender, timestamp) VALUES (?, ?, ?)",
                "T-100", "client", "2026-04-21T10:15:00Z"
        );

        service.markDialogAsRead("T-100", "operator");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_responsibles WHERE ticket_id = ?",
                Integer.class,
                "T-100"
        );
        assertThat(count).isZero();
    }

    @Test
    void markDialogAsReadUpdatesExistingResponsibleOnly() {
        jdbcTemplate.update(
                "INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by) VALUES (?, ?, ?)",
                "T-101", "operator", "lead"
        );
        jdbcTemplate.update(
                "INSERT INTO chat_history(ticket_id, sender, timestamp) VALUES (?, ?, ?)",
                "T-101", "client", "2026-04-21T10:15:00Z"
        );

        service.markDialogAsRead("T-101", "operator");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT responsible, assigned_by, to_char(last_read_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') || 'T' || to_char(last_read_at AT TIME ZONE 'UTC', 'HH24:MI:SS') || 'Z' AS last_read_at FROM ticket_responsibles WHERE ticket_id = ?",
                "T-101"
        );
        assertThat(row.get("responsible")).isEqualTo("operator");
        assertThat(row.get("assigned_by")).isEqualTo("lead");
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
                "SELECT responsible, assigned_by, to_char(last_read_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') || 'T' || to_char(last_read_at AT TIME ZONE 'UTC', 'HH24:MI:SS') || 'Z' AS last_read_at FROM ticket_responsibles WHERE ticket_id = ?",
                "T-200"
        );
        assertThat(row.get("responsible")).isEqualTo("new-operator");
        assertThat(row.get("assigned_by")).isEqualTo("lead");
        assertThat(row.get("last_read_at")).isEqualTo("2026-04-20T09:00:00Z");
    }

    @Test
    void doesNotChangeResponsibleForAmbiguousLegacyTicketId() {
        jdbcTemplate.update("INSERT INTO tickets(user_id, ticket_id) VALUES (?, ?), (?, ?)",
                41L, "T-AMB", 42L, "T-AMB");
        jdbcTemplate.update("INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by) VALUES (?, ?, ?)",
                "T-AMB", "old-operator", "lead");

        service.assignResponsibleIfMissingOrRedirected("T-AMB", "new-operator", "lead");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT responsible FROM ticket_responsibles WHERE ticket_id = ?",
                String.class,
                "T-AMB"
        )).isEqualTo("old-operator");
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE tickets (
                    user_id BIGINT,
                    ticket_id TEXT,
                    PRIMARY KEY (user_id, ticket_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE ticket_responsibles (
                    ticket_id TEXT PRIMARY KEY,
                    responsible TEXT,
                    assigned_by TEXT,
                    last_read_at TIMESTAMPTZ
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE chat_history (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    ticket_id TEXT,
                    sender TEXT,
                    timestamp TIMESTAMPTZ
                )
                """);
        jdbcTemplate.update("INSERT INTO tickets(user_id, ticket_id) VALUES (?, ?), (?, ?), (?, ?)",
                1L, "T-100", 2L, "T-101", 3L, "T-200");
    }
}
