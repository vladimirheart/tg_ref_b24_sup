package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.storage.AttachmentService;
import com.example.panel.storage.AttachmentStorageKeyResolver;
import com.example.panel.storage.ObjectStorageProperties;
import com.example.panel.storage.TicketAttachmentOwnerJdbcGuard;
import com.example.panel.support.PanelTimestampSqlSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.web.util.UriUtils;

class DialogLegacyAttachmentOwnershipPostgresqlTest {
    private static final String FILE = "12345678-1234-1234-1234-123456789abc_report.pdf";
    @TempDir Path tempDir;
    private record Fixture(JdbcTemplate jdbc, DialogConversationReadService reader,
                           AttachmentObjectStorageService storage, Path root) { }

    private Fixture fixture() throws Exception {
        return fixture(false);
    }

    private Fixture fixture(boolean failedOwnerLookup) throws Exception {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("history_legacy_reference");
        jdbc.execute("CREATE TABLE tickets(is_general BOOLEAN NOT NULL DEFAULT FALSE,user_id BIGINT, ticket_id TEXT, PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("""
                CREATE TABLE chat_history(
                    id BIGINT PRIMARY KEY, ticket_id TEXT, sender TEXT DEFAULT 'client',
                    message TEXT DEFAULT 'own message', timestamp TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                    message_type TEXT DEFAULT 'document', attachment TEXT, file_name TEXT,
                    tg_message_id BIGINT, reply_to_tg_id BIGINT, channel_id BIGINT DEFAULT 2)
                """);
        jdbc.execute("""
                CREATE TABLE chat_attachment_metadata(
                    chat_history_id BIGINT UNIQUE REFERENCES chat_history(id), ticket_id TEXT, storage_key TEXT,
                    storage_provider TEXT DEFAULT 'local_fs', storage_class TEXT DEFAULT 'dialog_attachment',
                    normalization_status TEXT DEFAULT 'normalized', deleted_at TIMESTAMPTZ,
                    original_name TEXT, size BIGINT, availability_status TEXT DEFAULT 'available',
                    legacy_attachment_ref TEXT)
                """);
        Path root = tempDir.resolve("attachments");
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setMode("local_fs");
        var storage = spy(new AttachmentObjectStorageService(properties, root.toString(),
                tempDir.resolve("knowledge").toString(), tempDir.resolve("passport").toString(),
                tempDir.resolve("avatars").toString()));
        var tickets = new LegacyTicketIdJdbcGuard(jdbc);
        JdbcTemplate ownerJdbc = jdbc;
        if (failedOwnerLookup) {
            ownerJdbc = mock(JdbcTemplate.class);
            when(ownerJdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenThrow(new DataAccessResourceFailureException("owner lookup unavailable"));
        }
        var attachments = new AttachmentService(mock(PermissionService.class), storage, tickets,
                new TicketAttachmentOwnerJdbcGuard(ownerJdbc, tickets), root.toString(),
                tempDir.resolve("knowledge").toString());
        return new Fixture(jdbc, new DialogConversationReadService(jdbc, attachments,
                new PanelTimestampSqlSupport(), tickets), storage, root);
    }

    private void ticket(Fixture fixture, long user, String owner) {
        fixture.jdbc().update("INSERT INTO tickets(user_id,ticket_id) VALUES (?,?)", user, owner);
    }

    private void history(Fixture fixture, long id, String owner, String reference, long channel) {
        fixture.jdbc().update("""
                INSERT INTO chat_history(id,ticket_id,attachment,file_name,tg_message_id,channel_id)
                VALUES (?,?,?,?,?,?)
                """, id, owner, reference, reference == null ? null : "PRIVATE-HISTORY-NAME.pdf", id, channel);
    }

    private void anchor(Fixture fixture, String owner, String key) {
        history(fixture, 11, owner, null, 1);
        fixture.jdbc().update("""
                INSERT INTO chat_attachment_metadata(chat_history_id,ticket_id,storage_key) VALUES (11,?,?)
                """, owner, key);
    }

    private void write(Fixture fixture, String key) throws Exception {
        Path file = fixture.root().resolve(key);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {1, 2, 3});
        clearInvocations(fixture.storage());
    }

    private ChatMessageDto single(Fixture fixture, String owner) {
        var messages = fixture.reader().loadHistory(owner, 2L);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).message()).isEqualTo("own message");
        return messages.get(0);
    }

    private void redacted(ChatMessageDto message) throws Exception {
        assertThat(message.attachment()).isNull();
        assertThat(message.attachmentName()).isNull();
        assertThat(message.attachmentSize()).isNull();
        assertThat(message.attachmentStatus()).isNull();
        assertThat(message.attachmentProvider()).isNull();
        assertThat(message.attachmentNote()).isNull();
        assertThat(new ObjectMapper().writeValueAsString(message)).doesNotContain("PRIVATE", FILE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"U/PRIVATE.pdf", "attachments/U/PRIVATE.pdf",
            "C:\\legacy\\attachments\\U\\PRIVATE.pdf", "/opt/attachments/U/PRIVATE.pdf",
            "/api/attachments/tickets/by-storage-key?key=U%2FPRIVATE.pdf",
            "/api/attachments/tickets/by-path?path=attachments%2FU%2FPRIVATE.pdf",
            "/api/attachments/tickets/U/PRIVATE.pdf", "/api/attachments/tickets/%20T%20/PRIVATE.pdf",
            "api/attachments/tickets/by-storage-key?key=U%2FPRIVATE.pdf", "T/../U/PRIVATE.pdf",
            "/api/attachments/knowledge-base/PRIVATE.pdf", "/api/avatars/PRIVATE.pdf",
            "/api/attachments/tickets/by-storage-key?key=T%2FPRIVATE.pdf&key=U%2FPRIVATE.pdf",
            "/api/attachments/tickets/by-path?path=%ZZ", "//example.test/PRIVATE.pdf"})
    void foreignOrUnsupportedLegacyLocatorIsRedactedBeforeStorage(String reference) throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T"); ticket(fixture, 2, "U"); ticket(fixture, 3, " T ");
        anchor(fixture, "U", "U/PRIVATE.pdf");
        history(fixture, 12, "T", null, 2);
        history(fixture, 13, "T", reference, 2);
        fixture.jdbc().update("UPDATE chat_history SET reply_to_tg_id=12 WHERE id=13");
        write(fixture, "U/PRIVATE.pdf");
        write(fixture, "T/PRIVATE.pdf");

        var messages = fixture.reader().loadHistory("T", 2L);

        assertThat(messages).hasSize(2);
        redacted(messages.get(1));
        assertThat(messages.get(1).replyPreview()).isEqualTo("own message");
        assertThat(messages.get(1).telegramMessageId()).isEqualTo(13L);
        assertThat(messages.get(1).replyToTelegramMessageId()).isEqualTo(12L);
        verifyNoInteractions(fixture.storage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"filename", "nested-key", "path", "windows-path", "absolute-path",
            "key-api", "path-api", "direct-api", "relative-api", "literal-symbols", "encoded-symbols"})
    void ownerProofAndDescriptorUseTheSameLegacyLocator(String kind) throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        String key = switch (kind) {
            case "nested-key" -> "T/2026/10/" + FILE;
            case "literal-symbols", "encoded-symbols" -> "T/folder/plus+percent%2F hash#.pdf";
            default -> "T/" + FILE;
        };
        String reference = switch (kind) {
            case "filename" -> FILE;
            case "path" -> "attachments/" + key;
            case "windows-path" -> "C:\\legacy\\attachments\\" + key.replace('/', '\\');
            case "absolute-path" -> "/opt/attachments/" + key;
            case "key-api" -> "/api/attachments/tickets/by-storage-key?key=" + UriUtils.encode(key, StandardCharsets.UTF_8);
            case "path-api" -> "/api/attachments/tickets/by-path?path=" + UriUtils.encode("attachments/" + key, StandardCharsets.UTF_8);
            case "direct-api" -> "/api/attachments/tickets/T/" + FILE;
            case "relative-api" -> "api/attachments/tickets/T/" + FILE;
            case "encoded-symbols" -> "/api/attachments/tickets/by-storage-key?key=" + UriUtils.encode(key, StandardCharsets.UTF_8);
            default -> key;
        };
        history(fixture, 12, "T", reference, 2);
        fixture.jdbc().update("UPDATE chat_history SET file_name=NULL");
        write(fixture, key);

        ChatMessageDto message = single(fixture, "T");

        assertThat(message.attachment()).startsWith("/api/attachments/tickets/");
        assertThat(message.attachmentName()).isEqualTo(
                kind.endsWith("symbols") ? "plus+percent%2F hash" : "report.pdf");
        assertThat(message.attachmentSize()).isEqualTo(3L);
        if (kind.endsWith("symbols") || "nested-key".equals(kind)) {
            assertThat(AttachmentStorageKeyResolver.parseLegacyDialogReference(message.attachment()).reference())
                    .isEqualTo(key);
        }
        if ("absolute-path".equals(kind)) {
            assertThat(message.attachment()).startsWith("/api/attachments/tickets/by-path?path=");
        }
        if ("literal-symbols".equals(kind)) {
            assertThat(message.attachment()).contains("%2B", "%252F", "%23").doesNotContain("+");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {" T ", " T/100?&%#+ Ю "})
    void metadataOnAnotherHistoryRowProvesExactOpaqueOwnerWithoutIdNormalization(String owner) throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, owner); ticket(fixture, 2, owner.trim()); ticket(fixture, 3, "channel");
        String key = "channel/" + FILE;
        anchor(fixture, owner, key);
        String reference = "/api/attachments/tickets/by-storage-key?key=" + UriUtils.encode(key, StandardCharsets.UTF_8);
        history(fixture, 12, owner, reference, 2);
        fixture.jdbc().update("UPDATE chat_history SET file_name=NULL WHERE id=12");
        write(fixture, key);

        assertThat(single(fixture, owner).attachmentSize()).isEqualTo(3L);
        clearInvocations(fixture.storage());
        history(fixture, 13, owner.trim(), reference, 2);
        redacted(single(fixture, owner.trim()));
        verifyNoInteractions(fixture.storage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-schema", "prefix-collision", "duplicate-owner", "padded-without-proof"})
    void legacyOwnerCannotBeGuessedAfterLookupFailureOrNamespaceCollision(String kind) throws Exception {
        Fixture fixture = fixture();
        String owner = "padded-without-proof".equals(kind) ? " T " : "T";
        ticket(fixture, 1, owner);
        if ("prefix-collision".equals(kind)) ticket(fixture, 2, "T/2026");
        if ("duplicate-owner".equals(kind)) ticket(fixture, 2, owner);
        if ("padded-without-proof".equals(kind)) ticket(fixture, 2, owner.trim());
        history(fixture, 12, owner, "T/2026/" + FILE, 2);
        write(fixture, "T/2026/" + FILE);
        if ("missing-schema".equals(kind)) fixture.jdbc().execute("DROP TABLE chat_attachment_metadata");

        if ("duplicate-owner".equals(kind)) {
            assertThat(fixture.reader().loadHistory(owner, 2L)).isEmpty();
        } else {
            redacted(single(fixture, owner));
        }
        verifyNoInteractions(fixture.storage());
    }

    @Test
    void metadataFastPathEncodesLiteralPlusAndPercentWithoutBinaryIo() throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        String key = "T/report+%2F hash#.pdf";
        history(fixture, 12, "T", FILE, 2);
        fixture.jdbc().update("UPDATE chat_history SET file_name=NULL");
        fixture.jdbc().update("""
                INSERT INTO chat_attachment_metadata(chat_history_id,ticket_id,storage_key,original_name,size)
                VALUES (12,'T',?,'own-metadata-name.pdf',17)
                """, key);

        ChatMessageDto message = single(fixture, "T");

        assertThat(message.attachmentName()).isEqualTo("own-metadata-name.pdf");
        assertThat(message.attachmentSize()).isEqualTo(17L);
        assertThat(message.attachment()).contains("%2B", "%252F", "%23").doesNotContain("+");
        assertThat(AttachmentStorageKeyResolver.parseLegacyDialogReference(message.attachment()).reference())
                .isEqualTo(key);
        verifyNoInteractions(fixture.storage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"by-storage-key?key=T%2Freport.pdf",
            "by-path?path=attachments%2FT%2Freport.pdf", "T/report.pdf"})
    void missingOwnedBinaryUsesLocatorFilenameRatherThanApiRouteName(String suffix) throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        history(fixture, 12, "T", "/api/attachments/tickets/" + suffix, 2);
        fixture.jdbc().update("UPDATE chat_history SET file_name=NULL");

        ChatMessageDto message = single(fixture, "T");

        assertThat(message.attachment()).startsWith("/api/attachments/tickets/");
        assertThat(message.attachmentName()).isEqualTo("report.pdf");
        assertThat(message.attachmentSize()).isNull();
    }

    @Test
    void failedOwnerLookupRedactsLegacyReferenceWithoutHidingOwnText() throws Exception {
        Fixture fixture = fixture(true);
        ticket(fixture, 1, "T");
        history(fixture, 12, "T", FILE, 2);
        write(fixture, "T/" + FILE);

        redacted(single(fixture, "T"));
        verifyNoInteractions(fixture.storage());
    }

    @Test
    void rawExternalReferenceAndOwnHistoryNameWithoutLocatorRemainCompatible() throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        history(fixture, 12, "T", "https://example.test/report.pdf", 2);
        history(fixture, 13, "T", null, 2);
        fixture.jdbc().update("UPDATE chat_history SET file_name='own-name.pdf'");

        var messages = fixture.reader().loadHistory("T", 2L);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).attachment()).isEqualTo("https://example.test/report.pdf");
        assertThat(messages.get(0).attachmentSize()).isNull();
        assertThat(messages.get(1).attachment()).isNull();
        assertThat(messages.get(1).attachmentName()).isEqualTo("own-name.pdf");
        verifyNoInteractions(fixture.storage());
    }
}
