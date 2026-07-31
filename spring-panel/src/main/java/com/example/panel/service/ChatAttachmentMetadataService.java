package com.example.panel.service;

import com.example.panel.storage.AttachmentStorageKeyResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;

@Service
public class ChatAttachmentMetadataService {

    private final JdbcTemplate jdbcTemplate;

    public ChatAttachmentMetadataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
    }

    public void upsertForChatHistory(Long chatHistoryId,
                                     String ticketId,
                                     Long channelId,
                                     String rawAttachment,
                                     String originalName,
                                     String mimeType,
                                     Long size,
                                     String messageType) {
        if (chatHistoryId == null || !StringUtils.hasText(rawAttachment)) {
            return;
        }
        String storageProvider = AttachmentStorageKeyResolver.isExternalUrl(rawAttachment) ? "external_url" : "local_fs";
        String storageKey = AttachmentStorageKeyResolver.normalizeStorageKey(ticketId, rawAttachment);
        String normalizationStatus = "external_url".equals(storageProvider) || StringUtils.hasText(storageKey)
                ? "normalized"
                : "unresolved";
        String availabilityStatus = "external_url".equals(storageProvider)
                ? "external"
                : (StringUtils.hasText(storageKey) ? "available" : "unresolved");
        String resolvedOriginalName = AttachmentStorageKeyResolver.resolveOriginalName(originalName, rawAttachment, storageKey);
        String resolvedMimeType = AttachmentStorageKeyResolver.guessMimeType(mimeType, resolvedOriginalName, storageKey, messageType);
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
                trim(resolvedMimeType),
                size,
                trim(rawAttachment),
                normalizationStatus,
                availabilityStatus,
                timestamp,
                timestamp
        );
    }

    private void ensureSchema() {
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

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
