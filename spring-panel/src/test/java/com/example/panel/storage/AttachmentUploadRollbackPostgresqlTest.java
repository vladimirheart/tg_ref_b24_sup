package com.example.panel.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.service.DialogAiAssistantService;
import com.example.panel.service.ClientBlacklistService;
import com.example.panel.service.DialogLookupReadService;
import com.example.panel.service.DialogNotificationService;
import com.example.panel.service.DialogParticipantService;
import com.example.panel.service.DialogQuickActionService;
import com.example.panel.service.DialogReplyService;
import com.example.panel.service.DialogResponsibilityService;
import com.example.panel.service.DialogTicketLifecycleService;
import com.example.panel.service.LegacyTicketIdJdbcGuard;
import com.example.panel.service.NotificationService;
import com.example.panel.service.PermissionService;
import com.example.panel.service.UiEventStreamService;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

class AttachmentUploadRollbackPostgresqlTest {
    @TempDir Path tempDir;
    private record Fixture(JdbcTemplate jdbc, AttachmentService service, AttachmentObjectStorageService storage,
                           Authentication authentication, Path root) { }

    private Fixture fixture() throws Exception {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("attachment_upload_rollback");
        jdbc.execute("CREATE TABLE tickets(user_id BIGINT, ticket_id TEXT, PRIMARY KEY(user_id,ticket_id))");
        jdbc.execute("CREATE TABLE chat_history(id BIGINT PRIMARY KEY, ticket_id TEXT, attachment TEXT)");
        jdbc.execute("""
                CREATE TABLE chat_attachment_metadata(
                    chat_history_id BIGINT UNIQUE REFERENCES chat_history(id), ticket_id TEXT, storage_key TEXT,
                    storage_provider TEXT DEFAULT 'local_fs', storage_class TEXT DEFAULT 'dialog_attachment',
                    normalization_status TEXT DEFAULT 'normalized', deleted_at TIMESTAMPTZ)
                """);
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setMode("local_fs");
        Path root = tempDir.resolve("attachments");
        Path knowledge = tempDir.resolve("knowledge");
        var storage = spy(new AttachmentObjectStorageService(properties, root.toString(), knowledge.toString(),
                tempDir.resolve("passport").toString(), tempDir.resolve("avatars").toString()));
        var tickets = new LegacyTicketIdJdbcGuard(jdbc);
        var owner = new TicketAttachmentOwnerJdbcGuard(jdbc, tickets);
        var permissions = mock(PermissionService.class);
        var authentication = mock(Authentication.class);
        when(permissions.hasAuthority(authentication, "PAGE_DIALOGS")).thenReturn(true);
        var service = spy(new AttachmentService(permissions, storage, tickets, owner, root.toString(), knowledge.toString()));
        return new Fixture(jdbc, service, storage, authentication, root);
    }

    private void ticket(Fixture fixture, long user, String ticket) {
        fixture.jdbc().update("INSERT INTO tickets VALUES (?,?)", user, ticket);
    }

    private void history(Fixture fixture, long id, String ticket, String attachment) {
        fixture.jdbc().update("INSERT INTO chat_history VALUES (?,?,?)", id, ticket, attachment);
    }

