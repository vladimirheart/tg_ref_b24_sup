package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogPreviousHistoryBatch;
import com.example.panel.model.dialog.DialogPreviousHistoryPage;
import com.example.panel.storage.AttachmentService;
import com.example.panel.storage.AttachmentStorageKeyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class DialogConversationReadService {

    private static final Logger log = LoggerFactory.getLogger(DialogConversationReadService.class);

    private final JdbcTemplate jdbcTemplate;
    private final AttachmentService attachmentService;

    public DialogConversationReadService(JdbcTemplate jdbcTemplate, AttachmentService attachmentService) {
        this.jdbcTemplate = jdbcTemplate;
        this.attachmentService = attachmentService;
    }

    public List<ChatMessageDto> loadHistory(String ticketId, Long channelId) {
        if (!StringUtils.hasText(ticketId)) {
            return Collections.emptyList();
        }
        try {
            Set<String> columns = loadTableColumns("chat_history");
            Set<String> attachmentMetadataColumns = loadTableColumns("chat_attachment_metadata");
            List<Object> args = new ArrayList<>();
            args.add(ticketId);
            if (channelId != null) {
                args.add(channelId);
            }
            List<Map<String, Object>> rows = queryHistoryRows(
                    ticketId,
                    channelId != null,
                    columns,
                    !attachmentMetadataColumns.isEmpty(),
                    args
            );
            Map<String, String> previewByMessage = new HashMap<>();
            for (Map<String, Object> row : rows) {
                Long tgMessageId = parseLong(row.get("tg_message_id"));
                if (tgMessageId == null) {
                    continue;
                }
                String key = previewKey(parseLong(row.get("channel_id")), tgMessageId);
                String preview = buildPreview(row.get("message"), row.get("message_type"));
                if (StringUtils.hasText(preview)) {
                    previewByMessage.put(key, preview);
                }
            }

            List<ChatMessageDto> history = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Long replyTo = parseLong(row.get("reply_to_tg_id"));
                String replyPreview = null;
                if (replyTo != null) {
                    String key = previewKey(parseLong(row.get("channel_id")), replyTo);
                    replyPreview = previewByMessage.get(key);
                }
                String rawAttachment = value(row.get("attachment"));
                String storageKey = value(row.get("attachment_storage_key"));
                String attachmentProvider = value(row.get("attachment_storage_provider"));
                String attachmentStatus = value(row.get("attachment_availability_status"));
                String legacyAttachmentRef = value(row.get("attachment_legacy_ref"));
                String attachment = toAttachmentUrl(ticketId, rawAttachment, storageKey, attachmentProvider, attachmentStatus, legacyAttachmentRef);
                AttachmentMeta attachmentMeta = resolveAttachmentMeta(
                        ticketId,
                        rawAttachment,
                        storageKey,
                        attachmentProvider,
                        attachmentStatus,
                        attachment,
                        value(row.get("attachment_original_name")),
                        parseLong(row.get("attachment_size"))
                );
                String message = value(row.get("message"));
                String originalMessage = value(row.get("original_message"));
                String deletedAt = value(row.get("deleted_at"));
                history.add(new ChatMessageDto(
                        value(row.get("sender")),
                        deletedAt != null ? "" : message,
                        originalMessage != null ? originalMessage : message,
                        value(row.get("timestamp")),
                        value(row.get("message_type")),
                        attachment,
                        firstNonBlank(value(row.get("file_name")), attachmentMeta.name()),
                        attachmentMeta.size(),
                        attachmentStatus,
                        attachmentProvider,
                        buildAttachmentNote(attachmentStatus, attachmentProvider),
                        parseLong(row.get("tg_message_id")),
                        replyTo,
                        replyPreview,
                        value(row.get("edited_at")),
                        deletedAt,
                        value(row.get("forwarded_from"))
                ));
            }
            return history;
        } catch (DataAccessException ex) {
            log.warn("Unable to load chat history for ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
            return List.of();
        }
    }

    private List<Map<String, Object>> queryHistoryRows(String ticketId,
                                                       boolean filterByChannelId,
                                                       Set<String> columns,
                                                       boolean attachmentMetadataAvailable,
                                                       List<Object> args) {
        try {
            return jdbcTemplate.queryForList(
                    buildHistorySql(columns, filterByChannelId, attachmentMetadataAvailable),
                    args.toArray()
            );
        } catch (DataAccessException ex) {
            if (!attachmentMetadataAvailable) {
                throw ex;
            }
            log.warn(
                    "Unable to load attachment metadata for ticket {}: {}. Retrying dialog history without metadata join.",
                    ticketId,
                    DialogDataAccessSupport.summarizeDataAccessException(ex)
            );
            return jdbcTemplate.queryForList(
                    buildHistorySql(columns, filterByChannelId, false),
                    args.toArray()
            );
        }
    }

    public Optional<DialogPreviousHistoryPage> loadPreviousDialogHistory(String ticketId, int offset) {
        if (!StringUtils.hasText(ticketId) || offset < 0) {
            return Optional.empty();
        }
        try {
            String sql = """
                    SELECT
                        m.ticket_id,
                        COALESCE(t.status, 'pending') AS status,
                        MAX(COALESCE(m.created_at, t.created_at)) AS created_at,
                        MAX(COALESCE(NULLIF(m.problem, ''), '')) AS problem,
                        MAX(COALESCE(c.channel_name, 'Без канала')) AS channel_name,
                        CASE
                            WHEN EXISTS (
                                SELECT 1
                                  FROM web_form_sessions w
                                 WHERE w.ticket_id = m.ticket_id
                            ) THEN 'web_form'
                            WHEN lower(COALESCE(MAX(c.platform), '')) = 'vk' THEN 'vk'
                            WHEN lower(COALESCE(MAX(c.platform), '')) = 'max' THEN 'max'
                            WHEN lower(COALESCE(MAX(c.platform), '')) = 'telegram'
                                 OR trim(COALESCE(MAX(c.platform), '')) = '' THEN 'telegram'
                            ELSE lower(MAX(c.platform))
                        END AS source_key
                      FROM messages m
                      LEFT JOIN tickets t ON t.ticket_id = m.ticket_id
                      LEFT JOIN channels c ON c.id = m.channel_id
                     WHERE m.user_id = (
                            SELECT m2.user_id
                              FROM messages m2
                             WHERE m2.ticket_id = ?
                               AND m2.user_id IS NOT NULL
                             ORDER BY substr(m2.created_at, 1, 19) DESC,
                                      m2.group_msg_id DESC
                             LIMIT 1
                        )
                       AND m.ticket_id <> ?
                     GROUP BY m.ticket_id, COALESCE(t.status, 'pending')
                     ORDER BY MAX(substr(COALESCE(m.created_at, t.created_at), 1, 19)) DESC,
                              m.ticket_id DESC
                     LIMIT 2 OFFSET ?
                    """;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, ticketId.trim(), ticketId.trim(), offset);
            List<DialogPreviousHistoryBatch> batches = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String previousTicketId = value(row.get("ticket_id"));
                String sourceKey = normalizeDialogSourceKey(value(row.get("source_key")));
                batches.add(new DialogPreviousHistoryBatch(
                        previousTicketId,
                        value(row.get("status")),
                        value(row.get("created_at")),
                        value(row.get("problem")),
                        value(row.get("channel_name")),
                        sourceKey,
                        resolveDialogSourceLabel(sourceKey),
                        loadHistory(previousTicketId, null)
                ));
            }
            if (batches.isEmpty()) {
                return Optional.empty();
            }
            boolean hasMore = batches.size() > 1;
            Integer nextOffset = hasMore ? offset + 1 : null;
            return Optional.of(new DialogPreviousHistoryPage(batches.get(0), nextOffset, hasMore));
        } catch (DataAccessException ex) {
            log.warn("Unable to load previous chat history for ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
            return Optional.empty();
        }
    }

    public List<String> loadTicketCategories(String ticketId) {
        if (!StringUtils.hasText(ticketId)) {
            return List.of();
        }
        try {
            return jdbcTemplate.query(
                    "SELECT category FROM ticket_categories WHERE ticket_id = ? ORDER BY category ASC",
                    (rs, rowNum) -> rs.getString("category"),
                    ticketId
            );
        } catch (DataAccessException ex) {
            log.warn("Unable to load categories for ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
            return List.of();
        }
    }

    private String normalizeDialogSourceKey(String value) {
        String normalized = value(value);
        return normalized == null ? "telegram" : normalized.toLowerCase();
    }

    private String resolveDialogSourceLabel(String value) {
        return switch (normalizeDialogSourceKey(value)) {
            case "vk" -> "VK";
            case "max" -> "MAX";
            case "web_form" -> "Внешняя форма";
            case "telegram" -> "Telegram";
            default -> "Источник не определён";
        };
    }

    private static String buildPreview(Object message, Object messageType) {
        String base = value(message);
        if (StringUtils.hasText(base)) {
            return base.length() > 96 ? base.substring(0, 93) + "..." : base;
        }
        String type = value(messageType);
        if (!StringUtils.hasText(type)) {
            return "Сообщение";
        }
        return switch (type.trim().toLowerCase()) {
            case "image" -> "Изображение";
            case "video" -> "Видео";
            case "audio", "voice" -> "Аудио";
            case "document", "file" -> "Файл";
            case "sticker" -> "Стикер";
            case "location" -> "Локация";
            case "contact" -> "Контакт";
            default -> "Вложение";
        };
    }

    private static String previewKey(Long channelId, Long telegramMessageId) {
        return (channelId != null ? channelId : 0L) + ":" + telegramMessageId;
    }

    private static String toAttachmentUrl(String ticketId,
                                          String attachment,
                                          String storageKey,
                                          String attachmentProvider,
                                          String attachmentStatus,
                                          String legacyAttachmentRef) {
        if ("external_url".equalsIgnoreCase(trimToNull(attachmentProvider))) {
            return trimToNull(legacyAttachmentRef);
        }
        if ("missing".equalsIgnoreCase(trimToNull(attachmentStatus))) {
            return null;
        }
        if (StringUtils.hasText(storageKey)) {
            return "/api/attachments/tickets/by-storage-key?key="
                    + UriUtils.encodeQueryParam(storageKey.trim(), StandardCharsets.UTF_8);
        }
        if (!StringUtils.hasText(attachment) || !StringUtils.hasText(ticketId)) {
            return attachment;
        }
        String trimmed = attachment.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("/")) {
            return trimmed;
        }
        if (trimmed.startsWith("api/")) {
            return "/" + trimmed;
        }
        String normalized = trimmed.replace('\\', '/');
        if (normalized.startsWith("attachments/") || normalized.contains("/attachments/")) {
            return "/api/attachments/tickets/by-path?path="
                    + UriUtils.encodeQueryParam(trimmed, StandardCharsets.UTF_8);
        }
        return "/api/attachments/tickets/"
                + UriUtils.encodePathSegment(ticketId.trim(), StandardCharsets.UTF_8)
                + "/"
                + UriUtils.encodePathSegment(trimmed, StandardCharsets.UTF_8);
    }

    private AttachmentMeta resolveAttachmentMeta(String ticketId,
                                                 String rawAttachment,
                                                 String storageKey,
                                                 String attachmentProvider,
                                                 String attachmentStatus,
                                                 String attachmentUrl,
                                                 String metadataOriginalName,
                                                 Long metadataSize) {
        if ("missing".equalsIgnoreCase(trimToNull(attachmentStatus))
                || "external".equalsIgnoreCase(trimToNull(attachmentStatus))
                || "external_url".equalsIgnoreCase(trimToNull(attachmentProvider))) {
            return new AttachmentMeta(
                    AttachmentStorageKeyResolver.resolveOriginalName(metadataOriginalName, rawAttachment, storageKey),
                    metadataSize
            );
        }
        if (StringUtils.hasText(storageKey) && (StringUtils.hasText(metadataOriginalName) || metadataSize != null)) {
            return new AttachmentMeta(
                    AttachmentStorageKeyResolver.resolveOriginalName(metadataOriginalName, rawAttachment, storageKey),
                    metadataSize
            );
        }
        if (!StringUtils.hasText(rawAttachment)) {
            return new AttachmentMeta(resolveAttachmentName(rawAttachment, storageKey, attachmentUrl), metadataSize);
        }
        try {
            AttachmentService.AttachmentDescriptor descriptor;
            if (StringUtils.hasText(storageKey)) {
                descriptor = attachmentService.describeTicketAttachmentByStorageKey(storageKey.trim());
            } else {
                String normalized = rawAttachment.trim().replace('\\', '/');
                if (normalized.startsWith("attachments/") || normalized.contains("/attachments/")) {
                    descriptor = attachmentService.describeTicketAttachmentByPath(rawAttachment);
                } else if (StringUtils.hasText(ticketId)) {
                    descriptor = attachmentService.describeTicketAttachment(ticketId.trim(), rawAttachment.trim());
                } else {
                    descriptor = null;
                }
            }
            if (descriptor != null) {
                return new AttachmentMeta(
                        AttachmentStorageKeyResolver.resolveOriginalName(
                                firstNonBlank(metadataOriginalName, descriptor.originalName()),
                                rawAttachment,
                                storageKey
                        ),
                        metadataSize != null ? metadataSize : (descriptor.size() >= 0 ? descriptor.size() : null)
                );
            }
        } catch (Exception ex) {
            log.debug("Unable to resolve attachment meta for ticket {} and attachment {}: {}", ticketId, rawAttachment, ex.getMessage());
        }
        return new AttachmentMeta(
                resolveAttachmentName(rawAttachment, storageKey, attachmentUrl),
                metadataSize
        );
    }

    private static String buildAttachmentNote(String attachmentStatus, String attachmentProvider) {
        String status = trimToNull(attachmentStatus);
        if ("missing".equalsIgnoreCase(status)) {
            return "Файл отсутствует в локальном storage.";
        }
        if ("external".equalsIgnoreCase(status) || "external_url".equalsIgnoreCase(trimToNull(attachmentProvider))) {
            return "Внешнее вложение.";
        }
        if ("unresolved".equalsIgnoreCase(status)) {
            return "Ссылка на вложение не нормализована.";
        }
        return null;
    }

    private String resolveAttachmentName(String rawAttachment, String storageKey, String attachmentUrl) {
        String resolved = AttachmentStorageKeyResolver.resolveOriginalName(null, rawAttachment, storageKey);
        if (StringUtils.hasText(resolved)) {
            return resolved;
        }
        String candidate = StringUtils.hasText(attachmentUrl) ? attachmentUrl.trim() : "";
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String filename = AttachmentStorageKeyResolver.extractFileName(candidate);
        if (!StringUtils.hasText(filename)) {
            return null;
        }
        String normalized = AttachmentStorageKeyResolver.stripStoredAttachmentPrefix(decodeFileName(filename));
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (isOpaqueAttachmentName(normalized)) {
            String extension = extractExtension(normalized);
            return StringUtils.hasText(extension) ? "Файл " + extension.toUpperCase() : null;
        }
        return normalized;
    }

    private static String qualifyChatHistoryColumn(String columnExpression) {
        if (!StringUtils.hasText(columnExpression) || columnExpression.contains(" AS ") || columnExpression.contains(" as ")) {
            return columnExpression;
        }
        return "ch." + columnExpression;
    }

    private String buildHistorySql(Set<String> columns,
                                   boolean filterByChannelId,
                                   boolean attachmentMetadataAvailable) {
        String originalMessageColumn = columns.contains("original_message")
                ? "original_message"
                : "NULL AS original_message";
        String forwardedFromColumn = columns.contains("forwarded_from")
                ? "forwarded_from"
                : "NULL AS forwarded_from";
        String editedAtColumn = columns.contains("edited_at")
                ? "edited_at"
                : "NULL AS edited_at";
        String deletedAtColumn = columns.contains("deleted_at")
                ? "deleted_at"
                : "NULL AS deleted_at";
        String fileNameColumn = columns.contains("file_name")
                ? "file_name"
                : "NULL AS file_name";
        String metadataSelect = attachmentMetadataAvailable
                ? """
                        , cam.storage_key AS attachment_storage_key,
                          cam.storage_provider AS attachment_storage_provider,
                          cam.original_name AS attachment_original_name,
                          cam.size AS attachment_size,
                          cam.availability_status AS attachment_availability_status,
                          cam.legacy_attachment_ref AS attachment_legacy_ref
                        """
                : """
                        , NULL AS attachment_storage_key,
                          NULL AS attachment_storage_provider,
                          NULL AS attachment_original_name,
                          NULL AS attachment_size,
                          NULL AS attachment_availability_status,
                          NULL AS attachment_legacy_ref
                        """;
        String metadataJoin = attachmentMetadataAvailable
                ? " LEFT JOIN chat_attachment_metadata cam ON cam.chat_history_id = ch.id "
                : "";
        StringBuilder sql = new StringBuilder("""
                SELECT ch.sender, ch.message, ch.timestamp, ch.message_type, ch.attachment,
                       ch.tg_message_id, ch.reply_to_tg_id, ch.channel_id,
                       %s, %s, %s, %s, %s
                       %s
                  FROM chat_history ch
                  %s
                 WHERE ch.ticket_id = ?
                """.formatted(
                qualifyChatHistoryColumn(originalMessageColumn),
                qualifyChatHistoryColumn(editedAtColumn),
                qualifyChatHistoryColumn(deletedAtColumn),
                qualifyChatHistoryColumn(forwardedFromColumn),
                qualifyChatHistoryColumn(fileNameColumn),
                metadataSelect,
                metadataJoin
        ));
        if (filterByChannelId) {
            sql.append(" AND ch.channel_id = ?");
        }
        sql.append(" ORDER BY substr(ch.timestamp,1,19) ASC, COALESCE(ch.tg_message_id, 0) ASC, ch.rowid ASC");
        return sql.toString();
    }

    private static String firstNonBlank(String... values) {
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

    private static String decodeFileName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UriUtils.decode(value.trim(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return value.trim();
        }
    }

    private static boolean isOpaqueAttachmentName(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String normalized = value.trim();
        int dotIndex = normalized.lastIndexOf('.');
        String stem = dotIndex > 0 ? normalized.substring(0, dotIndex) : normalized;
        return stem.matches("(?i)[0-9a-f]{16,}");
    }

    private static String extractExtension(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == normalized.length() - 1) {
            return null;
        }
        return normalized.substring(dotIndex + 1).trim();
    }

    private static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String value(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private Set<String> loadTableColumns(String tableName) {
        try {
            return jdbcTemplate.execute((ConnectionCallback<Set<String>>) connection -> {
                Set<String> columns = new LinkedHashSet<>();
                var metaData = connection.getMetaData();
                try (var resultSet = metaData.getColumns(null, null, tableName, null)) {
                    while (resultSet.next()) {
                        columns.add(resultSet.getString("COLUMN_NAME").toLowerCase());
                    }
                }
                if (!columns.isEmpty()) {
                    return columns;
                }
                try (var resultSet = metaData.getColumns(null, null, tableName.toUpperCase(), null)) {
                    while (resultSet.next()) {
                        columns.add(resultSet.getString("COLUMN_NAME").toLowerCase());
                    }
                }
                return columns;
            });
        } catch (DataAccessException ex) {
            log.warn("Unable to inspect {} columns: {}", tableName, DialogDataAccessSupport.summarizeDataAccessException(ex));
            return Set.of();
        }
    }

    private record AttachmentMeta(String name, Long size) {}
}
