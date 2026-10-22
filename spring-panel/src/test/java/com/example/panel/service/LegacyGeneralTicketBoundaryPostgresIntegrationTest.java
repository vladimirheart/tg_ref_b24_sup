package com.example.panel.service;

import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.storage.AttachmentService;
import com.example.panel.storage.TicketAttachmentOwnerJdbcGuard;
import com.example.panel.support.PanelTimestampSqlSupport;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LegacyGeneralTicketBoundaryPostgresIntegrationTest {
    @TempDir Path tempDir;
    private JdbcTemplate jdbc;
    private LegacyTicketIdJdbcGuard guard;

    @BeforeEach
    void setUp() {
        jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("legacy_general_boundary_r118");
        jdbc.execute("""
                CREATE TABLE tickets(user_id BIGINT,ticket_id TEXT,business_id BIGINT,
                    is_general BOOLEAN NOT NULL DEFAULT FALSE,PRIMARY KEY(user_id,ticket_id))
                """);
        jdbc.update("INSERT INTO tickets VALUES (42,'GENERAL',NULL,TRUE)");
        jdbc.execute("""
                CREATE TABLE ticket_responsibles(ticket_id TEXT PRIMARY KEY,responsible TEXT,
                    assigned_by TEXT,last_read_at TIMESTAMPTZ)
                """);
        jdbc.execute("""
                CREATE TABLE ticket_participants(ticket_id TEXT,username TEXT,added_by TEXT,
                    added_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(ticket_id,username))
                """);
        jdbc.execute("CREATE TABLE chat_history(ticket_id TEXT,timestamp TIMESTAMPTZ)");
        guard = new LegacyTicketIdJdbcGuard(jdbc);
    }

    @Test
    void directReadsRejectGeneralBeforeLoadingChildrenOrAttachmentMetadata() {
        JdbcTemplate children = mock(JdbcTemplate.class);
        JdbcTemplate users = mock(JdbcTemplate.class);
        AttachmentService attachments = mock(AttachmentService.class);
        AttachmentObjectStorageService storage = mock(AttachmentObjectStorageService.class);
        var lookup = new DialogLookupReadService(children, users, new PanelUserPhotoService(storage),
                new PanelTimestampSqlSupport(), guard);
        var conversation = new DialogConversationReadService(children, attachments,
                new PanelTimestampSqlSupport(), guard);
        var participants = new DialogParticipantService(children, users, guard);
        var context = new DialogClientContextReadService(children, new PanelTimestampSqlSupport(), guard);
        var responsibility = new DialogResponsibilityService(children, guard);

        assertThat(lookup.findDialog("GENERAL", "admin")).isEmpty();
        assertThat(lookup.resolveRequestNumber("GENERAL")).isNull();
        assertThat(conversation.loadHistory("GENERAL", null)).isEmpty();
        assertThat(conversation.loadPreviousDialogHistory("GENERAL", 0)).isEmpty();
        assertThat(conversation.loadTicketCategories("GENERAL")).isEmpty();
        assertThat(participants.loadParticipants("GENERAL")).isEmpty();
        assertThat(context.loadRelatedEvents("GENERAL", 20)).isEmpty();
        responsibility.markDialogAsRead("GENERAL", "admin");
        verifyNoInteractions(children, users, attachments, storage);
    }

    @Test
    void allFunctionalPermissionsCannotAuthorizeLegacyGeneralActions() {
        PermissionService permissions = mock(PermissionService.class);
        DialogAuditService audit = mock(DialogAuditService.class);
        Authentication actor = mock(Authentication.class);
        when(permissions.hasAuthority(eq(actor), anyString())).thenReturn(true);
        var authorization = new DialogAuthorizationService(permissions, audit, guard);

        for (String permission : new String[]{"can_reply", "can_assign", "can_close", "can_snooze", "can_bulk"}) {
            var response = authorization.requirePermission(actor, permission, "legacy_action", "GENERAL");
            assertThat(response).as(permission).isNotNull();
            assertThat(response.getStatusCode()).as(permission).isEqualTo(HttpStatus.NOT_FOUND);
        }
        verifyNoInteractions(audit);
    }

    @Test
    void everyLegacyDownloadFormRejectsGeneralBeforeBinaryIo() throws Exception {
        PermissionService permissions = mock(PermissionService.class);
        Authentication actor = mock(Authentication.class);
        when(permissions.hasAuthority(actor, "PAGE_DIALOGS")).thenReturn(true);
        AttachmentObjectStorageService storage = mock(AttachmentObjectStorageService.class);
        TicketAttachmentOwnerJdbcGuard owner = mock(TicketAttachmentOwnerJdbcGuard.class);
        when(owner.resolveUniqueOwner("GENERAL/file.png")).thenReturn("GENERAL");
        when(owner.hasDirectOwner("GENERAL", "file.png")).thenReturn(true);
        var attachments = new AttachmentService(permissions, storage, guard, owner,
                tempDir.resolve("attachments").toString(), tempDir.resolve("knowledge").toString());

        // Path and by-ticket controllers share this direct facade; Range must not bypass it.
        assertThat(attachments.downloadTicketAttachment(actor, "GENERAL", "file.png").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(attachments.downloadTicketAttachment(actor, "GENERAL", "file.png", "bytes=0-1").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(attachments.downloadTicketAttachmentByStorageKey(actor, "GENERAL/file.png", "bytes=0-1")
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(attachments.downloadTicketAttachmentByPath(actor, "C:\\legacy\\attachments\\GENERAL\\file.png")
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(storage);
    }

    @Test
    void readMarkAndParticipantsRecheckMarkerInTheirSqlSnapshot() {
        jdbc.update("UPDATE tickets SET is_general=FALSE");
        jdbc.update("""
                INSERT INTO ticket_responsibles VALUES ('GENERAL','operator','lead','2000-01-01T00:00:00Z')
                """);
        jdbc.update("INSERT INTO ticket_participants(ticket_id,username) VALUES ('GENERAL','operator')");
        var before = jdbc.queryForList("SELECT * FROM ticket_responsibles");
        var intercepted = spy(guard);
        doAnswer(call -> {
            boolean allowed = (boolean) call.callRealMethod();
            jdbc.update("UPDATE tickets SET is_general=TRUE");
            return allowed;
        }).when(intercepted).hasUniqueNonGeneralTicket("GENERAL");
        new DialogResponsibilityService(jdbc, intercepted).markDialogAsRead("GENERAL", "operator");
        assertThat(jdbc.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(before);
        jdbc.update("UPDATE tickets SET is_general=FALSE");
        JdbcTemplate users = mock(JdbcTemplate.class);
        assertThat(new DialogParticipantService(jdbc, users, intercepted).loadParticipants("GENERAL")).isEmpty();
        verifyNoInteractions(users);
    }

    @Test
    void bareIdAssignmentAndParticipantWritesCannotMutateGeneral() {
        jdbc.execute("ALTER TABLE tickets ADD COLUMN ticket_public_id TEXT DEFAULT '"+ "a".repeat(32)+"',"
            +"ADD COLUMN channel_id BIGINT,ADD COLUMN responsibility_revision BIGINT DEFAULT 0,ADD COLUMN status TEXT DEFAULT 'open'");
        jdbc.update("INSERT INTO ticket_responsibles(ticket_id,responsible) VALUES('GENERAL','untouched')");
        jdbc.update("INSERT INTO ticket_participants(ticket_id,username) VALUES('GENERAL','untouched')");
        var responsibilities=jdbc.queryForList("SELECT * FROM ticket_responsibles");
        var participants=jdbc.queryForList("SELECT * FROM ticket_participants");
        assertThat(guard.hasUniqueTicket("GENERAL")).isTrue();
        var responsibility = new DialogResponsibilityService(jdbc, guard);
        assertThat(responsibility.assignResponsibleIfMissing("GENERAL", "operator")).isNull();
        var service=new DialogParticipantService(jdbc,mock(JdbcTemplate.class),guard);
        assertThat(service.addParticipant("GENERAL","operator","bot")).isFalse();
        assertThat(service.removeParticipant("GENERAL","untouched")).isFalse();
        assertThat(jdbc.queryForList("SELECT * FROM ticket_responsibles")).isEqualTo(responsibilities);
        assertThat(jdbc.queryForList("SELECT * FROM ticket_participants")).isEqualTo(participants);
    }
}
