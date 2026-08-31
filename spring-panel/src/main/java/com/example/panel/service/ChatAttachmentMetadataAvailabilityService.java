package com.example.panel.service;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeWorkload;

import com.example.panel.storage.AttachmentStorageKeyResolver;
import com.example.panel.storage.AttachmentObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.sql.ResultSet;
import java.util.List;

@Service
@RuntimeWorkload(
    id = "chat-attachment-metadata-availability-reconcile",
    roles = {RuntimeRole.MIGRATOR},
    replicaPolicy = RuntimeReplicaPolicy.SINGLETON
)
public class ChatAttachmentMetadataAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(ChatAttachmentMetadataAvailabilityService.class);

    private final JdbcTemplate jdbcTemplate;
    private final AttachmentObjectStorageService attachmentObjectStorageService;
    private final ChatAttachmentMetadataService chatAttachmentMetadataService;

    public ChatAttachmentMetadataAvailabilityService(JdbcTemplate jdbcTemplate,
                                                     AttachmentObjectStorageService attachmentObjectStorageService,
                                                     ChatAttachmentMetadataService chatAttachmentMetadataService) {
        this.jdbcTemplate = jdbcTemplate;
        this.attachmentObjectStorageService = attachmentObjectStorageService;
        this.chatAttachmentMetadataService = chatAttachmentMetadataService;
    }

    @PostConstruct
    void reconcileAvailabilityStatuses() {
        try {
            backfillMissingMetadataRows();
            List<AttachmentMetadataRow> rows = jdbcTemplate.query("""
                    SELECT id, chat_history_id, storage_provider, storage_key, legacy_attachment_ref, normalization_status
                      FROM chat_attachment_metadata
                    """, (rs, rowNum) -> new AttachmentMetadataRow(
                    rs.getLong("id"),
                    rs.getLong("chat_history_id"),
                    trim(rs.getString("storage_provider")),
                    trim(rs.getString("storage_key")),
                    trim(rs.getString("legacy_attachment_ref")),
                    trim(rs.getString("normalization_status"))
            ));
            for (AttachmentMetadataRow row : rows) {
                String storageProvider = resolveStorageProvider(row);
                String normalizationStatus = resolveNormalizationStatus(row, storageProvider);
                String availabilityStatus;
                try {
                    availabilityStatus = resolveAvailabilityStatus(row, storageProvider);
                } catch (RuntimeException ex) {
                    log.warn(
                            "Keeping attachment metadata row {} unchanged because object storage availability could not be determined: {}",
                            row.id(),
                            summarizeAvailabilityFailure(ex)
                    );
                    continue;
                }
                jdbcTemplate.update("""
                        UPDATE chat_attachment_metadata
                           SET storage_provider = ?,
                               normalization_status = ?,
                               availability_status = ?,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE id = ?
                        """,
                        storageProvider,
                        normalizationStatus,
                        availabilityStatus,
                        row.id()
                );
            }
        } catch (DataAccessException ex) {
            log.warn(
                    "Skipping attachment metadata availability reconcile because chat_attachment_metadata is unavailable: {}",
                    DialogDataAccessSupport.summarizeDataAccessException(ex)
            );
        }
    }

    private void backfillMissingMetadataRows() {
        List<MissingAttachmentRow> rows = loadMissingAttachmentRows();

        for (MissingAttachmentRow row : rows) {
            chatAttachmentMetadataService.upsertForChatHistory(
                    row.chatHistoryId(),
                    row.ticketId(),
                    row.channelId(),
                    row.rawAttachment(),
                    row.originalName(),
                    null,
                    null,
                    row.messageType()
            );
        }
    }

    private List<MissingAttachmentRow> loadMissingAttachmentRows() {
        String sqlWithOptionalColumns = """
                SELECT ch.id,
                       ch.ticket_id,
                       ch.channel_id,
                       ch.attachment,
                       ch.file_name,
                       ch.message_type
                  FROM chat_history ch
                 WHERE ch.attachment IS NOT NULL
                   AND trim(ch.attachment) <> ''
                   AND NOT EXISTS (
                       SELECT 1
                         FROM chat_attachment_metadata cam
                        WHERE cam.chat_history_id = ch.id
                   )
                """;
        try {
            return jdbcTemplate.query(sqlWithOptionalColumns, this::mapMissingAttachmentRow);
        } catch (DataAccessException ex) {
            log.debug(
                    "chat_history optional attachment columns are unavailable, falling back to minimal metadata backfill query: {}",
                    DialogDataAccessSupport.summarizeDataAccessException(ex)
            );
        }

        String fallbackSql = """
                SELECT ch.id,
                       ch.ticket_id,
                       ch.channel_id,
                       ch.attachment,
                       NULL AS file_name,
                       NULL AS message_type
                  FROM chat_history ch
                 WHERE ch.attachment IS NOT NULL
                   AND trim(ch.attachment) <> ''
                   AND NOT EXISTS (
                       SELECT 1
                         FROM chat_attachment_metadata cam
                        WHERE cam.chat_history_id = ch.id
                   )
                """;
        return jdbcTemplate.query(fallbackSql, this::mapMissingAttachmentRow);
    }

    private MissingAttachmentRow mapMissingAttachmentRow(ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new MissingAttachmentRow(
                rs.getLong("id"),
                trim(rs.getString("ticket_id")),
                rs.getObject("channel_id") == null ? null : rs.getLong("channel_id"),
                trim(rs.getString("attachment")),
                trim(rs.getString("file_name")),
                trim(rs.getString("message_type"))
        );
    }

    private String resolveStorageProvider(AttachmentMetadataRow row) {
        if (AttachmentStorageKeyResolver.isExternalUrl(row.legacyAttachmentRef())) {
            return "external_url";
        }
        if (StringUtils.hasText(row.storageProvider()) && !"local_fs".equalsIgnoreCase(row.storageProvider().trim())) {
            return row.storageProvider().trim();
        }
        return attachmentObjectStorageService.providerLabel();
    }

    private String resolveNormalizationStatus(AttachmentMetadataRow row, String storageProvider) {
        if ("external_url".equals(storageProvider)) {
            return "normalized";
        }
        return StringUtils.hasText(row.storageKey()) ? "normalized" : "unresolved";
    }

    private String resolveAvailabilityStatus(AttachmentMetadataRow row, String storageProvider) {
        if ("external_url".equals(storageProvider)) {
            return "external";
        }
        if (!StringUtils.hasText(row.storageKey())) {
            return "unresolved";
        }
        attachmentObjectStorageService.backfillDialogAttachmentByStorageKey(row.storageKey());
        return attachmentObjectStorageService.dialogAttachmentExistsByStorageKey(row.storageKey()) ? "available" : "missing";
    }

    private String summarizeAvailabilityFailure(RuntimeException ex) {
        String message = ex.getMessage();
        if (StringUtils.hasText(message)) {
            return ex.getClass().getSimpleName() + ": " + message.trim();
        }
        return ex.getClass().getSimpleName();
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record AttachmentMetadataRow(Long id,
                                         Long chatHistoryId,
                                         String storageProvider,
                                         String storageKey,
                                         String legacyAttachmentRef,
                                         String normalizationStatus) {
    }

    private record MissingAttachmentRow(Long chatHistoryId,
                                        String ticketId,
                                        Long channelId,
                                        String rawAttachment,
                                        String originalName,
                                        String messageType) {
    }
}
