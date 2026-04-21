package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogPreviousHistoryPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DialogConversationReadServiceTest {

    private JdbcTemplate jdbcTemplate;
    private DialogConversationReadService service;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = Files.createTempFile("dialog-conversation-", ".db");
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + dbFile.toAbsolutePath());
        jdbcTemplate = new JdbcTemplate(dataSource);
        service = new DialogConversationReadService(jdbcTemplate);
        createSchema();
    }

    @Test
    void loadHistoryBuildsReplyPreviewAndAttachmentUrl() {
        jdbcTemplate.update("""
                INSERT INTO chat_history(
                    ticket_id, sender, message, timestamp, message_type, attachment,
                    tg_message_id, reply_to_tg_id, channel_id, original_message, edited_at, deleted_at, forwarded_from
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "T-10", "client", "Первое сообщение", "2026-04-21T09:00:00Z", "text", null,
                10L, null, 5L, "Первое сообщение", null, null, null
        );
        jdbcTemplate.update("""
                INSERT INTO chat_history(
                    ticket_id, sender, message, timestamp, message_type, attachment,
                    tg_message_id, reply_to_tg_id, channel_id, original_message, edited_at, deleted_at, forwarded_from
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "T-10", "operator", "", "2026-04-21T09:01:00Z", "image", "photo.png",
                11L, 10L, 5L, "", "2026-04-21T09:02:00Z", null, "manager"
        );

        List<ChatMessageDto> history = service.loadHistory("T-10", 5L);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).message()).isEqualTo("Первое сообщение");
        assertThat(history.get(1).replyPreview()).isEqualTo("Первое сообщение");
        assertThat(history.get(1).attachment()).isEqualTo("/api/dialogs/T-10/attachments/photo.png");
        assertThat(history.get(1).editedAt()).isEqualTo("2026-04-21T09:02:00Z");
        assertThat(history.get(1).forwardedFrom()).isEqualTo("manager");
    }

    @Test
    void loadPreviousHistoryResolvesSourceLabelsAndNestedMessages() {
        jdbcTemplate.update("INSERT INTO tickets(ticket_id, status, created_at) VALUES (?, ?, ?)", "T-CUR", "pending", "2026-04-21T10:00:00Z");
        jdbcTemplate.update("INSERT INTO tickets(ticket_id, status, created_at) VALUES (?, ?, ?)", "T-OLD", "resolved", "2026-04-20T08:00:00Z");
        jdbcTemplate.update("INSERT INTO channels(id, channel_name, platform) VALUES (?, ?, ?)", 7L, "Веб-канал", "telegram");
        jdbcTemplate.update("INSERT INTO web_form_sessions(ticket_id) VALUES (?)", "T-OLD");
        jdbcTemplate.update("""
                INSERT INTO messages(group_msg_id, ticket_id, user_id, created_at, problem, channel_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                200L, "T-CUR", 77L, "2026-04-21T10:00:00Z", "Текущий диалог", 7L
        );
        jdbcTemplate.update("""
                INSERT INTO messages(group_msg_id, ticket_id, user_id, created_at, problem, channel_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                150L, "T-OLD", 77L, "2026-04-20T08:00:00Z", "Старый диалог", 7L
        );
        jdbcTemplate.update("""
                INSERT INTO chat_history(ticket_id, sender, message, timestamp, message_type, tg_message_id, channel_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                "T-OLD", "client", "Историческое сообщение", "2026-04-20T08:05:00Z", "text", 1L, 7L
        );

        DialogPreviousHistoryPage page = service.loadPreviousDialogHistory("T-CUR", 0).orElseThrow();

        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextOffset()).isNull();
        assertThat(page.batch().ticketId()).isEqualTo("T-OLD");
        assertThat(page.batch().sourceKey()).isEqualTo("web_form");
        assertThat(page.batch().sourceLabel()).isEqualTo("Внешняя форма");
        assertThat(page.batch().messages()).hasSize(1);
        assertThat(page.batch().messages().get(0).message()).isEqualTo("Историческое сообщение");
    }

    @Test
    void loadTicketCategoriesReturnsSortedValues() {
        jdbcTemplate.update("INSERT INTO ticket_categories(ticket_id, category) VALUES (?, ?)", "T-55", "billing");
        jdbcTemplate.update("INSERT INTO ticket_categories(ticket_id, category) VALUES (?, ?)", "T-55", "delivery");

        List<String> categories = service.loadTicketCategories("T-55");

        assertThat(categories).containsExactly("billing", "delivery");
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE chat_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ticket_id TEXT,
                    sender TEXT,
                    message TEXT,
                    timestamp TEXT,
                    message_type TEXT,
                    attachment TEXT,
                    tg_message_id INTEGER,
                    reply_to_tg_id INTEGER,
                    channel_id INTEGER,
                    original_message TEXT,
                    edited_at TEXT,
                    deleted_at TEXT,
                    forwarded_from TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    group_msg_id INTEGER,
                    ticket_id TEXT,
                    user_id INTEGER,
                    created_at TEXT,
                    problem TEXT,
                    channel_id INTEGER
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE tickets (
                    ticket_id TEXT PRIMARY KEY,
                    status TEXT,
                    created_at TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE channels (
                    id INTEGER PRIMARY KEY,
                    channel_name TEXT,
                    platform TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE web_form_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ticket_id TEXT
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
}
