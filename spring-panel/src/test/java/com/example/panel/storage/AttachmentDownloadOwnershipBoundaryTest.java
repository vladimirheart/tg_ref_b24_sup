package com.example.panel.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.service.LegacyTicketIdJdbcGuard;
import com.example.panel.service.PermissionService;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.Authentication;
import org.springframework.jdbc.core.JdbcTemplate;

class AttachmentDownloadOwnershipBoundaryTest {
    @TempDir Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {" T /photo.jpg", "T/../U/photo.jpg", "T/./photo.jpg", "T//photo.jpg"})
    void storageKeyCannotAuthorizeAnAliasOrDifferentNormalizedOwner(String key) throws Exception {
        var permissions = mock(PermissionService.class);
        var storage = mock(AttachmentObjectStorageService.class);
        var tickets = mock(LegacyTicketIdJdbcGuard.class);
        var authentication = mock(Authentication.class);
        when(permissions.hasAuthority(authentication, "PAGE_DIALOGS")).thenReturn(true);
        when(tickets.hasUniqueTicket("T")).thenReturn(true);
        when(tickets.hasUniqueNonGeneralTicket("T")).thenReturn(true);
        when(storage.openDialogAttachmentByStorageKey(key)).thenReturn(binary(key));
        var jdbc = mock(JdbcTemplate.class);
        var service = new AttachmentService(permissions, storage, tickets, new TicketAttachmentOwnerJdbcGuard(jdbc, tickets),
                tempDir.resolve("attachments").toString(), tempDir.resolve("knowledge").toString());

        assertThat(service.downloadTicketAttachmentByStorageKey(authentication, key).getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(storage);
        verifyNoInteractions(jdbc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../U/photo.jpg", "folder/../../U/photo.jpg"})
    void directDownloadCannotUseFilenameTraversalToReadAnotherTicket(String filename) throws Exception {
        var permissions = mock(PermissionService.class);
        var storage = mock(AttachmentObjectStorageService.class);
        var tickets = mock(LegacyTicketIdJdbcGuard.class);
        var authentication = mock(Authentication.class);
        when(permissions.hasAuthority(authentication, "PAGE_DIALOGS")).thenReturn(true);
        when(tickets.hasUniqueTicket("T")).thenReturn(true);
        when(tickets.hasUniqueNonGeneralTicket("T")).thenReturn(true);
        when(storage.openDialogAttachment("T", filename)).thenReturn(binary(filename));
        var jdbc = mock(JdbcTemplate.class);
        var service = new AttachmentService(permissions, storage, tickets, new TicketAttachmentOwnerJdbcGuard(jdbc, tickets),
                tempDir.resolve("attachments").toString(), tempDir.resolve("knowledge").toString());

        assertThat(service.downloadTicketAttachment(authentication, "T", filename).getStatusCode().value()).isEqualTo(404);
        verifyNoInteractions(storage);
        verifyNoInteractions(jdbc);
    }

    @Test
    void missingPagePermissionStopsBeforeOwnerLookupAndBinaryStorage() throws Exception {
        var permissions = mock(PermissionService.class);
        var storage = mock(AttachmentObjectStorageService.class);
        var tickets = mock(LegacyTicketIdJdbcGuard.class);
        var jdbc = mock(JdbcTemplate.class);
        var authentication = mock(Authentication.class);
        var service = new AttachmentService(permissions, storage, tickets, new TicketAttachmentOwnerJdbcGuard(jdbc, tickets),
                tempDir.resolve("attachments").toString(), tempDir.resolve("knowledge").toString());

        assertThatThrownBy(() -> service.downloadTicketAttachmentByStorageKey(authentication, "T/photo.jpg"))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(jdbc, tickets, storage);
    }

    private AttachmentObjectStorageService.StoredBinary binary(String key) {
        return new AttachmentObjectStorageService.StoredBinary(key, "image/jpeg", 2, new ByteArrayInputStream(new byte[] {1, 2}));
    }
}
