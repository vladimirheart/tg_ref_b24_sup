package com.example.panel.service;

import java.io.IOException;
import com.example.panel.storage.AttachmentObjectStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AvatarService {

    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.parseMediaType("image/svg+xml");

    private final PermissionService permissionService;
    private final AttachmentObjectStorageService attachmentObjectStorageService;
    private final ResourceLoader resourceLoader;
    private final PanelUserPhotoService panelUserPhotoService;

    public AvatarService(PermissionService permissionService,
                         ResourceLoader resourceLoader,
                         AttachmentObjectStorageService attachmentObjectStorageService,
                         PanelUserPhotoService panelUserPhotoService) {
        this.permissionService = permissionService;
        this.resourceLoader = resourceLoader;
        this.attachmentObjectStorageService = attachmentObjectStorageService;
        this.panelUserPhotoService = panelUserPhotoService;
    }

    public ResponseEntity<Resource> loadAvatar(Authentication authentication,
                                               long userId,
                                               boolean full,
                                               boolean allowFallback) throws IOException {
        requireAuthority(authentication, "PAGE_CLIENTS");
        String primary = panelUserPhotoService.resolveClientAvatarStoredName(userId, full);
        String fallback = panelUserPhotoService.resolveClientAvatarStoredName(userId, !full);

        if (StringUtils.hasText(primary)) {
            return buildResponse(attachmentObjectStorageService.openAvatar(primary));
        }
        if (StringUtils.hasText(fallback) && !fallback.equals(primary)) {
            return buildResponse(attachmentObjectStorageService.openAvatar(fallback));
        }
        if (!allowFallback) {
            return ResponseEntity.notFound().build();
        }

        Resource defaultAvatar = resourceLoader.getResource("classpath:static/avatar_default.svg");
        return ResponseEntity.ok()
            .contentType(DEFAULT_MEDIA_TYPE)
            .body(defaultAvatar);
    }

    private ResponseEntity<Resource> buildResponse(AttachmentObjectStorageService.StoredBinary binary) {
        MediaType mediaType = StringUtils.hasText(binary.contentType())
                ? MediaType.parseMediaType(binary.contentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        InputStreamResource resource = new InputStreamResource(binary.inputStream());
        return ResponseEntity.ok()
            .contentType(mediaType)
            .contentLength(binary.size())
            .body(resource);
    }

    private void requireAuthority(Authentication authentication, String authority) {
        if (!permissionService.hasAuthority(authentication, authority)) {
            throw new SecurityException("Forbidden");
        }
    }
}
