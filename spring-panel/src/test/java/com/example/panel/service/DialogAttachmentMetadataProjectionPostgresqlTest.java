package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.storage.AttachmentService;
import com.example.panel.storage.TicketAttachmentOwnerJdbcGuard;
import com.example.panel.support.PanelTimestampSqlSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class DialogAttachmentMetadataProjectionPostgresqlTest {
    @TempDir Path tempDir;
    private record Fixture(JdbcTemplate jdbc, DialogConversationReadService reader,
                           AttachmentObjectStorageService storage, LegacyTicketIdJdbcGuard tickets,
                           AttachmentService attachments) { }

    private Fixture fixture(boolean failedOwnerLookup) throws Exception {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("history_metadata_projection");
        jdbc.execute("CREATE TABLE tickets(is_general BOOLEAN NOT NULL DEFAULT FALSE,user_id BIGINT, ticket_id TEXT, PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("""
                CREATE TABLE chat_history(
                    id BIGINT PRIMARY KEY, ticket_id TEXT, sender TEXT DEFAULT 'client',
                    message TEXT DEFAULT 'own message', timestamp TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                    message_type TEXT DEFAULT 'document', attachment TEXT, file_name TEXT,
                    tg_message_id BIGINT, reply_to_tg_id BIGINT, channel_id BIGINT DEFAULT 77)
                """);
        jdbc.execute("""
                CREATE TABLE chat_attachment_metadata(
                    chat_history_id BIGINT UNIQUE REFERENCES chat_history(id), ticket_id TEXT, storage_key TEXT,
                    storage_provider TEXT DEFAULT 'local_fs', storage_class TEXT DEFAULT 'dialog_attachment',
                    normalization_status TEXT DEFAULT 'normalized', deleted_at TIMESTAMPTZ,
                    original_name TEXT DEFAULT 'PRIVATE-METADATA-NAME.pdf', size BIGINT DEFAULT 999,
                    availability_status TEXT DEFAULT 'available', legacy_attachment_ref TEXT DEFAULT 'https://example.test/PRIVATE-URL')
                """);
        var tickets = new LegacyTicketIdJdbcGuard(jdbc);
        JdbcTemplate ownerJdbc = jdbc;
        if (failedOwnerLookup) {
            ownerJdbc = mock(JdbcTemplate.class);
            when(ownerJdbc.query(anyString(), any(RowMapper.class), any(Object[].class)))
                    .thenThrow(new DataAccessResourceFailureException("owner lookup unavailable"));
        }
        var storage = mock(AttachmentObjectStorageService.class);
        var attachments = new AttachmentService(mock(PermissionService.class), storage, tickets,
                new TicketAttachmentOwnerJdbcGuard(ownerJdbc, tickets), tempDir.resolve("attachments").toString(),
                tempDir.resolve("knowledge").toString(), com.example.panel.support.AttachmentReadAccessStub.permitted());
        return new Fixture(jdbc, new DialogConversationReadService(jdbc, attachments,
                new PanelTimestampSqlSupport(), tickets), storage, tickets, attachments);
    }

    private Fixture fixture() throws Exception {
        return fixture(false);
    }

    private void ticket(Fixture fixture, long user, String ticket) {
        fixture.jdbc().update("INSERT INTO tickets(user_id,ticket_id) VALUES (?,?)", user, ticket);
    }

    private void history(Fixture fixture, long id, String ticket, String attachment, String name) {
        fixture.jdbc().update("INSERT INTO chat_history(id,ticket_id,attachment,file_name,tg_message_id) VALUES (?,?,?,?,?)",
                id, ticket, attachment, name, id);
    }

    private void metadata(Fixture fixture, long id, String owner, String key) {
        fixture.jdbc().update("INSERT INTO chat_attachment_metadata(chat_history_id,ticket_id,storage_key) VALUES (?,?,?)",
                id, owner, key);
    }

    private ChatMessageDto single(Fixture fixture, String owner) {
        var history = fixture.reader().loadHistory(owner, null);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).message()).isEqualTo("own message");
        assertThat(history.get(0).sender()).isEqualTo("client");
        return history.get(0);
    }

    private void redacted(ChatMessageDto message) throws Exception {
        assertThat(message.attachment()).isNull();
        assertThat(message.attachmentName()).isNull();
        assertThat(message.attachmentSize()).isNull();
        assertThat(message.attachmentStatus()).isNull();
        assertThat(message.attachmentProvider()).isNull();
        assertThat(message.attachmentNote()).isNull();
        assertThat(new ObjectMapper().writeValueAsString(message)).doesNotContain("PRIVATE", "foreign/key");
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign-owner", "padded-sibling", "null-owner", "blank-owner", "deleted",
            "wrong-class", "null-class", "provider", "unresolved-key", "unsafe-key", "multiple-owners",
            "shared-deleted", "shared-unknown", "external-foreign", "external-deleted",
            "blank-key", "provider-without-key", "blank-provider"})
    void invalidMetadataCannotExposeAnyAttachmentFieldOrReactivateRawHistoryFallback(String invalid) throws Exception {
        Fixture fixture = fixture();
        String owner = " T ";
        ticket(fixture, 1, owner); ticket(fixture, 2, "T"); ticket(fixture, 3, "U");
        String key = "channel/foreign/key.pdf";
        history(fixture, 11, owner, "attachments/foreign/key.pdf", "PRIVATE-HISTORY-NAME.pdf");
        metadata(fixture, 11, owner, key);
        switch (invalid) {
            case "foreign-owner" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET ticket_id='U'");
            case "padded-sibling" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET ticket_id='T'");
            case "null-owner" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET ticket_id=NULL");
            case "blank-owner" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET ticket_id=' '");
            case "deleted" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET deleted_at=CURRENT_TIMESTAMP");
            case "wrong-class" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET storage_class='knowledge_base'");
            case "null-class" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET storage_class=NULL");
            case "provider" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET storage_provider='unknown'");
            case "unresolved-key" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET normalization_status='unresolved'");
            case "unsafe-key" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET storage_key='T/../U/PRIVATE.pdf'");
            case "blank-key" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET storage_key=' '");
            case "provider-without-key" -> fixture.jdbc().update(
                    "UPDATE chat_attachment_metadata SET storage_key=NULL,storage_provider='unknown'");
            case "blank-provider" -> fixture.jdbc().update(
                    "UPDATE chat_attachment_metadata SET storage_key=NULL,storage_provider=' local_fs '");
            case "multiple-owners", "shared-deleted", "shared-unknown" -> {
                String secondOwner = "multiple-owners".equals(invalid) ? "U" : owner;
                history(fixture, 12, secondOwner, null, null);
                metadata(fixture, 12, secondOwner, key);
                if ("shared-deleted".equals(invalid)) {
                    fixture.jdbc().update("UPDATE chat_attachment_metadata SET deleted_at=CURRENT_TIMESTAMP WHERE chat_history_id=12");
                } else if ("shared-unknown".equals(invalid)) {
                    fixture.jdbc().update("UPDATE chat_attachment_metadata SET ticket_id=NULL WHERE chat_history_id=12");
                }
                fixture.jdbc().update("UPDATE chat_history SET channel_id=88 WHERE id=12");
            }
            case "external-foreign", "external-deleted" -> {
                fixture.jdbc().update("""
                        UPDATE chat_attachment_metadata SET storage_key=NULL, storage_provider='external_url',
                            availability_status='external'
                        """);
                if ("external-foreign".equals(invalid)) {
                    fixture.jdbc().update("UPDATE chat_attachment_metadata SET ticket_id='U'");
                } else {
                    fixture.jdbc().update("UPDATE chat_attachment_metadata SET deleted_at=CURRENT_TIMESTAMP");
                }
            }
            default -> throw new AssertionError(invalid);
        }

        var messages = fixture.reader().loadHistory(owner, 77L);
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).message()).isEqualTo("own message");
        redacted(messages.get(0));
        verifyNoInteractions(fixture.storage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"T", " T ", " T/100?&%#+ Ю ", " null ", "T%2F+", "../opaque"})
    void exactOwnedMetadataFastPathPreservesNamesSizeAndUrlWithoutBinaryIo(String owner) throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, owner);
        if (!owner.equals(owner.trim())) ticket(fixture, 2, owner.trim());
        ticket(fixture, 3, "channel");
        String key = "channel/attachments/report.pdf";
        history(fixture, 11, owner, "report.pdf", null);
        metadata(fixture, 11, owner, key);

        ChatMessageDto message = single(fixture, owner);

        assertThat(message.attachmentName()).isEqualTo("PRIVATE-METADATA-NAME.pdf");
        assertThat(message.attachmentSize()).isEqualTo(999L);
        assertThat(message.attachment()).isEqualTo("/api/attachments/tickets/by-storage-key?key=" + key);
        assertThat(message.attachmentStatus()).isEqualTo("available");
        assertThat(message.attachmentProvider()).isEqualTo("local_fs");
        verifyNoInteractions(fixture.storage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "external", "unresolved"})
    void ownedAvailabilityStatesRemainCompatible(String state) throws Exception {
        Fixture fixture = fixture();
        String owner = " T ";
        ticket(fixture, 1, owner);
        history(fixture, 11, owner, null, null);
        metadata(fixture, 11, owner, "missing".equals(state) ? "T/report.pdf" : null);
        switch (state) {
            case "missing" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET availability_status='missing'");
            case "external" -> fixture.jdbc().update("""
                    UPDATE chat_attachment_metadata SET storage_provider='external_url', availability_status='external'
                    """);
            case "unresolved" -> {
                fixture.jdbc().update("""
                        UPDATE chat_attachment_metadata SET normalization_status='unresolved', availability_status='unresolved'
                        """);
                fixture.jdbc().update("UPDATE chat_history SET file_name='own-unresolved-name.pdf'");
            }
            default -> throw new AssertionError(state);
        }

        ChatMessageDto message = single(fixture, owner);

        assertThat(message.attachmentName()).isEqualTo(
                "unresolved".equals(state) ? "own-unresolved-name.pdf" : "PRIVATE-METADATA-NAME.pdf");
        assertThat(message.attachmentSize()).isEqualTo(999L);
        assertThat(message.attachmentStatus()).isEqualTo(state);
        assertThat(message.attachmentNote()).isNotBlank();
        if ("external".equals(state)) {
            assertThat(message.attachment()).isEqualTo("https://example.test/PRIVATE-URL");
        } else {
            assertThat(message.attachment()).isNull();
        }
        verifyNoInteractions(fixture.storage());
    }

    @Test
    void ownerLookupFailureRedactsOnlyAttachmentAndKeepsOwnMessage() throws Exception {
        Fixture fixture = fixture(true);
        ticket(fixture, 1, "T");
        history(fixture, 11, "T", "PRIVATE.bin", "PRIVATE-HISTORY-NAME.pdf");
        metadata(fixture, 11, "T", "T/PRIVATE.bin");

        redacted(single(fixture, "T"));
        verifyNoInteractions(fixture.storage());
    }

    @Test
    void absentMetadataDoesNotSuppressLegacyHistoryName() throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        history(fixture, 11, "T", null, "legacy-owned-name.pdf");

        ChatMessageDto message = single(fixture, "T");

        assertThat(message.attachmentName()).isEqualTo("legacy-owned-name.pdf");
        assertThat(message.attachment()).isNull();
        verifyNoInteractions(fixture.storage());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "T"})
    void readOnlyOwnerProbeDoesNotAcceptMissingContextOrTrimmedSibling(String requestedOwner) throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, " T "); ticket(fixture, 2, "T");
        history(fixture, 11, " T ", null, null);
        metadata(fixture, 11, " T ", "channel/report.pdf");

        assertThat(fixture.attachments().hasTicketAttachmentOwnerByStorageKey(requestedOwner, "channel/report.pdf")).isFalse();
        verifyNoInteractions(fixture.storage());
    }

    @Test
    void ownedS3MetadataProjectsWithoutConnectingToObjectStorage() throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, " T ");
        history(fixture, 11, " T ", "report.pdf", null);
        metadata(fixture, 11, " T ", "channel/report.pdf");
        fixture.jdbc().update("UPDATE chat_attachment_metadata SET storage_provider='s3'");

        ChatMessageDto message = single(fixture, " T ");

        assertThat(message.attachmentName()).isEqualTo("PRIVATE-METADATA-NAME.pdf");
        assertThat(message.attachmentSize()).isEqualTo(999L);
        assertThat(message.attachmentProvider()).isEqualTo("s3");
        verifyNoInteractions(fixture.storage());
    }

    @Test
    void redactionPreservesReplyPreviewAndOwnMessageFields() throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T"); ticket(fixture, 2, "U");
        history(fixture, 11, "T", null, null);
        history(fixture, 12, "T", "PRIVATE.bin", "PRIVATE-HISTORY-NAME.pdf");
        fixture.jdbc().update("UPDATE chat_history SET reply_to_tg_id=11 WHERE id=12");
        metadata(fixture, 12, "U", "foreign/PRIVATE.bin");

        var messages = fixture.reader().loadHistory("T", null);

        assertThat(messages).hasSize(2);
        redacted(messages.get(1));
        assertThat(messages.get(1).message()).isEqualTo("own message");
        assertThat(messages.get(1).originalMessage()).isEqualTo("own message");
        assertThat(messages.get(1).replyPreview()).isEqualTo("own message");
        assertThat(messages.get(1).telegramMessageId()).isEqualTo(12L);
        assertThat(messages.get(1).replyToTelegramMessageId()).isEqualTo(11L);
        verifyNoInteractions(fixture.storage());
    }

    @Test
    void sameExactOwnerCanShareStorageKeyAcrossMultipleMetadataReferences() throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, " T ");
        history(fixture, 11, " T ", "report.pdf", null);
        history(fixture, 12, " T ", "report.pdf", null);
        metadata(fixture, 11, " T ", "channel/report.pdf");
        metadata(fixture, 12, " T ", "channel/report.pdf");

        var messages = fixture.reader().loadHistory(" T ", null);

        assertThat(messages).hasSize(2);
        assertThat(messages).allSatisfy(message -> {
            assertThat(message.attachmentName()).isEqualTo("PRIVATE-METADATA-NAME.pdf");
            assertThat(message.attachmentSize()).isEqualTo(999L);
        });
        verifyNoInteractions(fixture.storage());
    }
}
