package com.example.panel.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.panel.service.DialogConversationReadService;
import com.example.panel.service.LegacyTicketIdJdbcGuard;
import com.example.panel.service.PermissionService;
import com.example.panel.support.PanelTimestampSqlSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class AttachmentDescriptorOwnershipPostgresqlTest {
    private static final String FILE = "12345678-1234-1234-1234-123456789abc_report.pdf";
    @TempDir Path tempDir;

    private record Fixture(JdbcTemplate jdbc, AttachmentService service, AttachmentObjectStorageService storage,
                           Path root, LegacyTicketIdJdbcGuard tickets) { }

    private Fixture fixture() throws Exception {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("attachment_descriptor_owner");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("""
                CREATE TABLE chat_history(
                    id BIGINT PRIMARY KEY, ticket_id TEXT, sender TEXT DEFAULT 'client',
                    message TEXT DEFAULT 'attachment', timestamp TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                    message_type TEXT DEFAULT 'document', attachment TEXT, tg_message_id BIGINT,
                    reply_to_tg_id BIGINT, channel_id BIGINT DEFAULT 1)
                """);
        jdbc.execute("""
                CREATE TABLE chat_attachment_metadata(
                    chat_history_id BIGINT UNIQUE REFERENCES chat_history(id), ticket_id TEXT, storage_key TEXT,
                    storage_provider TEXT DEFAULT 'local_fs', storage_class TEXT DEFAULT 'dialog_attachment',
                    normalization_status TEXT DEFAULT 'normalized', deleted_at TIMESTAMPTZ,
                    original_name TEXT, size BIGINT, availability_status TEXT DEFAULT 'available',
                    legacy_attachment_ref TEXT)
                """);
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setMode("local_fs");
        Path root = tempDir.resolve("attachments");
        Path knowledge = tempDir.resolve("knowledge");
        var storage = spy(new AttachmentObjectStorageService(properties, root.toString(), knowledge.toString(),
                tempDir.resolve("passport").toString(), tempDir.resolve("avatars").toString()));
        var tickets = new LegacyTicketIdJdbcGuard(jdbc);
        var owner = new TicketAttachmentOwnerJdbcGuard(jdbc, tickets);
        var service = spy(new AttachmentService(mock(PermissionService.class), storage, tickets, owner,
                root.toString(), knowledge.toString()));
        return new Fixture(jdbc, service, storage, root, tickets);
    }

    private void ticket(Fixture fixture, long user, String ticket) {
        fixture.jdbc().update("INSERT INTO tickets VALUES (?,?)", user, ticket);
    }

    private void history(Fixture fixture, long id, String ticket, String attachment, long channel) {
        fixture.jdbc().update("INSERT INTO chat_history(id,ticket_id,attachment,channel_id) VALUES (?,?,?,?)",
                id, ticket, attachment, channel);
    }

    private void metadata(Fixture fixture, long id, String owner, String key) {
        fixture.jdbc().update("INSERT INTO chat_attachment_metadata(chat_history_id,ticket_id,storage_key) VALUES (?,?,?)",
                id, owner, key);
    }

    private void write(Fixture fixture, String key) throws Exception {
        Path file = fixture.root().resolve(key);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {1, 2, 3});
    }

    private void descriptor(AttachmentService.AttachmentDescriptor descriptor) {
        assertThat(descriptor.originalName()).isEqualTo("report.pdf");
        assertThat(descriptor.size()).isEqualTo(3);
    }

    private void denied(org.assertj.core.api.ThrowableAssert.ThrowingCallable probe) {
        assertThatThrownBy(probe).isInstanceOf(IllegalArgumentException.class).hasMessage("File not found");
    }

    @ParameterizedTest
    @ValueSource(strings = {"T", " T ", " T/100?&%#+ Ю ", " null ", "T%2F+", "../opaque"})
    void exactMetadataOwnerAllowsDescriptorsWithoutGuessingStorageNamespace(String owner) throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, owner);
        if (!owner.equals(owner.trim())) ticket(fixture, 2, owner.trim());
        ticket(fixture, 3, "channel");
        String key = "channel/attachments/" + FILE;
        history(fixture, 11, owner, key, 1);
        metadata(fixture, 11, owner, key);
        write(fixture, key);
        String storedPath = "C:\\legacy\\attachments\\" + key.replace('/', '\\');

        descriptor(fixture.service().describeTicketAttachmentByStorageKey(key));
        descriptor(fixture.service().describeTicketAttachmentByStorageKey(owner, key));
        descriptor(fixture.service().describeTicketAttachmentByPath(storedPath));
        descriptor(fixture.service().describeTicketAttachmentByPath(owner, storedPath));
        assertThat(fixture.service().hasTicketAttachmentByStorageKey(key)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"T", " T "})
    void directDescriptorRequiresExactMetadataOwnerAndPreservesNameSize(String owner) throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, owner);
        if (!owner.equals(owner.trim())) ticket(fixture, 2, owner.trim());
        String key = owner.trim() + "/" + FILE;
        history(fixture, 11, owner, FILE, 1);
        metadata(fixture, 11, owner, key);
        write(fixture, key);

        descriptor(fixture.service().describeTicketAttachment(owner, FILE));
        clearInvocations(fixture.storage());
        if (!owner.equals(owner.trim())) {
            denied(() -> fixture.service().describeTicketAttachment(owner.trim(), FILE));
            verifyNoInteractions(fixture.storage());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"history-mismatch", "owners", "unknown", "deleted", "provider",
            "class", "normalization", "duplicate-ticket", "absent-ticket"})
    void invalidMetadataPreventsAllDescriptorAndExistenceStorageCalls(String invalid) throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        String key = "T/" + FILE;
        history(fixture, 11, "T", FILE, 1);
        metadata(fixture, 11, "T", key);
        write(fixture, key);
        switch (invalid) {
            case "history-mismatch" -> {
                ticket(fixture, 2, "U");
                fixture.jdbc().update("UPDATE chat_attachment_metadata SET ticket_id='U'");
            }
            case "owners" -> {
                ticket(fixture, 2, "U");
                history(fixture, 12, "U", FILE, 1);
                metadata(fixture, 12, "U", key);
            }
            case "unknown" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET ticket_id=NULL");
            case "deleted" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET deleted_at=CURRENT_TIMESTAMP");
            case "provider" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET storage_provider='external_url'");
            case "class" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET storage_class='knowledge_base'");
            case "normalization" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET normalization_status='unresolved'");
            case "duplicate-ticket" -> ticket(fixture, 2, "T");
            case "absent-ticket" -> fixture.jdbc().update("DELETE FROM tickets");
            default -> throw new AssertionError(invalid);
        }

        denied(() -> fixture.service().describeTicketAttachment("T", FILE));
        denied(() -> fixture.service().describeTicketAttachmentByStorageKey(key));
        denied(() -> fixture.service().describeTicketAttachmentByPath("attachments/" + key));
        denied(() -> fixture.service().describeTicketAttachmentByStorageKey("T", key));
        denied(() -> fixture.service().describeTicketAttachmentByPath("T", "attachments/" + key));
        assertThat(fixture.service().hasTicketAttachmentByStorageKey(key)).isFalse();
        verifyNoInteractions(fixture.storage());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "OWNER"})
    void ticketBoundDescriptorRejectsMissingOrTrimmedSiblingContext(String requestedOwner) throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, " OWNER "); ticket(fixture, 2, "OWNER");
        String key = "channel/" + FILE;
        history(fixture, 11, " OWNER ", FILE, 1);
        metadata(fixture, 11, " OWNER ", key);
        write(fixture, key);

        denied(() -> fixture.service().describeTicketAttachmentByStorageKey(requestedOwner, key));
        denied(() -> fixture.service().describeTicketAttachmentByPath(requestedOwner, "attachments/" + key));
        verifyNoInteractions(fixture.storage());
    }

    @Test
    void uniqueLegacyDirectoryWithoutMetadataStillAllowsDescriptorsButAbsentFileStaysFalse() throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        String key = "T/" + FILE;
        write(fixture, key);

        descriptor(fixture.service().describeTicketAttachment("T", FILE));
        descriptor(fixture.service().describeTicketAttachmentByStorageKey(key));
        descriptor(fixture.service().describeTicketAttachmentByStorageKey("T", key));
        descriptor(fixture.service().describeTicketAttachmentByPath("T/" + FILE));
        descriptor(fixture.service().describeTicketAttachmentByPath("T", "attachments/" + key));
        assertThat(fixture.service().hasTicketAttachmentByStorageKey(key)).isTrue();
        assertThat(fixture.service().hasTicketAttachmentByStorageKey("T/absent.pdf")).isFalse();
    }

    @Test
    void missingMetadataSchemaDoesNotFallBackToExistingLegacyFile() throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        String key = "T/" + FILE;
        write(fixture, key);
        fixture.jdbc().execute("DROP TABLE chat_attachment_metadata");

        denied(() -> fixture.service().describeTicketAttachment("T", FILE));
        denied(() -> fixture.service().describeTicketAttachmentByStorageKey(key));
        denied(() -> fixture.service().describeTicketAttachmentByPath("attachments/" + key));
        assertThat(fixture.service().hasTicketAttachmentByStorageKey(key)).isFalse();
        verifyNoInteractions(fixture.storage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"key", "path", "direct"})
    void historyFileProbeCarriesExactTicketAndEnrichesOnlyItsOwnedFile(String kind) throws Exception {
        Fixture fixture = fixture();
        String owner = "direct".equals(kind) ? " T-MEDIA " : " T/100?&%#+ Ю ";
        ticket(fixture, 1, owner); ticket(fixture, 2, owner.trim());
        String key = "direct".equals(kind) ? owner.trim() + "/" + FILE : "channel/attachments/" + FILE;
        String raw = "path".equals(kind) ? "attachments/" + key : FILE;
        history(fixture, 11, owner, null, 1);
        history(fixture, 12, owner, raw, 2);
        metadata(fixture, "key".equals(kind) ? 12 : 11, owner, key);
        write(fixture, key);
        var reader = new DialogConversationReadService(fixture.jdbc(), fixture.service(),
                new PanelTimestampSqlSupport(), fixture.tickets());

        var messages = reader.loadHistory(owner, 2L);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).attachmentName()).isEqualTo("report.pdf");
        assertThat(messages.get(0).attachmentSize()).isEqualTo(3L);
        switch (kind) {
            case "key" -> verify(fixture.service()).describeTicketAttachmentByStorageKey(owner, key);
            case "path" -> verify(fixture.service()).describeTicketAttachmentByPath(owner, raw);
            case "direct" -> verify(fixture.service()).describeTicketAttachment(owner, FILE);
            default -> throw new AssertionError(kind);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"key", "path", "direct"})
    void historyDoesNotReadFileSizeFromAnotherExactOwner(String kind) throws Exception {
        Fixture fixture = fixture();
        String owner = " T-MEDIA ";
        String foreign = owner.trim();
        ticket(fixture, 1, owner); ticket(fixture, 2, foreign);
        String key = "direct".equals(kind) ? foreign + "/" + FILE : "channel/" + FILE;
        String raw = "path".equals(kind) ? "attachments/" + key : FILE;
        history(fixture, 11, foreign, null, 1);
        history(fixture, 12, owner, raw, 2);
        metadata(fixture, 11, foreign, key);
        if ("key".equals(kind)) metadata(fixture, 12, foreign, key);
        write(fixture, key);
        var reader = new DialogConversationReadService(fixture.jdbc(), fixture.service(),
                new PanelTimestampSqlSupport(), fixture.tickets());

        var messages = reader.loadHistory(owner, 2L);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).attachmentSize()).isNull();
        if ("key".equals(kind)) {
            assertThat(messages.get(0).attachment()).isNull();
            assertThat(messages.get(0).attachmentName()).isNull();
            assertThat(messages.get(0).attachmentProvider()).isNull();
        } else {
            assertThat(messages.get(0).attachmentName()).isEqualTo("report.pdf");
        }
        verifyNoInteractions(fixture.storage());
    }
}
