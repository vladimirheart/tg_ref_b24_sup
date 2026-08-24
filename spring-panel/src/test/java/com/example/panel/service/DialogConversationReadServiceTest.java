package com.example.panel.service;

import com.example.panel.config.DatabaseMode;
import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogPreviousHistoryPage;
import com.example.panel.storage.AttachmentService;
import com.example.panel.support.PanelTimestampSqlSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogConversationReadServiceTest {

    private JdbcTemplate jdbcTemplate;
    private DialogConversationReadService service;

    @BeforeEach
    void setUp() throws Exception {
        Path dbFile = Files.createTempFile("dialog-conversation-", ".db");
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + dbFile.toAbsolutePath());
        jdbcTemplate = new JdbcTemplate(dataSource);
        service = new DialogConversationReadService(
                jdbcTemplate,
                mock(AttachmentService.class),
                new PanelTimestampSqlSupport(DatabaseMode.SQLITE)
        );
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
        assertThat(history.get(1).attachment()).isEqualTo("/api/attachments/tickets/T-10/photo.png");
        assertThat(history.get(1).editedAt()).isEqualTo("2026-04-21T09:02:00Z");
        assertThat(history.get(1).forwardedFrom()).isEqualTo("manager");
    }

    @Test
    void loadHistoryBuildsAttachmentUrlForStoredPath() {
        jdbcTemplate.update("""
                INSERT INTO chat_history(
                    ticket_id, sender, message, timestamp, message_type, attachment,
                    tg_message_id, reply_to_tg_id, channel_id, original_message, edited_at, deleted_at, forwarded_from
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "T-11", "client", "", "2026-04-21T09:03:00Z", "photo", "attachments/T-11/client photo.jpg",
                12L, null, 5L, "", null, null, null
        );

        List<ChatMessageDto> history = service.loadHistory("T-11", 5L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).attachment())
                .isEqualTo("/api/attachments/tickets/by-path?path=attachments/T-11/client%20photo.jpg");
    }

    @Test
    void loadHistoryBuildsStorageKeyUrlForNestedRawAttachmentWithoutMetadata() {
        String storageKey = "3d543ab982d2f414aa9dc8b135805291/2026/08/24/f50aeb246b294a9d00991bd6.jpg";
        jdbcTemplate.update("""
                INSERT INTO chat_history(
                    ticket_id, sender, message, timestamp, message_type, attachment,
                    tg_message_id, reply_to_tg_id, channel_id, original_message, edited_at, deleted_at, forwarded_from
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "T-12", "client", "", "2026-08-24T17:29:50+03:00", "photo", storageKey,
                13L, null, 5L, "", null, null, null
        );

        List<ChatMessageDto> history = service.loadHistory("T-12", 5L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).attachment()).isEqualTo(
                "/api/attachments/tickets/by-storage-key?key="
                        + org.springframework.web.util.UriUtils.encodeQueryParam(
                                storageKey,
                                java.nio.charset.StandardCharsets.UTF_8
                        )
        );
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

    @Test
    void loadHistoryFallsBackToLegacyQueryWhenAttachmentMetadataReadFails() {
        JdbcTemplate failingJdbcTemplate = mock(JdbcTemplate.class);
        DialogConversationReadService fallbackService = new DialogConversationReadService(
                failingJdbcTemplate,
                mock(AttachmentService.class),
                new PanelTimestampSqlSupport(DatabaseMode.SQLITE)
        );

        when(failingJdbcTemplate.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Set<String>>>any())).thenReturn(
                Set.of("original_message", "edited_at", "deleted_at", "forwarded_from", "file_name"),
                Set.of("storage_key")
        );
        when(failingJdbcTemplate.queryForList(any(String.class), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("LEFT JOIN chat_attachment_metadata")) {
                throw new DataIntegrityViolationException("database disk image is malformed");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sender", "operator");
            row.put("message", "Фолбэк без metadata");
            row.put("timestamp", "2026-07-31T17:40:00+03:00");
            row.put("message_type", "image");
            row.put("attachment", "attachments/T-77/image one.png");
            row.put("tg_message_id", 77L);
            row.put("reply_to_tg_id", null);
            row.put("channel_id", 5L);
            row.put("original_message", "Фолбэк без metadata");
            row.put("edited_at", null);
            row.put("deleted_at", null);
            row.put("forwarded_from", null);
            row.put("file_name", null);
            row.put("attachment_storage_key", null);
            row.put("attachment_storage_provider", null);
            row.put("attachment_original_name", null);
            row.put("attachment_size", null);
            row.put("attachment_availability_status", null);
            row.put("attachment_legacy_ref", null);
            return List.of(row);
        });

        List<ChatMessageDto> history = fallbackService.loadHistory("T-77", 5L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).message()).isEqualTo("Фолбэк без metadata");
        assertThat(history.get(0).attachment())
                .isEqualTo("/api/attachments/tickets/by-path?path=attachments/T-77/image%20one.png");
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
