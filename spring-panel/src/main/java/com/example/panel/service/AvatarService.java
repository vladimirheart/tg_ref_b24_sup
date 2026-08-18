package com.example.panel.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AvatarService {

    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.parseMediaType("image/svg+xml");

    private final PermissionService permissionService;
    private final Path avatarsRoot;
    private final ResourceLoader resourceLoader;

    public AvatarService(PermissionService permissionService,
                         ResourceLoader resourceLoader,
                         @Value("${app.storage.avatars:attachments/avatars}") String avatarsDir) throws IOException {
        this.permissionService = permissionService;
        this.resourceLoader = resourceLoader;
        this.avatarsRoot = ensureDirectory(resolveStoragePath(avatarsDir));
    }

    public ResponseEntity<Resource> loadAvatar(Authentication authentication,
                                               long userId,
                                               boolean full,
                                               boolean allowFallback) throws IOException {
        requireAuthority(authentication, "PAGE_CLIENTS");
        Path primary = resolveAvatarPath(userId, full);
        Path fallback = resolveAvatarPath(userId, !full);

        if (Files.isRegularFile(primary)) {
            return buildResponse(primary);
        }
        if (Files.isRegularFile(fallback)) {
            return buildResponse(fallback);
        }
        if (!allowFallback) {
            return ResponseEntity.notFound().build();
        }

        Resource defaultAvatar = resourceLoader.getResource("classpath:static/avatar_default.svg");
        return ResponseEntity.ok()
            .contentType(DEFAULT_MEDIA_TYPE)
            .body(defaultAvatar);
    }

    private Path resolveAvatarPath(long userId, boolean full) {
        String suffix = full ? "_full" : "";
        String filename = userId + suffix + ".jpg";
        return avatarsRoot.resolve(filename).normalize();
    }

    private ResponseEntity<Resource> buildResponse(Path file) throws IOException {
        MediaType mediaType = MediaTypeFactory.detect(file);
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(file));
        return ResponseEntity.ok()
            .contentType(mediaType)
            .contentLength(Files.size(file))
            .body(resource);
    }

    private Path resolveStoragePath(String directory) {
        Path configured = Paths.get(directory.trim());
        if (configured.isAbsolute()) {
            return configured.normalize();
        }

        Path workingDirectory = Paths.get("").toAbsolutePath().normalize();
        // Explicit ../ paths are intentionally relative to spring-panel and
        // already point at the repository root in the default configuration.
        if (configured.startsWith("..")) {
            return workingDirectory.resolve(configured).normalize();
        }

        Path workspaceRoot = locateWorkspaceRoot(workingDirectory);
        if (!workspaceRoot.equals(workingDirectory)) {
            return workspaceRoot.resolve(configured).normalize();
        }
        return workingDirectory.resolve(configured).normalize();
    }

    private Path locateWorkspaceRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.isDirectory(current.resolve(".git")) || Files.isDirectory(current.resolve("spring-panel"))) {
                return current;
            }
            current = current.getParent();
        }
        return start;
    }

    private Path ensureDirectory(Path path) throws IOException {
        Files.createDirectories(path);
        return path.toAbsolutePath().normalize();
    }

    private void requireAuthority(Authentication authentication, String authority) {
        if (!permissionService.hasAuthority(authentication, authority)) {
            throw new SecurityException("Forbidden");
        }
    }

    private static final class MediaTypeFactory {
        private static MediaType detect(Path file) throws IOException {
            String probe = Files.probeContentType(file);
            return probe != null ? MediaType.parseMediaType(probe) : MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
