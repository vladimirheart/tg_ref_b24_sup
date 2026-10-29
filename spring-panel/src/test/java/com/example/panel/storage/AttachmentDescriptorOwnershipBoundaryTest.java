package com.example.panel.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.service.LegacyTicketIdJdbcGuard;
import com.example.panel.service.PermissionService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

class AttachmentDescriptorOwnershipBoundaryTest {
    @TempDir Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {"direct", "key", "path"})
    void allowedDescriptorClosesBinaryWithoutReadingContent(String probe) throws Exception {
        var storage = mock(AttachmentObjectStorageService.class);
        var tickets = mock(LegacyTicketIdJdbcGuard.class);
        var owner = mock(TicketAttachmentOwnerJdbcGuard.class);
        var input = mock(InputStream.class);
        when(tickets.hasUniqueTicket("T")).thenReturn(true);
        when(owner.hasDirectOwner("T", "report.pdf")).thenReturn(true);
        when(owner.resolveUniqueOwner("T/report.pdf")).thenReturn("T");
        when(storage.openDialogAttachment("T", "report.pdf")).thenReturn(
                new AttachmentObjectStorageService.StoredBinary("T/report.pdf", "application/pdf", 123, input));
        when(storage.openDialogAttachmentByStorageKey("T/report.pdf")).thenReturn(
                new AttachmentObjectStorageService.StoredBinary("T/report.pdf", "application/pdf", 123, input));
        var service = service(storage, tickets, owner);

        var descriptor = switch (probe) {
            case "direct" -> service.describeTicketAttachment("T", "report.pdf");
            case "key" -> service.describeTicketAttachmentByStorageKey("T", "T/report.pdf");
            case "path" -> service.describeTicketAttachmentByPath("T", "attachments/T/report.pdf");
            default -> throw new AssertionError(probe);
        };

        assertThat(descriptor.originalName()).isEqualTo("report.pdf");
        assertThat(descriptor.size()).isEqualTo(123);
        verify(input).close();
        org.mockito.Mockito.verifyNoMoreInteractions(input);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void missingTicketContextCannotBecomeAnUnboundDescriptorLookup(String ticketId) throws Exception {
        var storage = mock(AttachmentObjectStorageService.class);
        var tickets = mock(LegacyTicketIdJdbcGuard.class);
        var owner = mock(TicketAttachmentOwnerJdbcGuard.class);
        var service = service(storage, tickets, owner);

        assertThatThrownBy(() -> service.describeTicketAttachmentByStorageKey(ticketId, "T/report.pdf"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("File not found");
        assertThatThrownBy(() -> service.describeTicketAttachmentByPath(ticketId, "attachments/T/report.pdf"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("File not found");
        verifyNoInteractions(storage, tickets, owner);
    }

    @Test
    void failedStorageExistenceProbeStaysFalseAfterSuccessfulOwnerLookup() throws Exception {
        var storage = mock(AttachmentObjectStorageService.class);
        var owner = mock(TicketAttachmentOwnerJdbcGuard.class);
        when(owner.resolveUniqueOwner("T/report.pdf")).thenReturn("T");
        when(storage.dialogAttachmentExistsByStorageKey("T/report.pdf")).thenThrow(new IllegalStateException("offline"));
        var service = service(storage, mock(LegacyTicketIdJdbcGuard.class), owner);

        assertThat(service.hasTicketAttachmentByStorageKey("T/report.pdf")).isFalse();
        verify(storage).dialogAttachmentExistsByStorageKey("T/report.pdf");
    }

    @ParameterizedTest
    @ValueSource(strings = {"direct", "key", "path", "exists"})
    void unknownOwnerCannotExposeNameSizeOrExistence(String probe) throws Exception {
        var storage = storage();
        var tickets = mock(LegacyTicketIdJdbcGuard.class);
        when(tickets.hasUniqueTicket("T")).thenReturn(true);
        var owner = mock(TicketAttachmentOwnerJdbcGuard.class);
        var service = service(storage, tickets, owner);

        denied(service, probe, "T/private.jpg");
        verifyNoInteractions(storage);
    }

    @ParameterizedTest
    @MethodSource("unsafeProbes")
    void unsafeReferenceStopsBeforeOwnerJdbcAndStorage(String probe, String key) throws Exception {
        var storage = storage();
        var tickets = mock(LegacyTicketIdJdbcGuard.class);
        when(tickets.hasUniqueTicket("T")).thenReturn(true);
        var jdbc = mock(JdbcTemplate.class);
        var service = service(storage, tickets, new TicketAttachmentOwnerJdbcGuard(jdbc, tickets));

        denied(service, probe, key);
        verifyNoInteractions(storage, jdbc);
    }

    static Stream<Arguments> unsafeProbes() {
        return Stream.of(" T /private.jpg", "T/../U/private.jpg", "T/./private.jpg", "T//private.jpg")
                .flatMap(key -> Stream.of("key", "path", "exists").map(probe -> Arguments.of(probe, key)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../U/private.jpg", "folder/../../U/private.jpg"})
    void directDescriptorCannotTraverseIntoAnotherTicket(String filename) throws Exception {
        var storage = storage();
        var tickets = mock(LegacyTicketIdJdbcGuard.class);
        when(tickets.hasUniqueTicket("T")).thenReturn(true);
        var jdbc = mock(JdbcTemplate.class);
        var service = service(storage, tickets, new TicketAttachmentOwnerJdbcGuard(jdbc, tickets));

        assertThatThrownBy(() -> service.describeTicketAttachment("T", filename))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("File not found");
        verifyNoInteractions(storage, jdbc);
    }

    private void denied(AttachmentService service, String probe, String key) {
        if ("exists".equals(probe)) {
            assertThat(service.hasTicketAttachmentByStorageKey(key)).isFalse();
            return;
        }
        assertThatThrownBy(() -> {
            switch (probe) {
                case "direct" -> service.describeTicketAttachment("T", "private.jpg");
                case "key" -> service.describeTicketAttachmentByStorageKey(key);
                case "path" -> service.describeTicketAttachmentByPath("attachments/" + key);
                default -> throw new AssertionError(probe);
            }
        }).isInstanceOf(IllegalArgumentException.class).hasMessage("File not found");
    }

    private AttachmentService service(AttachmentObjectStorageService storage,
                                      LegacyTicketIdJdbcGuard tickets, TicketAttachmentOwnerJdbcGuard owner) throws Exception {
        return new AttachmentService(mock(PermissionService.class), storage, tickets, owner,
                tempDir.resolve("attachments").toString(), tempDir.resolve("knowledge").toString(), com.example.panel.support.AttachmentReadAccessStub.permitted());
    }

    private AttachmentObjectStorageService storage() throws Exception {
        var storage = mock(AttachmentObjectStorageService.class);
        when(storage.openDialogAttachment(anyString(), anyString())).thenAnswer(invocation -> binary());
        when(storage.openDialogAttachmentByStorageKey(anyString())).thenAnswer(invocation -> binary());
        when(storage.dialogAttachmentExistsByStorageKey(anyString())).thenReturn(true);
        return storage;
    }

    private AttachmentObjectStorageService.StoredBinary binary() {
        return new AttachmentObjectStorageService.StoredBinary("private.jpg", "image/jpeg", 4096,
                new ByteArrayInputStream(new byte[] {1, 2}));
    }
}
