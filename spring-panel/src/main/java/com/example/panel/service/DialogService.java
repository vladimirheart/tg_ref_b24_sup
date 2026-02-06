package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogChannelStat;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class DialogService {

    private static final Logger log = LoggerFactory.getLogger(DialogService.class);

    private final JdbcTemplate jdbcTemplate;

    public DialogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DialogSummary loadSummary() {
        try {
            long total = Objects.requireNonNullElse(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tickets", Long.class), 0L);
            long resolved = Objects.requireNonNullElse(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tickets WHERE status = 'resolved'", Long.class), 0L);
            long pending = Math.max(0, total - resolved);
            List<DialogChannelStat> channelStats = jdbcTemplate.query(
                    "SELECT COALESCE(c.channel_name, 'Без канала') AS name, COUNT(*) AS total " +
                            "FROM tickets t LEFT JOIN channels c ON c.id = t.channel_id " +
                            "GROUP BY COALESCE(c.channel_name, 'Без канала') ORDER BY total DESC",
                    (rs, rowNum) -> new DialogChannelStat(rs.getString("name"), rs.getLong("total"))
            );
            return new DialogSummary(total, resolved, pending, channelStats);
        } catch (DataAccessException ex) {
            log.warn("Unable to load dialog summary, returning empty view: {}", ex.getMessage());
            return new DialogSummary(0, 0, 0, List.of());
        }
    }

    public List<DialogListItem> loadDialogs(String currentOperator) {
        try {
            boolean feedbackHasTicketId = loadTableColumns("feedbacks").contains("ticket_id");
            String ratingSelect = feedbackHasTicketId
                    ? """
                       (
                           SELECT rating
                             FROM feedbacks f
                            WHERE f.ticket_id = m.ticket_id
                            ORDER BY f.timestamp DESC, f.id DESC
                            LIMIT 1
                       ) AS rating,
                       """
                    : "NULL AS rating,";
            String sql = """
                    SELECT m.ticket_id, m.group_msg_id AS request_number,
                           m.user_id, m.username, m.client_name, m.business,
                           COALESCE(m.channel_id, t.channel_id) AS channel_id,
                           c.channel_name AS channel_name,
                           m.city, m.location_name,
                       m.problem, m.created_at, t.status, t.resolved_by, t.resolved_at,
                       tr.responsible AS responsible,
                       m.created_date, m.created_time, cs.status AS client_status,
                       %s
                       (
                           SELECT sender
                             FROM chat_history ch
                            WHERE ch.ticket_id = m.ticket_id
                            ORDER BY substr(ch.timestamp, 1, 19) DESC,
                                     COALESCE(ch.tg_message_id, 0) DESC,
                                     ch.id DESC
                            LIMIT 1
                       ) AS last_sender,
                       (
                           SELECT timestamp
                             FROM chat_history ch
                            WHERE ch.ticket_id = m.ticket_id
                            ORDER BY substr(ch.timestamp, 1, 19) DESC,
                                     COALESCE(ch.tg_message_id, 0) DESC,
                                     ch.id DESC
                            LIMIT 1
                       ) AS last_sender_time,
                       (
                           SELECT GROUP_CONCAT(tc.category, ', ')
                             FROM ticket_categories tc
                            WHERE tc.ticket_id = m.ticket_id
                       ) AS categories,
                       CASE
                           WHEN tr.responsible = ? THEN (
                               SELECT COUNT(*)
                                 FROM chat_history ch
                                WHERE ch.ticket_id = m.ticket_id
                                  AND lower(ch.sender) NOT IN ('operator', 'support', 'admin', 'system')
                                  AND ch.timestamp > COALESCE(
                                      tr.last_read_at,
                                      (
                                          SELECT MAX(op.timestamp)
                                            FROM chat_history op
                                           WHERE op.ticket_id = m.ticket_id
                                             AND lower(op.sender) IN ('operator', 'support', 'admin', 'system')
                                      ),
                                      ''
                                  )
                           )
                           ELSE 0
                       END AS unread_count
                  FROM messages m
                  LEFT JOIN tickets t ON m.ticket_id = t.ticket_id
                  LEFT JOIN channels c ON c.id = COALESCE(m.channel_id, t.channel_id)
                  LEFT JOIN ticket_responsibles tr ON tr.ticket_id = m.ticket_id
                  LEFT JOIN client_statuses cs ON cs.user_id = m.user_id
                       AND cs.updated_at = (
                           SELECT MAX(updated_at) FROM client_statuses WHERE user_id = m.user_id
                       )
                  ORDER BY m.created_at DESC
                    """.formatted(ratingSelect);
            return jdbcTemplate.query(sql, (rs, rowNum) -> new DialogListItem(
                    rs.getString("ticket_id"),
                    rs.getObject("request_number") != null ? rs.getLong("request_number") : null,
                    rs.getObject("user_id") != null ? rs.getLong("user_id") : null,
                    rs.getString("username"),
                    rs.getString("client_name"),
                    rs.getString("business"),
                    rs.getObject("channel_id") != null ? rs.getLong("channel_id") : null,
                    rs.getString("channel_name"),
                    rs.getString("city"),
                    rs.getString("location_name"),
                    rs.getString("problem"),
                    rs.getString("created_at"),
                    rs.getString("status"),
                    rs.getString("resolved_by"),
                    rs.getString("resolved_at"),
                    rs.getString("responsible"),
                    rs.getString("created_date"),
                    rs.getString("created_time"),
                    rs.getString("client_status"),
                    rs.getString("last_sender"),
                    rs.getString("last_sender_time"),
                    rs.getObject("unread_count") != null ? rs.getInt("unread_count") : 0,
                    rs.getObject("rating") != null ? rs.getInt("rating") : null,
                    rs.getString("categories")
            ), currentOperator);
        } catch (DataAccessException ex) {
            log.warn("Unable to load dialogs, returning empty list: {}", ex.getMessage());
            return List.of();
        }
    }

    public Optional<DialogListItem> findDialog(String ticketId, String operator) {
        try {
            boolean feedbackHasTicketId = loadTableColumns("feedbacks").contains("ticket_id");
            String ratingSelect = feedbackHasTicketId
                    ? """
                       (
                           SELECT rating
                             FROM feedbacks f
                            WHERE f.ticket_id = m.ticket_id
                            ORDER BY f.timestamp DESC, f.id DESC
                            LIMIT 1
                       ) AS rating,
                       """
                    : "NULL AS rating,";
            String sql = """
                    SELECT m.ticket_id, m.group_msg_id AS request_number,
                           m.user_id, m.username, m.client_name, m.business,
                           COALESCE(m.channel_id, t.channel_id) AS channel_id,
                           c.channel_name AS channel_name,
                           m.city, m.location_name,
                           m.problem, m.created_at, t.status, t.resolved_by, t.resolved_at,
                           tr.responsible AS responsible,
                           m.created_date, m.created_time, cs.status AS client_status,
                           %s
                           (
                               SELECT sender
                                 FROM chat_history ch
                                WHERE ch.ticket_id = m.ticket_id
                                ORDER BY substr(ch.timestamp, 1, 19) DESC,
                                         COALESCE(ch.tg_message_id, 0) DESC,
                                         ch.id DESC
                                LIMIT 1
                           ) AS last_sender,
                           (
                               SELECT timestamp
                                 FROM chat_history ch
                                WHERE ch.ticket_id = m.ticket_id
                                ORDER BY substr(ch.timestamp, 1, 19) DESC,
                                         COALESCE(ch.tg_message_id, 0) DESC,
                                         ch.id DESC
                                LIMIT 1
                           ) AS last_sender_time,
                           (
                               SELECT GROUP_CONCAT(tc.category, ', ')
                                 FROM ticket_categories tc
                                WHERE tc.ticket_id = m.ticket_id
                           ) AS categories,
                           CASE
                               WHEN tr.responsible = ? THEN (
                                   SELECT COUNT(*)
                                     FROM chat_history ch
                                    WHERE ch.ticket_id = m.ticket_id
                                      AND lower(ch.sender) NOT IN ('operator', 'support', 'admin', 'system')
                                      AND ch.timestamp > COALESCE(
                                          tr.last_read_at,
                                          (
                                              SELECT MAX(op.timestamp)
                                                FROM chat_history op
                                               WHERE op.ticket_id = m.ticket_id
                                                 AND lower(op.sender) IN ('operator', 'support', 'admin', 'system')
                                          ),
                                          ''
                                      )
                               )
                               ELSE 0
                           END AS unread_count
                      FROM messages m
                      LEFT JOIN tickets t ON m.ticket_id = t.ticket_id
                      LEFT JOIN channels c ON c.id = COALESCE(m.channel_id, t.channel_id)
                      LEFT JOIN ticket_responsibles tr ON tr.ticket_id = m.ticket_id
                      LEFT JOIN client_statuses cs ON cs.user_id = m.user_id
                           AND cs.updated_at = (
                               SELECT MAX(updated_at) FROM client_statuses WHERE user_id = m.user_id
                           )
                     WHERE m.ticket_id = ?
                     ORDER BY m.created_at DESC
                     LIMIT 1
                    """.formatted(ratingSelect);
            List<DialogListItem> items = jdbcTemplate.query(sql, (rs, rowNum) -> new DialogListItem(
                    rs.getString("ticket_id"),
                    rs.getObject("request_number") != null ? rs.getLong("request_number") : null,
                    rs.getObject("user_id") != null ? rs.getLong("user_id") : null,
                    rs.getString("username"),
                    rs.getString("client_name"),
                    rs.getString("business"),
                    rs.getObject("channel_id") != null ? rs.getLong("channel_id") : null,
                    rs.getString("channel_name"),
                    rs.getString("city"),
                    rs.getString("location_name"),
                    rs.getString("problem"),
                    rs.getString("created_at"),
                    rs.getString("status"),
                    rs.getString("resolved_by"),
                    rs.getString("resolved_at"),
                    rs.getString("responsible"),
                    rs.getString("created_date"),
                    rs.getString("created_time"),
                    rs.getString("client_status"),
                    rs.getString("last_sender"),
                    rs.getString("last_sender_time"),
                    rs.getObject("unread_count") != null ? rs.getInt("unread_count") : 0,
                    rs.getObject("rating") != null ? rs.getInt("rating") : null,
                    rs.getString("categories")
            ), operator, ticketId);
            return items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
        } catch (DataAccessException ex) {
            log.warn("Unable to load dialog {} details: {}", ticketId, ex.getMessage());
            return Optional.empty();
        }
    }

    public void assignResponsibleIfMissing(String ticketId, String username) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(username)) {
            return;
        }
        try {
            jdbcTemplate.update(
                    "INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by) "
                            + "SELECT ?, ?, ? WHERE NOT EXISTS ("
                            + "SELECT 1 FROM ticket_responsibles WHERE ticket_id = ?)",
                    ticketId, username, username, ticketId
            );
        } catch (DataAccessException ex) {
            log.warn("Unable to assign responsible for ticket {}: {}", ticketId, ex.getMessage());
        }
    }

    public void markDialogAsRead(String ticketId, String operator) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(operator)) {
            return;
        }
        assignResponsibleIfMissing(ticketId, operator);
        try {
            jdbcTemplate.update(
                    "UPDATE ticket_responsibles "
                            + "SET last_read_at = COALESCE("
                            + "    (SELECT MAX(timestamp) FROM chat_history WHERE ticket_id = ?),"
                            + "    CURRENT_TIMESTAMP"
                            + ") "
                            + "WHERE ticket_id = ? AND responsible = ?",
                    ticketId,
                    ticketId,
                    operator
            );
        } catch (DataAccessException ex) {
            log.warn("Unable to mark dialog {} as read for {}: {}", ticketId, operator, ex.getMessage());
        }
    }

    public void assignResponsibleIfMissingOrRedirected(String ticketId, String newResponsible, String assignedBy) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(newResponsible)) {
            return;
        }
        String actor = StringUtils.hasText(assignedBy) ? assignedBy : newResponsible;
        try {
            int inserted = jdbcTemplate.update(
                    "INSERT INTO ticket_responsibles(ticket_id, responsible, assigned_by) "
                            + "SELECT ?, ?, ? WHERE NOT EXISTS ("
                            + "SELECT 1 FROM ticket_responsibles WHERE ticket_id = ?)",
                    ticketId, newResponsible, actor, ticketId
            );
            if (inserted == 0) {
                jdbcTemplate.update(
                        "UPDATE ticket_responsibles SET responsible = ?, assigned_by = ? WHERE ticket_id = ?",
                        newResponsible, actor, ticketId
                );
            }
        } catch (DataAccessException ex) {
            log.warn("Unable to update responsible for ticket {}: {}", ticketId, ex.getMessage());
        }
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
                Long tgMessageId = toLong(row.get("tg_message_id"));
                if (tgMessageId == null) {
                    continue;
                }
                String key = previewKey(toLong(row.get("channel_id")), tgMessageId);
                String preview = buildPreview(row.get("message"), row.get("message_type"));
                if (StringUtils.hasText(preview)) {
                    previewByMessage.put(key, preview);
                }
            }

            List<ChatMessageDto> history = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Long replyTo = toLong(row.get("reply_to_tg_id"));
                String replyPreview = null;
                if (replyTo != null) {
                    String key = previewKey(toLong(row.get("channel_id")), replyTo);
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
                        toLong(row.get("tg_message_id")),
                        replyTo,
                        replyPreview,
                        value(row.get("edited_at")),
                        deletedAt,
                        value(row.get("forwarded_from"))
                ));
            }
            return history;
        } catch (DataAccessException ex) {
            log.warn("Unable to load chat history for ticket {}: {}", ticketId, ex.getMessage());
            return List.of();
        }
    }

    public Optional<DialogDetails> loadDialogDetails(String ticketId, Long channelId, String operator) {
        return findDialog(ticketId, operator).map(item -> new DialogDetails(item, loadHistory(ticketId, channelId), loadTicketCategories(ticketId)));
    }

    private Set<String> loadTableColumns(String tableName) {
        try {
            return jdbcTemplate.execute((ConnectionCallback<Set<String>>) connection -> {
                Set<String> columns = new java.util.HashSet<>();
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
            log.warn("Unable to inspect {} columns: {}", tableName, ex.getMessage());
            return Set.of();
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
            log.warn("Unable to load categories for ticket {}: {}", ticketId, ex.getMessage());
            return List.of();
        }
    }

    public void setTicketCategories(String ticketId, List<String> categories) {
        if (!StringUtils.hasText(ticketId)) {
            return;
        }
        List<String> normalized = normalizeCategories(categories);
        try {
            jdbcTemplate.update("DELETE FROM ticket_categories WHERE ticket_id = ?", ticketId);
            for (String category : normalized) {
                jdbcTemplate.update(
                        "INSERT INTO ticket_categories(ticket_id, category, created_at, updated_at) VALUES(?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                        ticketId,
                        category
                );
            }
        } catch (DataAccessException ex) {
            log.warn("Unable to set categories for ticket {}: {}", ticketId, ex.getMessage());
        }
    }

    public ResolveResult resolveTicket(String ticketId, String operator, List<String> categories) {
        if (!StringUtils.hasText(ticketId)) {
            return new ResolveResult(false, false, null);
        }
        try {
            List<String> normalizedCategories = normalizeCategories(categories);
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tickets WHERE ticket_id = ?",
                    Integer.class,
                    ticketId
            );
            if (count == null || count == 0) {
                return new ResolveResult(false, false, null);
            }
            if (normalizedCategories.isEmpty()) {
                return new ResolveResult(false, true, "Укажите хотя бы одну категорию обращения перед закрытием.");
            }
            String resolvedBy = StringUtils.hasText(operator) ? operator : "Оператор";
            int updated = jdbcTemplate.update(
                    "UPDATE tickets SET status = 'resolved', resolved_at = CURRENT_TIMESTAMP, "
                            + "resolved_by = ?, closed_count = COALESCE(closed_count, 0) + 1 "
                            + "WHERE ticket_id = ? AND (status IS NULL OR status != 'resolved')",
                    resolvedBy,
                    ticketId
            );
            if (updated > 0) {
                setTicketCategories(ticketId, normalizedCategories);
                ensurePendingFeedbackRequest(ticketId, resolvedBy);
            }
            return new ResolveResult(updated > 0, true, null);
        } catch (DataAccessException ex) {
            log.warn("Unable to resolve ticket {}: {}", ticketId, ex.getMessage());
            return new ResolveResult(false, false, null);
        }
    }

    private void ensurePendingFeedbackRequest(String ticketId, String resolvedBy) {
        if (!StringUtils.hasText(ticketId)) {
            return;
        }
        String source = "operator_close";
        if (StringUtils.hasText(resolvedBy) && "Авто-система".equalsIgnoreCase(resolvedBy.trim())) {
            source = "auto_close";
        }
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE pending_feedback_requests "
                            + "SET expires_at = datetime('now', '+1 day'), source = ? "
                            + "WHERE ticket_id = ?",
                    source,
                    ticketId
            );
            if (updated > 0) {
                return;
            }
            TicketOwner owner = jdbcTemplate.query(
                    "SELECT user_id, channel_id FROM tickets WHERE ticket_id = ?",
                    rs -> rs.next()
                            ? new TicketOwner(rs.getLong("user_id"), rs.getLong("channel_id"))
                            : null,
                    ticketId
            );
            if (owner == null) {
                return;
            }
            jdbcTemplate.update(
                    "INSERT INTO pending_feedback_requests "
                            + "(user_id, channel_id, ticket_id, source, created_at, expires_at) "
                            + "VALUES(?, ?, ?, ?, CURRENT_TIMESTAMP, datetime('now', '+1 day'))",
                    owner.userId,
                    owner.channelId,
                    ticketId,
                    source
            );
        } catch (DataAccessException ex) {
            log.warn("Unable to ensure pending feedback request for ticket {}: {}", ticketId, ex.getMessage());
        }
    }

    private record TicketOwner(long userId, long channelId) {
    }

    public ResolveResult reopenTicket(String ticketId, String operator) {
        if (!StringUtils.hasText(ticketId)) {
            return new ResolveResult(false, false, null);
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM tickets WHERE ticket_id = ?",
                    Integer.class,
                    ticketId
            );
            if (count == null || count == 0) {
                return new ResolveResult(false, false, null);
            }
            int updated = jdbcTemplate.update(
                    "UPDATE tickets SET status = 'pending', resolved_at = NULL, resolved_by = NULL, "
                            + "reopen_count = COALESCE(reopen_count, 0) + 1, "
                            + "last_reopen_at = CURRENT_TIMESTAMP "
                            + "WHERE ticket_id = ? AND status = 'resolved'",
                    ticketId
            );
            if (updated > 0 && StringUtils.hasText(operator)) {
                assignResponsibleIfMissing(ticketId, operator);
            }
            return new ResolveResult(updated > 0, true, null);
        } catch (DataAccessException ex) {
            log.warn("Unable to reopen ticket {}: {}", ticketId, ex.getMessage());
            return new ResolveResult(false, false, null);
        }
    }

    private static String buildPreview(Object message, Object messageType) {
        String base = value(message);
        if (StringUtils.hasText(base)) {
            return truncate(base.trim(), 140);
        }
        String type = value(messageType);
        if (StringUtils.hasText(type) && !"text".equalsIgnoreCase(type)) {
            return "[" + type.toLowerCase() + "]";
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (!StringUtils.hasText(value) || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "…";
    }

    private static String previewKey(Long channelId, Long telegramMessageId) {
        return channelId + ":" + telegramMessageId;
    }

    private static String toAttachmentUrl(String ticketId, String attachment) {
        if (!StringUtils.hasText(attachment)) {
            return null;
        }
        if (attachment.startsWith("/api/attachments/")) {
            return attachment;
        }
        if (attachment.startsWith("http://") || attachment.startsWith("https://")) {
            try {
                URI uri = URI.create(attachment);
                if (uri.getPath() != null && uri.getPath().startsWith("/api/attachments/")) {
                    String query = StringUtils.hasText(uri.getQuery()) ? "?" + uri.getQuery() : "";
                    String fragment = StringUtils.hasText(uri.getFragment()) ? "#" + uri.getFragment() : "";
                    return uri.getPath() + query + fragment;
                }
            } catch (IllegalArgumentException ignored) {
                return attachment;
            }
            return attachment;
        }
        try {
            java.nio.file.Path parsed = java.nio.file.Paths.get(attachment);
            String normalized = parsed.toString().replace('\\', '/');
            if (normalized.contains("/")) {
                return "/api/attachments/tickets/by-path?path=" + java.net.URLEncoder.encode(normalized, java.nio.charset.StandardCharsets.UTF_8);
            }
            String filename = parsed.getFileName().toString();
            return "/api/attachments/tickets/" + ticketId + "/" + filename;
        } catch (Exception ex) {
            return attachment;
        }
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String value(Object value) {
        return value != null ? value.toString() : null;
    }

    private List<String> normalizeCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return categories.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    public record ResolveResult(boolean updated, boolean exists, String error) {
    }
}

