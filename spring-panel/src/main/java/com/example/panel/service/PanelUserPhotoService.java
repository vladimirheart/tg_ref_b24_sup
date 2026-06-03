package com.example.panel.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class PanelUserPhotoService {

    private final Path avatarsRoot;

    public PanelUserPhotoService(@Value("${app.storage.avatars:attachments/avatars}") String avatarsDir) throws IOException {
        this.avatarsRoot = ensureDirectory(avatarsDir);
    }

    public String resolveUrl(String photo) {
        return resolveUrl(photo, null);
    }

    public String resolveUrl(String photo, String fallbackUrl) {
        String resolved = resolveUrlInternal(photo);
        return StringUtils.hasText(resolved) ? resolved : fallbackUrl;
    }

    private String resolveUrlInternal(String photo) {
        if (!StringUtils.hasText(photo)) {
            return null;
        }
        String trimmed = photo.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("data:")) {
            return trimmed;
        }
        if (isLegacyUserPhotoPath(trimmed)) {
            return resolveLegacyUserPhotoPath(trimmed);
        }
        if (trimmed.startsWith("/api/attachments/avatars/")) {
            return resolveStoredAvatarPath(trimmed.substring("/api/attachments/avatars/".length()));
        }
        if (trimmed.startsWith("api/attachments/avatars/")) {
            return resolveStoredAvatarPath(trimmed.substring("api/attachments/avatars/".length()));
        }
        if (trimmed.startsWith("/avatars/")) {
            return resolveStoredAvatarPath(trimmed.substring("/avatars/".length()));
        }
        if (trimmed.startsWith("avatars/")) {
            return resolveStoredAvatarPath(trimmed.substring("avatars/".length()));
        }
        if (trimmed.startsWith("/")) {
            return trimmed;
        }
        return resolveStoredAvatarPath(trimmed);
    }

    private boolean isLegacyUserPhotoPath(String value) {
        return value.startsWith("/static/user_photos/")
                || value.startsWith("static/user_photos/")
                || value.startsWith("/user_photos/")
                || value.startsWith("user_photos/");
    }

    private String resolveLegacyUserPhotoPath(String value) {
        String filename = extractFilename(value);
        if (StringUtils.hasText(filename) && avatarExists(filename)) {
            return buildStoredAvatarUrl(filename);
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    private String resolveStoredAvatarPath(String rawFilename) {
        String filename = extractFilename(rawFilename);
        if (!StringUtils.hasText(filename) || !avatarExists(filename)) {
            return null;
        }
        return buildStoredAvatarUrl(filename);
    }

    private String buildStoredAvatarUrl(String filename) {
        return "/api/attachments/avatars/" + filename;
    }

    private boolean avatarExists(String filename) {
        Path resolved = avatarsRoot.resolve(filename).normalize();
        return resolved.startsWith(avatarsRoot) && Files.isRegularFile(resolved);
    }

    private String extractFilename(String rawValue) {
        String cleaned = StringUtils.cleanPath(StringUtils.hasText(rawValue) ? rawValue.trim() : "");
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        int slashIndex = cleaned.lastIndexOf('/');
        String filename = slashIndex >= 0 ? cleaned.substring(slashIndex + 1) : cleaned;
        if (!StringUtils.hasText(filename) || filename.contains("..") || ".".equals(filename)) {
            return null;
        }
        return filename;
    }

    private Path ensureDirectory(String directory) throws IOException {
        Path path = Paths.get(directory).toAbsolutePath().normalize();
        Files.createDirectories(path);
        return path;
    }
}
