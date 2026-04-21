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

class DialogClientContextReadServiceTest {

    private JdbcTemplate jdbcTemplate;
    private DialogClientContextReadService service;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = Files.createTempFile("dialog-client-context-", ".db");
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + dbFile.toAbsolutePath());
        jdbcTemplate = new JdbcTemplate(dataSource);
        service = new DialogClientContextReadService(jdbcTemplate);
        createSchema();
    }

    @Test
    void loadsClientHistoryAndProfileEnrichmentFromMessagesAndTickets() {
        jdbcTemplate.update(
                "INSERT INTO tickets(ticket_id, status, created_at, resolved_at) VALUES (?, ?, ?, ?)",
                "T-1", "pending", "2026-04-20T10:00:00Z", null
        );
        jdbcTemplate.update(
                "INSERT INTO tickets(ticket_id, status, created_at, resolved_at) VALUES (?, ?, ?, ?)",
                "T-2", "resolved", "2026-04-15T10:00:00Z", "2099-04-16T10:00:00Z"
        );
        jdbcTemplate.update(
                "INSERT INTO tickets(ticket_id, status, created_at, resolved_at) VALUES (?, ?, ?, ?)",
                "T-3", "closed", "2026-03-10T10:00:00Z", "2026-03-11T10:00:00Z"
        );
        jdbcTemplate.update(
                "INSERT INTO messages(ticket_id, user_id, created_at, problem) VALUES (?, ?, ?, ?)",
                "T-1", 55L, "2026-04-20T10:00:00Z", "Текущий диалог"
        );
        jdbcTemplate.update(
                "INSERT INTO messages(ticket_id, user_id, created_at, problem) VALUES (?, ?, ?, ?)",
                "T-2", 55L, "2026-04-15T10:00:00Z", "Старый resolved"
        );
        jdbcTemplate.update(
                "INSERT INTO messages(ticket_id, user_id, created_at, problem) VALUES (?, ?, ?, ?)",
                "T-3", 55L, "2026-03-10T10:00:00Z", "Старый closed"
        );

        List<Map<String, Object>> history = service.loadClientDialogHistory(55L, "T-1", 5);
        Map<String, Object> enrichment = service.loadClientProfileEnrichment(55L);

        assertThat(history).hasSize(2);
        assertThat(history).extracting(item -> item.get("ticket_id")).containsExactly("T-2", "T-3");
        assertThat(enrichment)
                .containsEntry("total_dialogs", 3)
                .containsEntry("open_dialogs", 1)
                .containsEntry("resolved_30d", 1)
                .containsEntry("first_seen_at", "2026-03-10T10:00:00Z")
                .containsEntry("last_ticket_activity_at", "2099-04-16T10:00:00Z");
    }

    @Test
    void loadDialogProfileMatchCandidatesBuildsReviewPayloadFromSettingsParameters() {
        jdbcTemplate.update(
                "INSERT INTO settings_parameters(id, param_type, value, state, is_deleted) VALUES (?, ?, ?, ?, ?)",
                1L, "business", "Блинбери Express", "active", 0
        );
        jdbcTemplate.update(
                "INSERT INTO settings_parameters(id, param_type, value, state, is_deleted) VALUES (?, ?, ?, ?, ?)",
                2L, "city", "Москва", "active", 0
        );

        Map<String, Object> payload = service.loadDialogProfileMatchCandidates(
                Map.of("business", "Блинбери", "city", "Москва"),
                5
        );

        assertThat(payload)
                .containsEntry("enabled", true)
                .containsEntry("source", "settings_parameters")
                .containsEntry("summary", "review_required")
                .containsEntry("has_any_candidates", true);
        List<?> fields = (List<?>) payload.get("fields");
        assertThat(fields).hasSize(2);
        Map<?, ?> businessField = fields.stream()
                .map(Map.class::cast)
                .filter(item -> "business".equals(item.get("field")))
                .findFirst()
                .orElseThrow();
        Map<?, ?> cityField = fields.stream()
                .map(Map.class::cast)
                .filter(item -> "city".equals(item.get("field")))
                .findFirst()
                .orElseThrow();
        assertThat(businessField.get("needs_review")).isEqualTo(true);
        assertThat(((List<?>) businessField.get("candidates"))).hasSize(1);
        assertThat(cityField.get("needs_review")).isEqualTo(false);
        assertThat(((List<?>) cityField.get("candidates"))).hasSize(1);
    }

    @Test
    void loadRelatedEventsFormatsAuditRowsAndKeepsNewestFirst() {
        jdbcTemplate.update(
                "INSERT INTO chat_history(id, ticket_id, sender, timestamp, message_type, message) VALUES (?, ?, ?, ?, ?, ?)",
                1L, "T-55", "operator", "2026-04-21T08:00:00Z", "event", "Ответ отправлен"
        );
        jdbcTemplate.update(
                "INSERT INTO dialog_action_audit(id, ticket_id, actor, action, result, detail, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                5L, "T-55", "system", "resolve", "success", "auto_close", "2026-04-21T09:00:00Z"
        );

        List<Map<String, Object>> events = service.loadRelatedEvents("T-55", 10);

        assertThat(events).hasSize(2);
        assertThat(events.get(0))
                .containsEntry("type", "audit")
                .containsEntry("detail", "resolve: success (auto_close)");
        assertThat(events.get(1))
                .containsEntry("type", "event")
                .containsEntry("detail", "Ответ отправлен");
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE tickets (
                    ticket_id VARCHAR(120) PRIMARY KEY,
                    status VARCHAR(32),
                    created_at VARCHAR(64),
                    resolved_at VARCHAR(64)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ticket_id TEXT,
                    user_id INTEGER,
                    created_at TEXT,
                    problem TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE settings_parameters (
                    id INTEGER PRIMARY KEY,
                    param_type TEXT,
                    value TEXT,
                    state TEXT,
                    is_deleted INTEGER DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE chat_history (
                    id INTEGER PRIMARY KEY,
                    ticket_id TEXT,
                    sender TEXT,
                    timestamp TEXT,
                    message_type TEXT,
                    message TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE task_links (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ticket_id TEXT,
                    task_id INTEGER
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE tasks (
                    id INTEGER PRIMARY KEY,
                    assignee TEXT,
                    creator TEXT,
                    last_activity_at TEXT,
                    created_at TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE task_history (
                    id INTEGER PRIMARY KEY,
                    task_id INTEGER,
                    at TEXT,
                    text TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE dialog_action_audit (
                    id INTEGER PRIMARY KEY,
                    ticket_id TEXT,
                    actor TEXT,
                    action TEXT,
                    result TEXT,
                    detail TEXT,
                    created_at TEXT
                )
                """);
    }
}
