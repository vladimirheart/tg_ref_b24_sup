package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogPreviousHistoryPage;
import com.example.panel.storage.AttachmentService;
import com.example.panel.support.PanelTimestampSqlSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DialogConversationReadServiceTest {

    private JdbcTemplate jdbcTemplate;
    private DialogConversationReadService service;
    private AttachmentService attachments;

    @BeforeEach
    void setUp() {
        jdbcTemplate = PostgresqlJdbcTestSupport.freshJdbcTemplate("dialog_conversation");
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = alwaysUniqueTicketGuard();
        attachments = mock(AttachmentService.class);
        service = new DialogConversationReadService(
                jdbcTemplate,
                attachments,
                new PanelTimestampSqlSupport(),
                legacyTicketIdJdbcGuard
        );
        createSchema();
    }

    @Test
    void doesNotQueryAmbiguousLegacyTicketId() {
        JdbcTemplate guardedJdbcTemplate = mock(JdbcTemplate.class);
        DialogConversationReadService guardedService = new DialogConversationReadService(
                guardedJdbcTemplate,
                mock(AttachmentService.class),
                new PanelTimestampSqlSupport(),
                mock(LegacyTicketIdJdbcGuard.class)
        );

        assertThat(guardedService.loadHistory("T-AMB", null)).isEmpty();
        assertThat(guardedService.loadPreviousDialogHistory("T-AMB", 0)).isEmpty();
        assertThat(guardedService.loadTicketCategories("T-AMB")).isEmpty();

        verifyNoInteractions(guardedJdbcTemplate);
    }

    @Test
    void loadHistoryBuildsReplyPreviewAndAttachmentUrl() {
        when(attachments.hasTicketAttachmentOwnerByLegacyReference("T-10", "photo.png")).thenReturn(true);
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
        when(attachments.hasTicketAttachmentOwnerByLegacyReference("T-11", "attachments/T-11/client photo.jpg")).thenReturn(true);
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
        when(attachments.hasTicketAttachmentOwnerByLegacyReference("T-12", storageKey)).thenReturn(true);
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
    void loadHistoryScopesColumnMetadataToCurrentSchema() {
        String shadowSchema = "dialog_conversation_shadow_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbcTemplate.execute("CREATE SCHEMA " + shadowSchema);
        jdbcTemplate.execute("CREATE TABLE " + shadowSchema + ".chat_history (file_name TEXT)");
        jdbcTemplate.update("""
                INSERT INTO chat_history(
                    ticket_id, sender, message, timestamp, message_type, attachment,
                    tg_message_id, reply_to_tg_id, channel_id, original_message, edited_at, deleted_at, forwarded_from
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "T-SCHEMA", "client", "Schema scoped metadata", "2026-09-30T20:00:00Z", "text", null,
                501L, null, 5L, "Schema scoped metadata", null, null, null
        );

        List<ChatMessageDto> history = service.loadHistory("T-SCHEMA", 5L);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).message()).isEqualTo("Schema scoped metadata");
    }

    @Test
    void loadTicketCategoriesReturnsSortedValues() {
        jdbcTemplate.update("INSERT INTO ticket_categories(ticket_id, category) VALUES (?, ?)", "T-55", "billing");
        jdbcTemplate.update("INSERT INTO ticket_categories(ticket_id, category) VALUES (?, ?)", "T-55", "delivery");

        List<String> categories = service.loadTicketCategories("T-55");

        assertThat(categories).containsExactly("billing", "delivery");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void loadHistoryFallsBackToLegacyQueryWhenAttachmentMetadataReadFails(boolean ownedReference) throws java.io.IOException {
        JdbcTemplate failingJdbcTemplate = mock(JdbcTemplate.class);
        AttachmentService fallbackAttachments = mock(AttachmentService.class);
        when(fallbackAttachments.hasTicketAttachmentOwnerByLegacyReference("T-77", "attachments/T-77/image one.png"))
                .thenReturn(ownedReference);
        DialogConversationReadService fallbackService = new DialogConversationReadService(
                failingJdbcTemplate,
                fallbackAttachments,
                new PanelTimestampSqlSupport(),
                alwaysUniqueTicketGuard()
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
        if (ownedReference) {
            assertThat(history.get(0).attachment())
                    .isEqualTo("/api/attachments/tickets/by-path?path=attachments/T-77/image%20one.png");
        } else {
            assertThat(history.get(0).attachment()).isNull();
            assertThat(history.get(0).attachmentName()).isNull();
            assertThat(history.get(0).attachmentSize()).isNull();
            verify(fallbackAttachments, org.mockito.Mockito.never())
                    .describeTicketAttachmentByLegacyReference(anyString(), anyString());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-root", "duplicate-root", "wrong-owner"})
    void previousHistoryDoesNotExposeInvalidNeighbourFields(String condition) {
        seedTicket("T-CURRENT", 77L, "2026-04-30T10:00:00Z");
        seedMessage("T-CURRENT", 77L, "2026-04-30T10:00:00Z");
        if (!"missing-root".equals(condition)) {
            seedTicket("T-PRIVATE", "wrong-owner".equals(condition) ? 88L : 77L, "2026-04-29T10:00:00Z");
        }
        if ("duplicate-root".equals(condition)) {
            seedTicket("T-PRIVATE", 88L, "2026-04-29T10:00:00Z");
        }
        seedMessage("T-PRIVATE", 77L, "2026-04-29T10:00:00Z");

        assertThat(guardedService(mock(AttachmentService.class)).loadPreviousDialogHistory("T-CURRENT", 0)).isEmpty();
    }

    @Test
    void previousHistoryUsesCanonicalCurrentOwnerInsteadOfCorruptRootMessage() {
        seedTicket("T-CURRENT", 77L, "2026-04-30T10:00:00Z");
        seedMessage("T-CURRENT", 88L, "2026-04-30T10:00:00Z");
        seedTicket("T-FOREIGN", 88L, "2026-04-29T10:00:00Z");
        seedMessage("T-FOREIGN", 88L, "2026-04-29T10:00:00Z");
        seedTicket("T-VALID", 77L, "2026-04-28T10:00:00Z");
        seedMessage("T-VALID", 77L, "2026-04-28T10:00:00Z");

        DialogPreviousHistoryPage page = guardedService(mock(AttachmentService.class))
            .loadPreviousDialogHistory("T-CURRENT", 0).orElseThrow();

        assertThat(page.batch().ticketId()).isEqualTo("T-VALID");
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void previousHistoryFiltersUnsafeRowsBeforePaginationAndPreservesExactChildId() {
        seedTicket(" T-CURRENT ", 77L, "2026-04-30T10:00:00Z");
        seedMessage(" T-CURRENT ", 77L, "2026-04-30T10:00:00Z");
        seedTicket("T-CURRENT", 88L, "2026-04-30T10:00:00Z");
        seedMessage("T-CURRENT", 88L, "2026-04-30T10:00:00Z");
        seedMessage("T-ORPHAN", 77L, "2026-04-29T10:00:00Z");
        seedTicket("T-DUP", 77L, "2026-04-28T10:00:00Z");
        seedTicket("T-DUP", 88L, "2026-04-28T10:00:00Z");
        seedMessage("T-DUP", 77L, "2026-04-28T10:00:00Z");
        seedTicket("T-MISMATCH", 88L, "2026-04-27T10:00:00Z");
        seedMessage("T-MISMATCH", 77L, "2026-04-27T10:00:00Z");
        seedTicket(" T-OLD ", 77L, "2026-04-26T10:00:00Z");
        seedMessage(" T-OLD ", 77L, "2026-04-26T10:00:00Z");
        seedTicket("T-OLD", 88L, "2026-04-26T10:00:00Z");
        seedMessage("T-OLD", 88L, "2026-04-26T10:00:00Z");
        seedTicket("T-OLDER", 77L, "2026-04-25T10:00:00Z");
        seedMessage("T-OLDER", 77L, "2026-04-25T10:00:00Z");
        jdbcTemplate.update("""
            INSERT INTO chat_history(ticket_id, sender, message, timestamp, message_type) VALUES
                (' T-OLD ', 'client', 'exact history', '2026-04-26T10:00:00Z', 'text'),
                ('T-OLD', 'client', 'foreign history', '2026-04-26T10:00:00Z', 'text')
            """);
        DialogConversationReadService guarded = guardedService(mock(AttachmentService.class));

        DialogPreviousHistoryPage first = guarded.loadPreviousDialogHistory(" T-CURRENT ", 0).orElseThrow();
        assertThat(first.batch().ticketId()).isEqualTo(" T-OLD ");
        assertThat(first.batch().messages()).singleElement().extracting(ChatMessageDto::message).isEqualTo("exact history");
        assertThat(first.hasMore()).isTrue();
        assertThat(first.nextOffset()).isEqualTo(1);
        DialogPreviousHistoryPage second = guarded.loadPreviousDialogHistory(" T-CURRENT ", 1).orElseThrow();
        assertThat(second.batch().ticketId()).isEqualTo("T-OLDER");
        assertThat(second.hasMore()).isFalse();
        assertThat(second.nextOffset()).isNull();
    }

    @Test
    void historyAttachmentUrlAndDescriptorUseTheSameExactValidatedTicketId() throws java.io.IOException {
        seedTicket(" T-MEDIA ", 77L, "2026-04-30T10:00:00Z");
        jdbcTemplate.update("""
            INSERT INTO chat_history(ticket_id, sender, message, timestamp, message_type, attachment) VALUES
                (' T-MEDIA ', 'operator', '', '2026-04-30T10:00:00Z', 'image', 'photo.png')
            """);
        AttachmentService attachments = mock(AttachmentService.class);
        when(attachments.hasTicketAttachmentOwnerByLegacyReference(" T-MEDIA ", "photo.png")).thenReturn(true);

        List<ChatMessageDto> history = guardedService(attachments).loadHistory(" T-MEDIA ", null);

        assertThat(history).singleElement().extracting(ChatMessageDto::attachment)
            .isEqualTo("/api/attachments/tickets/%20T-MEDIA%20/photo.png");
        verify(attachments).describeTicketAttachmentByLegacyReference(" T-MEDIA ", "photo.png");
    }

    private DialogConversationReadService guardedService(AttachmentService attachments) {
        return new DialogConversationReadService(jdbcTemplate, attachments,
            new PanelTimestampSqlSupport(), new LegacyTicketIdJdbcGuard(jdbcTemplate));
    }

    private void seedTicket(String ticketId, Long userId, String createdAt) {
        jdbcTemplate.update("INSERT INTO tickets(ticket_id, user_id, status, created_at) VALUES (?, ?, 'resolved', ?)",
            ticketId, userId, java.time.OffsetDateTime.parse(createdAt));
    }

    private void seedMessage(String ticketId, Long userId, String createdAt) {
        jdbcTemplate.update("INSERT INTO messages(ticket_id, user_id, problem, created_at) VALUES (?, ?, ?, ?)",
            ticketId, userId, "problem " + ticketId, java.time.OffsetDateTime.parse(createdAt));
    }

    private LegacyTicketIdJdbcGuard alwaysUniqueTicketGuard() {
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = mock(LegacyTicketIdJdbcGuard.class);
        when(legacyTicketIdJdbcGuard.hasUniqueTicket(anyString())).thenReturn(true);
        return legacyTicketIdJdbcGuard;
    }

    private void createSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE chat_history (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    ticket_id TEXT,
                    sender TEXT,
                    message TEXT,
                    timestamp TIMESTAMPTZ,
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
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    group_msg_id INTEGER,
                    ticket_id TEXT,
                    user_id INTEGER,
                    created_at TIMESTAMPTZ,
                    problem TEXT,
                    channel_id INTEGER
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE tickets (
                    ticket_id TEXT,
                    user_id BIGINT NOT NULL DEFAULT 77,
                    status TEXT,
                    created_at TIMESTAMPTZ,
                    PRIMARY KEY(user_id, ticket_id)
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
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    ticket_id TEXT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE ticket_categories (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    ticket_id TEXT,
                    category TEXT
                )
                """);
    }
}
