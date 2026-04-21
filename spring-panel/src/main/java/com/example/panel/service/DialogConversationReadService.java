package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogPreviousHistoryBatch;
import com.example.panel.model.dialog.DialogPreviousHistoryPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    public DialogConversationReadService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ChatMessageDto> loadHistory(String ticketId, Long channelId) {
        if (!StringUtils.hasText(ticketId)) {
            return Collections.emptyList();
        }
        try {
            Set<String> columns = loadTableColumns("chat_history");
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
            String baseSql = """
                    SELECT sender, message, timestamp, message_type, attachment,
                           tg_message_id, reply_to_tg_id, channel_id,
                           %s, %s, %s, %s
                      FROM chat_history
                     WHERE ticket_id = ?
                    """.formatted(originalMessageColumn, editedAtColumn, deletedAtColumn, forwardedFromColumn);
            List<Object> args = new ArrayList<>();
            args.add(ticketId);
            if (channelId != null) {
                baseSql += " AND channel_id = ?";
                args.add(channelId);
            }
            baseSql += " ORDER BY substr(timestamp,1,19) ASC, COALESCE(tg_message_id, 0) ASC, rowid ASC";

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(baseSql, args.toArray());
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
                String attachment = toAttachmentUrl(ticketId, value(row.get("attachment")));
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
            log.warn("Unable to load chat history for ticket {}: {}", ticketId, DialogService.summarizeDataAccessException(ex));
            return List.of();
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
            log.warn("Unable to load previous chat history for ticket {}: {}", ticketId, DialogService.summarizeDataAccessException(ex));
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
            log.warn("Unable to load categories for ticket {}: {}", ticketId, DialogService.summarizeDataAccessException(ex));
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

    private static String toAttachmentUrl(String ticketId, String attachment) {
        if (!StringUtils.hasText(attachment) || !StringUtils.hasText(ticketId)) {
            return attachment;
        }
        String trimmed = attachment.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("/")) {
            return trimmed;
        }
        return "/api/dialogs/" + ticketId.trim() + "/attachments/" + trimmed;
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
            log.warn("Unable to inspect {} columns: {}", tableName, DialogService.summarizeDataAccessException(ex));
            return Set.of();
        }
    }
}
