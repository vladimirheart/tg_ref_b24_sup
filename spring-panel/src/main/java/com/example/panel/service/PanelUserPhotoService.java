package com.example.panel.service;

import com.example.panel.storage.AttachmentObjectStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PanelUserPhotoService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    private final AttachmentObjectStorageService attachmentObjectStorageService;
    private Path legacyLocalAvatarsRoot = Paths.get("attachments/avatars").toAbsolutePath().normalize();

    public PanelUserPhotoService(AttachmentObjectStorageService attachmentObjectStorageService) {
        this.attachmentObjectStorageService = attachmentObjectStorageService;
    }

    @Value("${app.storage.avatars:attachments/avatars}")
    void configureLegacyLocalAvatarsRoot(String avatarsDir) {
        if (StringUtils.hasText(avatarsDir)) {
            this.legacyLocalAvatarsRoot = Paths.get(avatarsDir).toAbsolutePath().normalize();
        }
    }

    public String resolveUrl(String photo) {
        return resolveUrl(photo, null);
    }

    public String resolveUrl(String photo, String fallbackUrl) {
        String resolved = resolveUrlInternal(photo);
        return StringUtils.hasText(resolved) ? resolved : fallbackUrl;
    }

    public String resolveUserAvatarUrl(Long userId, String photo, String fallbackUrl) {
        String resolved = resolveUrlInternal(photo);
        if (StringUtils.hasText(resolved)) {
            return resolved;
        }
        if (userId != null && userId > 0) {
            String byId = resolveAvatarUrl(userId, false);
            if (!StringUtils.hasText(byId)) {
                byId = resolveAvatarUrl(userId, true);
            }
            if (StringUtils.hasText(byId)) {
                return byId;
            }
        }
        return fallbackUrl;
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
        if (StringUtils.hasText(filename)
                && (avatarExists(filename) || migrateLegacyLocalAvatar(filename))) {
            return buildStoredAvatarUrl(filename);
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    private String resolveStoredAvatarPath(String rawFilename) {
        String filename = extractFilename(rawFilename);
        if (!StringUtils.hasText(filename)) {
            return null;
        }
        if (!avatarExists(filename) && !migrateLegacyLocalAvatar(filename)) {
            return null;
        }
        return buildStoredAvatarUrl(filename);
    }

    private String buildStoredAvatarUrl(String filename) {
        return "/api/attachments/avatars/" + filename;
    }

    private boolean avatarExists(String filename) {
        return attachmentObjectStorageService.avatarExists(filename);
    }

    private boolean migrateLegacyLocalAvatar(String filename) {
        if (!StringUtils.hasText(filename)) {
            return false;
        }
        Path source = legacyLocalAvatarsRoot.resolve(filename).normalize();
        if (!source.startsWith(legacyLocalAvatarsRoot) || !Files.isRegularFile(source)) {
            return false;
        }
        try (InputStream inputStream = Files.newInputStream(source)) {
            attachmentObjectStorageService
                    .storeAvatar(filename, Files.probeContentType(source), inputStream)
                    .close();
            return avatarExists(filename);
        } catch (IOException | RuntimeException ex) {
            return false;
        }
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

    public StoredAvatar storeUploadedAvatar(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не может быть пустым");
        }
        String extension = extractExtension(file.getOriginalFilename());
        if (!isAllowedImageExtension(extension)) {
            throw new IllegalArgumentException("Поддерживаются изображения PNG, JPG, GIF или WebP.");
        }
        String storedName = System.currentTimeMillis() + "_" + UUID.randomUUID() + extension;
        try (InputStream inputStream = file.getInputStream()) {
            attachmentObjectStorageService.storeAvatar(storedName, file.getContentType(), inputStream).close();
        }
        return new StoredAvatar(storedName, buildStoredAvatarUrl(storedName));
    }

    public String storeNamedAvatar(String storedName, String contentType, byte[] data) throws IOException {
        if (!StringUtils.hasText(storedName) || data == null || data.length == 0) {
            return null;
        }
        try (InputStream inputStream = new java.io.ByteArrayInputStream(data)) {
            attachmentObjectStorageService.storeAvatar(storedName, contentType, inputStream).close();
        }
        return buildStoredAvatarUrl(storedName);
    }

    public String avatarFileName(long userId, boolean full) {
        return userId + (full ? "_full" : "") + ".jpg";
    }

    public boolean avatarExists(long userId, boolean full) {
        return attachmentObjectStorageService.avatarExists(avatarFileName(userId, full));
    }

    public String resolveAvatarUrl(long userId, boolean full) {
        String storedName = avatarFileName(userId, full);
        if (!avatarExists(storedName) && !migrateLegacyLocalAvatar(storedName)) {
            return null;
        }
        return buildStoredAvatarUrl(storedName);
    }

    private String extractExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        if (idx == -1) {
            return "";
        }
        return filename.substring(idx).toLowerCase(Locale.ROOT);
    }

    private boolean isAllowedImageExtension(String extension) {
        return ALLOWED_EXTENSIONS.contains(extension);
    }

    public record StoredAvatar(String storedName, String url) {
    }
}
