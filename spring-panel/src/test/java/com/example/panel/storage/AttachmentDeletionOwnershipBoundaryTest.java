package com.example.panel.storage;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.service.LegacyTicketIdJdbcGuard;
import com.example.panel.service.PermissionService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class AttachmentDeletionOwnershipBoundaryTest {
    @TempDir Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {"T", " T ", "T/nested", "T%2F+"})
    void uniqueRequestedTicketAloneCannotAuthorizeDeletion(String ticket) throws Exception {
        var storage = mock(AttachmentObjectStorageService.class);
        var tickets = mock(LegacyTicketIdJdbcGuard.class);
        when(tickets.hasUniqueTicket(ticket)).thenReturn(true);
        var service = service(storage, tickets, mock(TicketAttachmentOwnerJdbcGuard.class));

        service.deleteTicketAttachment(ticket, "private.jpg");
        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../U/private.jpg", "folder/../../U/private.jpg", "./private.jpg",
            "folder//private.jpg", "folder/../private.jpg"})
    void filenameTraversalOrAliasNeverReachesDeletionStorage(String filename) throws Exception {
        var storage = mock(AttachmentObjectStorageService.class);
        var tickets = mock(LegacyTicketIdJdbcGuard.class);
        var jdbc = mock(JdbcTemplate.class);
        when(tickets.hasUniqueTicket("T")).thenReturn(true);
        var service = service(storage, tickets, new TicketAttachmentOwnerJdbcGuard(jdbc, tickets));

        service.deleteTicketAttachment("T", filename);
        verifyNoInteractions(storage, jdbc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"T/../U", "T/./U", " T /nested", "T//nested"})
    void unsafeTicketDirectoryNeverReachesDeletionStorage(String ticket) throws Exception {
        var storage = mock(AttachmentObjectStorageService.class);
        var tickets = mock(LegacyTicketIdJdbcGuard.class);
        var jdbc = mock(JdbcTemplate.class);
        when(tickets.hasUniqueTicket(ticket)).thenReturn(true);
        var service = service(storage, tickets, new TicketAttachmentOwnerJdbcGuard(jdbc, tickets));

        service.deleteTicketAttachment(ticket, "private.jpg");
        verifyNoInteractions(storage, jdbc);
    }

    @Test
    void duplicateRequestedTicketStopsBeforeOwnerLookupAndDeletion() throws Exception {
        var storage = mock(AttachmentObjectStorageService.class);
        var tickets = mock(LegacyTicketIdJdbcGuard.class);
        var owner = mock(TicketAttachmentOwnerJdbcGuard.class);
        var service = service(storage, tickets, owner);

        service.deleteTicketAttachment("T", "private.jpg");
        verifyNoInteractions(storage, owner);
    }

    private AttachmentService service(AttachmentObjectStorageService storage,
                                      LegacyTicketIdJdbcGuard tickets, TicketAttachmentOwnerJdbcGuard owner) throws Exception {
        return new AttachmentService(mock(PermissionService.class), storage, tickets, owner,
                tempDir.resolve("attachments").toString(), tempDir.resolve("knowledge").toString(), com.example.panel.support.AttachmentReadAccessStub.permitted());
    }
}
