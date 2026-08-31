package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.storage.AttachmentObjectStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

class AvatarServiceTest {

    @Test
    void loadAvatarUsesResolvedClientAvatarVariantInsteadOfHardcodedJpgName() throws IOException {
        PermissionService permissionService = mock(PermissionService.class);
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        AttachmentObjectStorageService storage = mock(AttachmentObjectStorageService.class);
        PanelUserPhotoService panelUserPhotoService = mock(PanelUserPhotoService.class);
        Authentication authentication = mock(Authentication.class);
        AvatarService service = new AvatarService(
                permissionService,
                resourceLoader,
                storage,
                panelUserPhotoService
        );

        when(permissionService.hasAuthority(authentication, "PAGE_CLIENTS")).thenReturn(true);
        when(panelUserPhotoService.resolveClientAvatarStoredName(55L, false)).thenReturn("55.webp");
        when(storage.openAvatar("55.webp")).thenReturn(new AttachmentObjectStorageService.StoredBinary(
                "55.webp",
                "image/webp",
                4L,
                InputStream.nullInputStream()
        ));

        ResponseEntity<Resource> response = service.loadAvatar(authentication, 55L, false, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/webp");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(4L);
        verify(storage).openAvatar("55.webp");
    }

    @Test
    void loadAvatarFallsBackToAlternateSizeBeforeDefaultPlaceholder() throws IOException {
        PermissionService permissionService = mock(PermissionService.class);
        ResourceLoader resourceLoader = mock(ResourceLoader.class);
        AttachmentObjectStorageService storage = mock(AttachmentObjectStorageService.class);
        PanelUserPhotoService panelUserPhotoService = mock(PanelUserPhotoService.class);
        Authentication authentication = mock(Authentication.class);
        AvatarService service = new AvatarService(
                permissionService,
                resourceLoader,
                storage,
                panelUserPhotoService
        );

        when(permissionService.hasAuthority(authentication, "PAGE_CLIENTS")).thenReturn(true);
        when(panelUserPhotoService.resolveClientAvatarStoredName(77L, true)).thenReturn(null);
        when(panelUserPhotoService.resolveClientAvatarStoredName(77L, false)).thenReturn("77.png");
        when(storage.openAvatar("77.png")).thenReturn(new AttachmentObjectStorageService.StoredBinary(
                "77.png",
                "image/png",
                3L,
                new java.io.ByteArrayInputStream("png".getBytes(StandardCharsets.UTF_8))
        ));

        ResponseEntity<Resource> response = service.loadAvatar(authentication, 77L, true, true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(response.getBody()).isNotNull();
        assertThat(new String(response.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("png");
        verify(storage).openAvatar("77.png");
    }
}
