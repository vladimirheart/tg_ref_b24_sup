package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DialogLookupReadServiceTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcTemplate usersJdbcTemplate;
    private DialogLookupReadService service;

    @BeforeEach
    void setUp() throws Exception {
        Path panelDbFile = Files.createTempFile("dialog-lookup-panel-", ".db");
        Path usersDbFile = Files.createTempFile("dialog-lookup-users-", ".db");
        DataSource panelDataSource = new DriverManagerDataSource("jdbc:sqlite:" + panelDbFile.toAbsolutePath());
        DataSource usersDataSource = new DriverManagerDataSource("jdbc:sqlite:" + usersDbFile.toAbsolutePath());
        jdbcTemplate = new JdbcTemplate(panelDataSource);
        usersJdbcTemplate = new JdbcTemplate(usersDataSource);
        service = new DialogLookupReadService(jdbcTemplate, usersJdbcTemplate);
        createPanelSchema();
        createUsersSchema();
    }

    @Test
    void loadsSummaryAndEnrichesResponsibleProfile() {
        jdbcTemplate.update(
                "INSERT INTO channels(id, channel_name) VALUES (?, ?)",
                7L, "Telegram Support"
        );
        jdbcTemplate.update(
                "INSERT INTO tickets(ticket_id, status, resolved_by, resolved_at, user_id, channel_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                "T-100", "pending", null, null, 55L, 7L, "2026-04-21T09:00:00Z"
        );
        jdbcTemplate.update("""
                INSERT INTO messages(group_msg_id, ticket_id, user_id, username, client_name, business, channel_id, city, location_name, problem, created_at, created_date, created_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                501L, "T-100", 55L, "client55", "Клиент", "billing", 7L, "Москва", "Офис", "Проблема", "2026-04-21T09:00:00Z", "2026-04-21", "09:00:00"
        );
        jdbcTemplate.update(
                "INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by, last_read_at) VALUES (?, ?, ?, ?)",
                "T-100", "operator", "operator", "2026-04-21T08:59:00Z"
        );
        jdbcTemplate.update(
                "INSERT INTO chat_history(id, ticket_id, sender, timestamp, tg_message_id) VALUES (?, ?, ?, ?, ?)",
                1L, "T-100", "client", "2026-04-21T09:05:00Z", 10L
        );
        jdbcTemplate.update(
                "INSERT INTO feedbacks(id, ticket_id, rating, timestamp) VALUES (?, ?, ?, ?)",
                1L, "T-100", 5, "2026-04-21T09:06:00Z"
        );
        jdbcTemplate.update(
                "INSERT INTO ticket_categories(ticket_id, category) VALUES (?, ?)",
                "T-100", "billing"
        );
        usersJdbcTemplate.update(
                "INSERT INTO users(username, full_name, photo) VALUES (?, ?, ?)",
                "operator", "Ivan Operator", "ivan.png"
        );

        DialogSummary summary = service.loadSummary();
        List<DialogListItem> dialogs = service.loadDialogs("operator");
        Optional<DialogListItem> dialog = service.findDialog("T-100", "operator");

        assertThat(summary.totalTickets()).isEqualTo(1);
        assertThat(summary.pendingTickets()).isEqualTo(1);
        assertThat(summary.channelStats()).hasSize(1);
        assertThat(dialogs).hasSize(1);
        assertThat(dialogs.get(0).ticketId()).isEqualTo("T-100");
        assertThat(dialogs.get(0).rawResponsible()).isEqualTo("operator");
        assertThat(dialogs.get(0).rating()).isEqualTo(5);
        assertThat(dialog).isPresent();
        assertThat(dialog.orElseThrow().ticketId()).isEqualTo("T-100");
        assertThat(dialog.orElseThrow().categories()).isEqualTo("billing");
    }

    private void createPanelSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE tickets (
                    ticket_id TEXT PRIMARY KEY,
                    status TEXT,
                    resolved_by TEXT,
                    resolved_at TEXT,
                    user_id INTEGER,
                    channel_id INTEGER,
                    created_at TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    group_msg_id INTEGER,
                    ticket_id TEXT,
                    user_id INTEGER,
                    username TEXT,
                    client_name TEXT,
                    business TEXT,
                    channel_id INTEGER,
                    city TEXT,
                    location_name TEXT,
                    problem TEXT,
                    created_at TEXT,
                    created_date TEXT,
                    created_time TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE channels (
                    id INTEGER PRIMARY KEY,
                    channel_name TEXT
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
        jdbcTemplate.execute("""
                CREATE TABLE ticket_ai_agent_state (
                    ticket_id TEXT PRIMARY KEY,
                    is_processing INTEGER
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE client_statuses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER,
                    status TEXT,
                    updated_at TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE chat_history (
                    id INTEGER PRIMARY KEY,
                    ticket_id TEXT,
                    sender TEXT,
                    timestamp TEXT,
                    tg_message_id INTEGER
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE feedbacks (
                    id INTEGER PRIMARY KEY,
                    ticket_id TEXT,
                    rating INTEGER,
                    timestamp TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE ticket_categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ticket_id TEXT,
                    category TEXT
                )
                """);
    }

    private void createUsersSchema() {
        usersJdbcTemplate.execute("""
                CREATE TABLE users (
                    username TEXT PRIMARY KEY,
                    full_name TEXT,
                    photo TEXT
                )
                """);
    }
}
