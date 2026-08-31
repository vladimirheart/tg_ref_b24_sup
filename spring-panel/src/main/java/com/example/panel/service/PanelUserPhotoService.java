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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PanelUserPhotoService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    private final AttachmentObjectStorageService attachmentObjectStorageService;
    private List<Path> legacyLocalAvatarRoots = List.of(
            Paths.get("attachments/avatars").toAbsolutePath().normalize()
    );

    public PanelUserPhotoService(AttachmentObjectStorageService attachmentObjectStorageService) {
        this.attachmentObjectStorageService = attachmentObjectStorageService;
    }

    @Value("${app.storage.avatars:attachments/avatars}")
    void configureLegacyLocalAvatarsRoot(String avatarsDir) {
        Path workingDirectory = Paths.get("").toAbsolutePath().normalize();
        Path workspaceRoot = locateWorkspaceRoot(workingDirectory);
        LinkedHashSet<Path> roots = new LinkedHashSet<>();

        addLegacyAvatarRoot(roots, avatarsDir, workingDirectory);

        // Historical panel-user uploads used "attachments/avatars" while the
        // launcher working directory was spring-panel.
        roots.add(workingDirectory.resolve("attachments/avatars").normalize());
        roots.add(workspaceRoot.resolve("spring-panel/attachments/avatars").normalize());

        // Current canonical local path.
        roots.add(workspaceRoot.resolve("attachments/avatars").normalize());

        // Very old users.photo values may still point at static/user_photos.
        roots.add(workspaceRoot.resolve("spring-panel/src/main/resources/static/user_photos").normalize());

        this.legacyLocalAvatarRoots = List.copyOf(roots);
    }

    private void addLegacyAvatarRoot(Set<Path> roots, String configuredPath, Path workingDirectory) {
        if (!StringUtils.hasText(configuredPath)) {
            return;
        }
        Path configured = Paths.get(configuredPath.trim());
        Path resolved = configured.isAbsolute()
                ? configured.normalize()
                : workingDirectory.resolve(configured).normalize();
        roots.add(resolved);
    }

    private Path locateWorkspaceRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.isDirectory(current.resolve(".git"))) {
                return current;
            }
            current = current.getParent();
        }
        return start;
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
        if (StringUtils.hasText(resolved) && !"/avatar_default.svg".equals(resolved)) {
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

        // A stale /static/user_photos/... value must not block the user-id
        // fallback in resolveUserAvatarUrl().
        return null;
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
        if (!attachmentObjectStorageService.isLegacyLocalFallbackEnabled()) {
            return false;
        }

        for (Path root : legacyLocalAvatarRoots) {
            if (root == null) {
                continue;
            }
            Path source = root.resolve(filename).normalize();
            if (!source.startsWith(root) || !Files.isRegularFile(source)) {
                continue;
            }
            try (InputStream inputStream = Files.newInputStream(source)) {
                attachmentObjectStorageService
                        .storeAvatar(filename, Files.probeContentType(source), inputStream)
                        .close();
                if (avatarExists(filename)) {
                    return true;
                }
            } catch (IOException | RuntimeException ignored) {
                // Try the next historical location.
            }
        }
        return false;
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
        return storeUploadedAvatar(null, file);
    }

    public StoredAvatar storeUploadedAvatar(Long userId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не может быть пустым");
        }
        String extension = extractExtension(file.getOriginalFilename());
        if (!isAllowedImageExtension(extension)) {
            throw new IllegalArgumentException("Поддерживаются изображения PNG, JPG, GIF или WebP.");
        }

        String storedName = userId != null && userId > 0
                ? userAvatarFileName(userId, extension)
                : System.currentTimeMillis() + "_" + UUID.randomUUID() + extension;

        try (InputStream inputStream = file.getInputStream()) {
            attachmentObjectStorageService.storeAvatar(storedName, file.getContentType(), inputStream).close();
        }

        if (userId != null && userId > 0) {
            removeObsoleteUserAvatarVariants(userId, storedName);
        }

        return new StoredAvatar(storedName, buildStoredAvatarUrl(storedName));
    }

    private String userAvatarFileName(long userId, String extension) {
        return "panel-user-" + userId + extension;
    }

    private void removeObsoleteUserAvatarVariants(long userId, String keepStoredName) {
        for (String extension : ALLOWED_EXTENSIONS) {
            String candidate = userAvatarFileName(userId, extension);
            if (candidate.equals(keepStoredName)) {
                continue;
            }
            try {
                if (attachmentObjectStorageService.avatarExists(candidate)) {
                    attachmentObjectStorageService.deleteAvatar(candidate);
                }
            } catch (IOException | RuntimeException ignored) {
                // A stale variant must never make a successful upload fail.
            }
        }
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

    public List<String> clientAvatarFileNames(long userId, boolean full) {
        java.util.ArrayList<String> candidates = new java.util.ArrayList<>();
        String base = userId + (full ? "_full" : "");
        for (String extension : ALLOWED_EXTENSIONS) {
            candidates.add(base + extension);
        }
        return List.copyOf(candidates);
    }

    public String resolveClientAvatarStoredName(long userId, boolean full) {
        for (String storedName : clientAvatarFileNames(userId, full)) {
            if (avatarExists(storedName) || migrateLegacyLocalAvatar(storedName)) {
                return storedName;
            }
        }
        return null;
    }

    public boolean avatarExists(long userId, boolean full) {
        return StringUtils.hasText(resolveAvatarUrl(userId, full));
    }

    public String resolveAvatarUrl(long userId, boolean full) {
        for (String storedName : userAvatarCandidates(userId, full)) {
            if (avatarExists(storedName) || migrateLegacyLocalAvatar(storedName)) {
                return buildStoredAvatarUrl(storedName);
            }
        }
        return null;
    }

    private List<String> userAvatarCandidates(long userId, boolean full) {
        java.util.ArrayList<String> candidates = new java.util.ArrayList<>();

        // New canonical panel-user name.
        for (String extension : ALLOWED_EXTENSIONS) {
            candidates.add(userAvatarFileName(userId, extension));
        }

        // Historical id.jpg / id_full.jpg naming, now with every accepted extension.
        candidates.addAll(clientAvatarFileNames(userId, full));

        return candidates;
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

    public void deleteUserAvatarFiles(long userId) {
        if (userId <= 0) {
            return;
        }
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.addAll(userAvatarCandidates(userId, false));
        candidates.addAll(userAvatarCandidates(userId, true));
        for (String candidate : candidates) {
            try {
                if (attachmentObjectStorageService.avatarExists(candidate)) {
                    attachmentObjectStorageService.deleteAvatar(candidate);
                }
            } catch (IOException | RuntimeException ignored) {
                // Best-effort cleanup; DB photo removal remains authoritative.
            }
        }
    }
    public record StoredAvatar(String storedName, String url) {
    }
}
