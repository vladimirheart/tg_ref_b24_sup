package com.example.panel.service;

import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.storage.AttachmentStorageKeyResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;

@Service
public class ChatAttachmentMetadataService {

    private final JdbcTemplate jdbcTemplate;
    private final AttachmentObjectStorageService attachmentObjectStorageService;

    public ChatAttachmentMetadataService(JdbcTemplate jdbcTemplate,
                                         AttachmentObjectStorageService attachmentObjectStorageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.attachmentObjectStorageService = attachmentObjectStorageService;
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
        String storageProvider = AttachmentStorageKeyResolver.isExternalUrl(rawAttachment)
                ? "external_url"
                : attachmentObjectStorageService.providerLabel();
        String storageKey = AttachmentStorageKeyResolver.normalizeStorageKey(ticketId, rawAttachment);
        String normalizationStatus = "external_url".equals(storageProvider) || StringUtils.hasText(storageKey)
                ? "normalized"
                : "unresolved";
        String availabilityStatus = "external_url".equals(storageProvider)
                ? "external"
                : (StringUtils.hasText(storageKey) ? "available" : "unresolved");
        String resolvedOriginalName = AttachmentStorageKeyResolver.resolveOriginalName(originalName, rawAttachment, storageKey);
        String resolvedMimeType = AttachmentStorageKeyResolver.guessMimeType(mimeType, resolvedOriginalName, storageKey, messageType);
        OffsetDateTime timestamp = OffsetDateTime.now();

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
                exactTicketIdOrNull(ticketId),
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

    /** The ingestion transaction supplies a hash only after verifying the owned object bytes. */
    public void upsertForChatHistory(Long chatHistoryId,String ticketId,Long channelId,String rawAttachment,
            String originalName,String mimeType,Long size,String messageType,String contentHash) {
        if(contentHash==null || !contentHash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid attachment hash");
        upsertForChatHistory(chatHistoryId,ticketId,channelId,rawAttachment,originalName,mimeType,size,messageType);
        if(jdbcTemplate.update("UPDATE chat_attachment_metadata SET content_hash=? WHERE chat_history_id=?",contentHash,chatHistoryId)!=1)
            throw new IllegalStateException("Verified attachment metadata is missing");
    }

    // Ticket IDs are opaque legacy identities, not free-form text to sanitize.
    private String exactTicketIdOrNull(String ticketId) {
        return StringUtils.hasText(ticketId) ? ticketId : null;
    }
    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
