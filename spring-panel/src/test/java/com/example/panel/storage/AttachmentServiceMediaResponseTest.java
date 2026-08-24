package com.example.panel.storage;

import com.example.panel.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttachmentServiceMediaResponseTest {

    @TempDir
    Path tempDir;

    @Test
    void storageKeyVideoSupportsByteRangeAndDeterministicMimeType() throws Exception {
        PermissionService permissions = mock(PermissionService.class);
        AttachmentObjectStorageService storage = mock(AttachmentObjectStorageService.class);
        Authentication authentication = mock(Authentication.class);
        when(permissions.hasAuthority(authentication, "PAGE_DIALOGS")).thenReturn(true);
        byte[] payload = new byte[] {0, 1, 2, 3, 4, 5};
        when(storage.openDialogAttachmentByStorageKey("ticket/media.mp4"))
                .thenReturn(new AttachmentObjectStorageService.StoredBinary(
                        "ticket/media.mp4",
                        "application/octet-stream",
                        payload.length,
                        new ByteArrayInputStream(payload)
                ));

        AttachmentService service = new AttachmentService(
                permissions,
                storage,
                tempDir.resolve("attachments").toString(),
                tempDir.resolve("knowledge").toString()
        );

        ResponseEntity<Resource> response = service.downloadTicketAttachmentByStorageKey(
                authentication,
                "ticket/media.mp4",
                "bytes=1-3"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 1-3/6");
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("video/mp4");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(3);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getInputStream().readAllBytes()).containsExactly(1, 2, 3);
    }

    @Test
    void storageKeyVoiceUsesAudioOggMimeInsteadOfOctetStream() throws Exception {
        PermissionService permissions = mock(PermissionService.class);
        AttachmentObjectStorageService storage = mock(AttachmentObjectStorageService.class);
        Authentication authentication = mock(Authentication.class);
        when(permissions.hasAuthority(authentication, "PAGE_DIALOGS")).thenReturn(true);
        when(storage.openDialogAttachmentByStorageKey("ticket/voice.ogg"))
                .thenReturn(new AttachmentObjectStorageService.StoredBinary(
                        "ticket/voice.ogg",
                        "application/octet-stream",
                        2,
                        new ByteArrayInputStream(new byte[] {7, 8})
                ));

        AttachmentService service = new AttachmentService(
                permissions,
                storage,
                tempDir.resolve("attachments-2").toString(),
                tempDir.resolve("knowledge-2").toString()
        );

        ResponseEntity<Resource> response = service.downloadTicketAttachmentByStorageKey(
                authentication,
                "ticket/voice.ogg",
                null
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("audio/ogg");
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
    }
}
