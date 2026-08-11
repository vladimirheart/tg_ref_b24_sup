package com.example.supportbot.service;

import com.example.supportbot.config.BotDatabaseRuntimeMode;
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
    private final BotDatabaseRuntimeMode databaseRuntimeMode;

    public ChatAttachmentMetadataService(JdbcTemplate jdbcTemplate,
                                         BotDatabaseRuntimeMode databaseRuntimeMode) {
        this.jdbcTemplate = jdbcTemplate;
        this.databaseRuntimeMode = databaseRuntimeMode;
        ensureSchema();
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
        String storageProvider = isExternalUrl(rawAttachment) ? "external_url" : "local_fs";
        String storageKey = normalizeStorageKey(ticketId, rawAttachment);
        Path resolvedPath = resolveLocalPath(rawAttachment);
        Long size = resolveSize(resolvedPath);
        String resolvedOriginalName = resolveOriginalName(originalName, rawAttachment, storageKey);
        String mimeType = resolveMimeType(resolvedPath, resolvedOriginalName, storageKey, messageType);
        String normalizationStatus = "external_url".equals(storageProvider) || StringUtils.hasText(storageKey)
                ? "normalized"
                : "unresolved";
        String availabilityStatus = "external_url".equals(storageProvider)
                ? "external"
                : (resolvedPath != null ? "available" : (StringUtils.hasText(storageKey) ? "missing" : "unresolved"));
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

    private void ensureSchema() {
        if (!databaseRuntimeMode.isSqliteMode()) {
            return;
        }
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS chat_attachment_metadata (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    chat_history_id BIGINT NOT NULL UNIQUE REFERENCES chat_history(id) ON DELETE CASCADE,
                    ticket_id TEXT,
                    channel_id BIGINT,
                    storage_key TEXT,
                    storage_provider TEXT NOT NULL DEFAULT 'local_fs',
                    storage_class TEXT NOT NULL DEFAULT 'dialog_attachment',
                    original_name TEXT,
                    mime_type TEXT,
                    size BIGINT,
                    content_hash TEXT,
                    legacy_attachment_ref TEXT,
                    normalization_status TEXT NOT NULL DEFAULT 'normalized',
                    availability_status TEXT NOT NULL DEFAULT 'unknown',
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TEXT,
                    archived_at TEXT,
                    deleted_at TEXT,
                    CHECK (normalization_status IN ('normalized', 'unresolved')),
                    CHECK (availability_status IN ('available', 'missing', 'external', 'unresolved', 'unknown'))
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_chat_attachment_metadata_ticket
                ON chat_attachment_metadata(ticket_id, chat_history_id)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_chat_attachment_metadata_storage_key
                ON chat_attachment_metadata(storage_key)
                """);
        try {
            jdbcTemplate.execute("""
                    ALTER TABLE chat_attachment_metadata
                    ADD COLUMN availability_status TEXT NOT NULL DEFAULT 'unknown'
                    CHECK (availability_status IN ('available', 'missing', 'external', 'unresolved', 'unknown'))
                    """);
        } catch (Exception ignored) {
        }
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
}
