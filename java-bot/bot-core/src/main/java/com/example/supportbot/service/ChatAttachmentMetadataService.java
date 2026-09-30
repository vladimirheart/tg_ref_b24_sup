package com.example.supportbot.service;

import com.example.supportbot.config.ObjectStorageProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Locale;

@Service
public class ChatAttachmentMetadataService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectStorageProperties objectStorageProperties;

    public ChatAttachmentMetadataService(JdbcTemplate jdbcTemplate,
                                         ObjectStorageProperties objectStorageProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectStorageProperties = objectStorageProperties;
    }

    public void upsertForChatHistory(Long chatHistoryId,
                                     String ticketId,
                                     Long channelId,
                                     String rawAttachment,
                                     String originalName,
                                     String messageType) {
        if (chatHistoryId == null || !StringUtils.hasText(rawAttachment)) {
            return;
        }
        String storageProvider = resolveStorageProvider(rawAttachment);
        String storageKey = normalizeStorageKey(ticketId, rawAttachment);
        Path resolvedPath = resolveLocalPath(rawAttachment);
        Long size = resolveSize(resolvedPath);
        String resolvedOriginalName = resolveOriginalName(originalName, rawAttachment, storageKey);
        String mimeType = resolveMimeType(resolvedPath, resolvedOriginalName, storageKey, messageType);
        String normalizationStatus = "external_url".equals(storageProvider) || StringUtils.hasText(storageKey)
                ? "normalized"
                : "unresolved";
        String availabilityStatus = resolveAvailabilityStatus(storageProvider, storageKey, resolvedPath);
        String timestamp = OffsetDateTime.now().toString();

        jdbcTemplate.update("DELETE FROM chat_attachment_metadata WHERE chat_history_id = ?", chatHistoryId);
        jdbcTemplate.update("""
                INSERT INTO chat_attachment_metadata (
                    chat_history_id,
                    ticket_id,
                    channel_id,
                    storage_key,
                    storage_provider,
                    storage_class,
                    original_name,
                    mime_type,
                    size,
                    content_hash,
                    legacy_attachment_ref,
                    normalization_status,
                    availability_status,
                    created_at,
                    updated_at,
                    archived_at,
                    deleted_at
                ) VALUES (?, ?, ?, ?, ?, 'dialog_attachment', ?, ?, ?, NULL, ?, ?, ?, ?, ?, NULL, NULL)
                """,
                chatHistoryId,
                trim(ticketId),
                channelId,
                trim(storageKey),
                storageProvider,
                trim(resolvedOriginalName),
                trim(mimeType),
                size,
                trim(rawAttachment),
                normalizationStatus,
                availabilityStatus,
                timestamp,
                timestamp
        );
    }


    private String normalizeStorageKey(String ticketId, String rawAttachment) {
        String normalized = normalizeReference(rawAttachment);
        if (!StringUtils.hasText(normalized)
                || isExternalUrl(normalized)
                || normalized.startsWith("api/")
                || normalized.startsWith("/api/")) {
            return null;
        }
        String suffix = extractAttachmentsSuffix(normalized);
        if (StringUtils.hasText(suffix)) {
            return suffix;
        }
        if (normalized.contains("/")) {
            return normalized;
        }
        if (StringUtils.hasText(ticketId)) {
            return normalizeReference(ticketId) + "/" + normalized;
        }
        return null;
    }

    private String extractAttachmentsSuffix(String raw) {
        String normalized = normalizeReference(raw);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String lowered = normalized.toLowerCase(Locale.ROOT);
        String marker = "/attachments/";
        int markerIndex = lowered.indexOf(marker);
        if (markerIndex >= 0) {
            return normalizeReference(normalized.substring(markerIndex + marker.length()));
        }
        if (lowered.startsWith("attachments/")) {
            return normalizeReference(normalized.substring("attachments/".length()));
        }
        return null;
    }

    private String resolveOriginalName(String preferredName, String rawAttachment, String storageKey) {
        if (StringUtils.hasText(preferredName)) {
            return preferredName.trim();
        }
        String candidate = extractFileName(StringUtils.hasText(storageKey) ? storageKey : rawAttachment);
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String normalized = candidate.trim();
        int separatorIndex = normalized.indexOf('_');
        if (separatorIndex > 0) {
            String prefix = normalized.substring(0, separatorIndex);
            if (prefix.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                return normalized.substring(separatorIndex + 1).trim();
            }
        }
        return normalized;
    }

    private String resolveMimeType(Path resolvedPath, String originalName, String storageKey, String messageType) {
        try {
            if (resolvedPath != null && Files.exists(resolvedPath)) {
                String detected = Files.probeContentType(resolvedPath);
                if (StringUtils.hasText(detected)) {
                    return detected;
                }
            }
        } catch (Exception ignored) {
        }
        String guessedByName = URLConnection.guessContentTypeFromName(firstNonBlank(originalName, extractFileName(storageKey)));
        if (StringUtils.hasText(guessedByName)) {
            return guessedByName;
        }
        String normalizedType = StringUtils.hasText(messageType) ? messageType.trim().toLowerCase(Locale.ROOT) : "";
        return switch (normalizedType) {
            case "photo", "image" -> "image/*";
            case "video", "video_note" -> "video/*";
            case "voice", "audio" -> "audio/*";
            case "document", "file" -> "application/octet-stream";
            default -> null;
        };
    }

    private Long resolveSize(Path resolvedPath) {
        if (resolvedPath == null) {
            return null;
        }
        try {
            if (Files.exists(resolvedPath) && Files.isRegularFile(resolvedPath)) {
                return Files.size(resolvedPath);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Path resolveLocalPath(String rawAttachment) {
        if (!StringUtils.hasText(rawAttachment)) {
            return null;
        }
        try {
            Path path = Paths.get(rawAttachment.trim()).toAbsolutePath().normalize();
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return path;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String normalizeReference(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.trim();
    }

    private String extractFileName(String raw) {
        String normalized = normalizeReference(raw);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isExternalUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private String resolveStorageProvider(String rawAttachment) {
        if (isExternalUrl(rawAttachment)) {
            return "external_url";
        }
        return objectStorageProperties.isS3Mode() ? "s3" : "local_fs";
    }

    private String resolveAvailabilityStatus(String storageProvider, String storageKey, Path resolvedPath) {
        if ("external_url".equals(storageProvider)) {
            return "external";
        }
        if ("s3".equals(storageProvider) && StringUtils.hasText(storageKey)) {
            return "available";
        }
        if (resolvedPath != null) {
            return "available";
        }
        return StringUtils.hasText(storageKey) ? "missing" : "unresolved";
    }
}