    private void metadata(Fixture fixture, long id, String ticket, String key) {
        fixture.jdbc().update("INSERT INTO chat_attachment_metadata(chat_history_id,ticket_id,storage_key) VALUES (?,?,?)",
                id, ticket, key);
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "report.pdf", "application/pdf", new byte[] {1, 2, 3});
    }

    private String key(String ticket, String storedName) {
        return ticket.trim() + "/" + storedName;
    }

    private Path uploadFile(Fixture fixture, String ticket, AttachmentService.TicketAttachmentUpload upload) {
        return fixture.root().resolve(key(ticket, upload.metadata().storedName()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"T", " T ", "T/opaque", "T%2F+", " null ", "T/Ю+"})
    void rollbackReceiptRemovesOnlyItsNewFileAndOnlyOnce(String ticket) throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, ticket);
        if (!ticket.equals(ticket.trim())) ticket(fixture, 2, ticket.trim());
        var upload = fixture.service().prepareTicketAttachment(fixture.authentication(), ticket, file());
        Path created = uploadFile(fixture, ticket, upload);
        Path unrelated = fixture.root().resolve("unrelated/private.pdf");
        Files.createDirectories(unrelated.getParent());
        Files.write(unrelated, new byte[] {7, 8});
        assertThat(Files.readAllBytes(created)).containsExactly(1, 2, 3);
        assertThat(upload.metadata().originalName()).isEqualTo("report.pdf");
        assertThat(upload.metadata().size()).isEqualTo(3);
        assertThat(Modifier.isPrivate(upload.getClass().getModifiers())).isTrue();
        assertThat(Modifier.isFinal(upload.getClass().getModifiers())).isTrue();
        clearInvocations(fixture.storage());

        upload.rollback();
        upload.rollback();
        upload.retain();
        upload.rollback();

        assertThat(created).doesNotExist();
        assertThat(Files.readAllBytes(unrelated)).containsExactly(7, 8);
        verify(fixture.storage(), times(1)).deleteDialogAttachment(ticket, upload.metadata().storedName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"T", " T "})
    void retainedUploadCannotBeDeletedByLateRollback(String ticket) throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, ticket);
        var upload = fixture.service().prepareTicketAttachment(fixture.authentication(), ticket, file());
        Path created = uploadFile(fixture, ticket, upload);
        clearInvocations(fixture.storage());

        upload.retain();
        upload.rollback();

        assertThat(Files.readAllBytes(created)).containsExactly(1, 2, 3);
        verifyNoInteractions(fixture.storage());
    }

    @Test
    void concurrentRollbackCallsConsumeOneReceiptOnlyOnce() throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        var upload = fixture.service().prepareTicketAttachment(fixture.authentication(), "T", file());
        clearInvocations(fixture.storage());
        var workers = Executors.newFixedThreadPool(4);
        try {
            var tasks = new CompletableFuture<?>[4];
            for (int i = 0; i < tasks.length; i++) {
                tasks[i] = CompletableFuture.runAsync(() -> {
                    try {
                        upload.rollback();
                    } catch (IOException ex) {
                        throw new IllegalStateException(ex);
                    }
                }, workers);
            }
            CompletableFuture.allOf(tasks).get();
        } finally {
            workers.shutdownNow();
        }

        assertThat(uploadFile(fixture, "T", upload)).doesNotExist();
        verify(fixture.storage(), times(1)).deleteDialogAttachment("T", upload.metadata().storedName());
    }

    @ParameterizedTest
    @ValueSource(strings = {"metadata", "foreign-metadata", "deleted-metadata", "history-raw",
            "history-key", "history-path", "history-foreign"})
    void anyPersistedClaimPreventsReceiptCleanupEvenWithoutMatchingActiveMetadata(String claim) throws Exception {
        Fixture fixture = fixture();
        String owner = " T ";
        ticket(fixture, 1, owner); ticket(fixture, 2, "U");
        var upload = fixture.service().prepareTicketAttachment(fixture.authentication(), owner, file());
        String storedName = upload.metadata().storedName();
        String key = key(owner, storedName);
        switch (claim) {
            case "metadata", "foreign-metadata", "deleted-metadata" -> {
                String claimedOwner = "foreign-metadata".equals(claim) ? "U" : owner;
                history(fixture, 11, claimedOwner, null);
                metadata(fixture, 11, claimedOwner, key);
                if ("deleted-metadata".equals(claim)) {
                    fixture.jdbc().update("UPDATE chat_attachment_metadata SET deleted_at=CURRENT_TIMESTAMP");
                }
            }
            case "history-raw" -> history(fixture, 11, owner, storedName);
            case "history-key" -> history(fixture, 11, owner, key);
            case "history-path" -> history(fixture, 11, owner, "attachments/" + key);
            case "history-foreign" -> history(fixture, 11, "U", storedName);
            default -> throw new AssertionError(claim);
        }
        clearInvocations(fixture.storage());

        upload.rollback();

        assertThat(Files.readAllBytes(uploadFile(fixture, owner, upload))).containsExactly(1, 2, 3);
        verifyNoInteractions(fixture.storage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"metadata-schema", "history-schema", "duplicate-ticket", "missing-ticket"})
    void failedOrAmbiguousClaimCheckKeepsPendingFile(String failure) throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        var upload = fixture.service().prepareTicketAttachment(fixture.authentication(), "T", file());
        switch (failure) {
            case "metadata-schema" -> fixture.jdbc().execute("DROP TABLE chat_attachment_metadata");
            case "history-schema" -> fixture.jdbc().execute("ALTER TABLE chat_history DROP COLUMN attachment");
            case "duplicate-ticket" -> ticket(fixture, 2, "T");
            case "missing-ticket" -> fixture.jdbc().update("DELETE FROM tickets");
            default -> throw new AssertionError(failure);
        }
        clearInvocations(fixture.storage());

        upload.rollback();

        assertThat(uploadFile(fixture, "T", upload)).exists();
        verifyNoInteractions(fixture.storage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-owner", "history-mismatch", "unknown", "deleted", "normalization",
            "class", "provider", "duplicate", "absent", "unclaimed-padded"})
    void ordinaryDeleteKeepsFileWhenExactPhysicalOwnerIsNotResolved(String failure) throws Exception {
        Fixture fixture = fixture();
        String owner = " T ";
        ticket(fixture, 1, owner); ticket(fixture, 2, "T");
        var upload = fixture.service().prepareTicketAttachment(fixture.authentication(), owner, file());
        upload.retain();
        String storedName = upload.metadata().storedName();
        if (!"unclaimed-padded".equals(failure)) {
            history(fixture, 11, owner, storedName);
            metadata(fixture, 11, owner, key(owner, storedName));
        }
        switch (failure) {
            case "wrong-owner" -> {
                fixture.jdbc().update("UPDATE chat_history SET ticket_id='T'");
                fixture.jdbc().update("UPDATE chat_attachment_metadata SET ticket_id='T'");
            }
            case "history-mismatch" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET ticket_id='T'");
            case "unknown" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET ticket_id=NULL");
            case "deleted" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET deleted_at=CURRENT_TIMESTAMP");
            case "normalization" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET normalization_status='unresolved'");
            case "class" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET storage_class='knowledge_base'");
            case "provider" -> fixture.jdbc().update("UPDATE chat_attachment_metadata SET storage_provider='external_url'");
            case "duplicate" -> ticket(fixture, 3, owner);
            case "absent" -> fixture.jdbc().update("DELETE FROM tickets WHERE ticket_id=?", owner);
            case "unclaimed-padded" -> { }
            default -> throw new AssertionError(failure);
        }
        clearInvocations(fixture.storage());

        fixture.service().deleteTicketAttachment(owner, storedName);

        assertThat(Files.readAllBytes(uploadFile(fixture, owner, upload))).containsExactly(1, 2, 3);
        verifyNoInteractions(fixture.storage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"T", " T "})
    void ordinaryDeleteRemovesOnlyMatchingMetadataOwnerFile(String owner) throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, owner);
        var upload = fixture.service().prepareTicketAttachment(fixture.authentication(), owner, file());
        upload.retain();
        history(fixture, 11, owner, upload.metadata().storedName());
        metadata(fixture, 11, owner, key(owner, upload.metadata().storedName()));

        fixture.service().deleteTicketAttachment(owner, upload.metadata().storedName());

        assertThat(uploadFile(fixture, owner, upload)).doesNotExist();
        assertThat(fixture.jdbc().queryForObject("SELECT COUNT(*) FROM chat_attachment_metadata", Integer.class)).isEqualTo(1);
    }

    @Test
    void ordinaryDeletePreservesUniquePlainLegacyDirectoryCompatibility() throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        var upload = fixture.service().prepareTicketAttachment(fixture.authentication(), "T", file());
        upload.retain();

        fixture.service().deleteTicketAttachment("T", upload.metadata().storedName());
        fixture.service().deleteTicketAttachment("T", upload.metadata().storedName());

        assertThat(uploadFile(fixture, "T", upload)).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"T/../U", "T//nested", " T /nested", "T/./U"})
    void unsafeDirectoryIsRejectedBeforeMultipartStreamOrStorageWrite(String ticket) throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, ticket);
        var multipart = spy(file());

        assertThatThrownBy(() -> fixture.service().prepareTicketAttachment(fixture.authentication(), ticket, multipart))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid dialog attachment key");

        verify(multipart, never()).getInputStream();
        verifyNoInteractions(fixture.storage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"../../private.pdf", "dir/../../private.pdf", "file."})
    void unsafeOriginalFilenameIsRejectedBeforeStorageWrite(String name) throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        var multipart = spy(new MockMultipartFile("file", name, "application/pdf", new byte[] {1}));

        assertThatThrownBy(() -> fixture.service().prepareTicketAttachment(fixture.authentication(), "T", multipart))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Invalid dialog attachment key");

        verify(multipart, never()).getInputStream();
        verifyNoInteractions(fixture.storage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"success", "error", "runtime", "io-cleanup", "runtime-cleanup", "partial-history"})
    void realQuickActionUploadPreservesResultAndFileIntegrityAcrossCleanup(String outcome) throws Exception {
        Fixture fixture = fixture();
        String owner = " T ";
        ticket(fixture, 1, owner); ticket(fixture, 2, "T");
        var reply = mock(DialogReplyService.class);
        var ai = mock(DialogAiAssistantService.class);
        var notifications = mock(NotificationService.class);
        var service = new DialogQuickActionService(mock(DialogTicketLifecycleService.class),
                mock(DialogLookupReadService.class), mock(DialogResponsibilityService.class), mock(DialogParticipantService.class),
                reply, mock(DialogNotificationService.class), ai, notifications,
                mock(UiEventStreamService.class), fixture.service(), mock(ClientBlacklistService.class));
        var storedName = new AtomicReference<String>();
        var transportFailure = new IllegalStateException("transport exploded");
        if ("io-cleanup".equals(outcome)) {
            doThrow(new IOException("cleanup IO")).when(fixture.storage()).deleteDialogAttachment(anyString(), anyString());
        } else if ("runtime-cleanup".equals(outcome)) {
            doThrow(new IllegalStateException("cleanup runtime")).when(fixture.storage()).deleteDialogAttachment(anyString(), anyString());
        }
        when(reply.sendMediaReply(eq(owner), any(MultipartFile.class), eq("caption"), isNull(), eq("operator"),
                anyString(), eq("report.pdf"), eq("application/pdf"), eq(3L))).thenAnswer(invocation -> {
            String name = invocation.getArgument(5);
            storedName.set(name);
            if ("partial-history".equals(outcome)) history(fixture, 11, owner, name);
            if (!"success".equals(outcome) && !"error".equals(outcome)) throw transportFailure;
            return new DialogReplyService.DialogMediaReplyResult("success".equals(outcome),
                    "error".equals(outcome) ? "transport_error" : null, "2026-10-13T11:00:00Z", 11L,
                    name, "document", "caption", "operator");
        });

        if ("success".equals(outcome) || "error".equals(outcome)) {
            var response = service.sendMediaReply(owner, file(), "caption", null, "operator", fixture.authentication());
            assertThat(response.get("success")).isEqualTo("success".equals(outcome));
            if ("error".equals(outcome)) assertThat(response.get("error")).isEqualTo("transport_error");
        } else {
            assertThatThrownBy(() -> service.sendMediaReply(owner, file(), "caption", null, "operator", fixture.authentication()))
                    .isSameAs(transportFailure);
        }

        assertThat(storedName.get()).isNotBlank();
        Path created = fixture.root().resolve(key(owner, storedName.get()));
        if ("error".equals(outcome) || "runtime".equals(outcome)) {
            assertThat(created).doesNotExist();
        } else {
            assertThat(Files.readAllBytes(created)).containsExactly(1, 2, 3);
        }
        verify(fixture.service(), never()).deleteTicketAttachment(anyString(), anyString());
        if (!"success".equals(outcome)) {
            verify(ai, never()).registerOperatorReply(anyString(), anyString(), anyString());
            verify(notifications, never()).notifyDialogParticipants(any(), any(), any(), any());
        }
    }

    @Test
    void receiptDoesNotChangeSerializedUploadMetadataContract() throws Exception {
        Fixture fixture = fixture();
        ticket(fixture, 1, "T");
        var upload = fixture.service().prepareTicketAttachment(fixture.authentication(), "T", file());
        var mapper = new ObjectMapper().findAndRegisterModules();
        var json = mapper.readTree(mapper.writeValueAsString(upload.metadata()));
        var fields = new HashSet<String>();
        json.fieldNames().forEachRemaining(fields::add);

        assertThat(fields).containsExactlyInAnyOrder("originalName", "storedName", "mimeType", "size", "uploadedAt");
        upload.retain();
    }
}
