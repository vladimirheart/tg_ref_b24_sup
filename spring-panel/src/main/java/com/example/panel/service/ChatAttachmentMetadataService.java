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

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
