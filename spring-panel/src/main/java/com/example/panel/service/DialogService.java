package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogChannelStat;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class DialogService {

    private final JdbcTemplate jdbcTemplate;

    public DialogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DialogSummary loadSummary() {
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
    }

    public List<DialogListItem> loadDialogs() {
        String sql = """
                SELECT m.ticket_id, m.user_id, m.username, m.client_name, m.business, m.city, m.location_name,
                       m.problem, m.created_at, t.status, t.resolved_by, t.resolved_at,
                       m.created_date, m.created_time, cs.status AS client_status
                  FROM messages m
                  LEFT JOIN tickets t ON m.ticket_id = t.ticket_id
                  LEFT JOIN client_statuses cs ON cs.user_id = m.user_id
                       AND cs.updated_at = (
                           SELECT MAX(updated_at) FROM client_statuses WHERE user_id = m.user_id
                       )
                 ORDER BY m.created_at DESC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new DialogListItem(
                rs.getString("ticket_id"),
                rs.getObject("user_id") != null ? rs.getLong("user_id") : null,
                rs.getString("username"),
                rs.getString("client_name"),
                rs.getString("business"),
                rs.getString("city"),
                rs.getString("location_name"),
                rs.getString("problem"),
                rs.getString("created_at"),
                rs.getString("status"),
                rs.getString("resolved_by"),
                rs.getString("resolved_at"),
                rs.getString("created_date"),
                rs.getString("created_time"),
                rs.getString("client_status")
        ));
    }

    public Optional<DialogListItem> findDialog(String ticketId) {
        String sql = """
                SELECT m.ticket_id, m.user_id, m.username, m.client_name, m.business, m.city, m.location_name,
                       m.problem, m.created_at, t.status, t.resolved_by, t.resolved_at,
                       m.created_date, m.created_time, cs.status AS client_status
                  FROM messages m
                  LEFT JOIN tickets t ON m.ticket_id = t.ticket_id
                  LEFT JOIN client_statuses cs ON cs.user_id = m.user_id
                       AND cs.updated_at = (
                           SELECT MAX(updated_at) FROM client_statuses WHERE user_id = m.user_id
                       )
                 WHERE m.ticket_id = ?
                 ORDER BY m.created_at DESC
                 LIMIT 1
                """;
        List<DialogListItem> items = jdbcTemplate.query(sql, (rs, rowNum) -> new DialogListItem(
                rs.getString("ticket_id"),
                rs.getObject("user_id") != null ? rs.getLong("user_id") : null,
                rs.getString("username"),
                rs.getString("client_name"),
                rs.getString("business"),
                rs.getString("city"),
                rs.getString("location_name"),
                rs.getString("problem"),
                rs.getString("created_at"),
                rs.getString("status"),
                rs.getString("resolved_by"),
                rs.getString("resolved_at"),
                rs.getString("created_date"),
                rs.getString("created_time"),
                rs.getString("client_status")
        ), ticketId);
        return items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
    }

    public List<ChatMessageDto> loadHistory(String ticketId, Long channelId) {
        if (!StringUtils.hasText(ticketId)) {
            return Collections.emptyList();
        }
        String baseSql = """
                SELECT sender, message, timestamp, message_type, attachment,
                       tg_message_id, reply_to_tg_id, channel_id
                  FROM chat_history
                 WHERE ticket_id = ?
                """;
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
            history.add(new ChatMessageDto(
                    value(row.get("sender")),
                    value(row.get("message")),
                    value(row.get("timestamp")),
                    value(row.get("message_type")),
                    value(row.get("attachment")),
                    toLong(row.get("tg_message_id")),
                    replyTo,
                    replyPreview
            ));
        }
        return history;
    }

    public Optional<DialogDetails> loadDialogDetails(String ticketId, Long channelId) {
        return findDialog(ticketId).map(item -> new DialogDetails(item, loadHistory(ticketId, channelId)));
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
}
