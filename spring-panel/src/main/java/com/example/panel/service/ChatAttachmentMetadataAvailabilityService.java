package com.example.panel.service;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeWorkload;

import com.example.panel.storage.AttachmentService;
import com.example.panel.storage.AttachmentStorageKeyResolver;
import com.example.panel.storage.AttachmentObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
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
    private final AttachmentService attachmentService;
    private final AttachmentObjectStorageService attachmentObjectStorageService;

    public ChatAttachmentMetadataAvailabilityService(JdbcTemplate jdbcTemplate,
                                                     AttachmentService attachmentService,
                                                     AttachmentObjectStorageService attachmentObjectStorageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.attachmentService = attachmentService;
        this.attachmentObjectStorageService = attachmentObjectStorageService;
    }

    @PostConstruct
    void reconcileAvailabilityStatuses() {
        try {
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
                String availabilityStatus = resolveAvailabilityStatus(row, storageProvider);
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
        return attachmentService.hasTicketAttachmentByStorageKey(row.storageKey()) ? "available" : "missing";
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
}
