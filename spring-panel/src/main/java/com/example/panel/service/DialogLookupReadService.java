package com.example.panel.service;

import com.example.panel.model.dialog.DialogChannelStat;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DialogLookupReadService {

    private static final Logger log = LoggerFactory.getLogger(DialogLookupReadService.class);

    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate usersJdbcTemplate;

    public DialogLookupReadService(JdbcTemplate jdbcTemplate,
                                   @Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.usersJdbcTemplate = usersJdbcTemplate;
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
            log.warn("Unable to load dialog summary, returning empty view: {}", DialogService.summarizeDataAccessException(ex));
            return new DialogSummary(0, 0, 0, List.of());
        }
    }

    public List<DialogListItem> loadDialogs(String currentOperator) {
        try {
            Set<String> feedbackColumns = loadTableColumns("feedbacks");
            boolean feedbackHasTicketId = feedbackColumns.contains("ticket_id");
            boolean feedbackHasId = feedbackColumns.contains("id");
            String feedbackOrderBy = feedbackHasTicketId ? "f.timestamp DESC, f.id DESC" : "f.timestamp DESC";
            String ratingSelect = feedbackHasTicketId
                    ? """
                       (
                           SELECT rating
                             FROM feedbacks f
                            WHERE f.ticket_id = m.ticket_id
                            ORDER BY %s
                            LIMIT 1
                       ) AS rating,
                       """
                    .formatted(feedbackOrderBy)
                    : "NULL AS rating,";
            String sql = """
                    SELECT t.ticket_id, m.group_msg_id AS request_number,
                           COALESCE(
                               m.user_id,
                               t.user_id,
                               (
                                   SELECT m3.user_id
                                     FROM messages m3
                                    WHERE m3.ticket_id = t.ticket_id
                                      AND m3.user_id IS NOT NULL
                                    ORDER BY substr(m3.created_at, 1, 19) DESC,
                                             m3.group_msg_id DESC
                                    LIMIT 1
                               )
                           ) AS user_id,
                           m.username, m.client_name, m.business,
                           COALESCE(m.channel_id, t.channel_id) AS channel_id,
                           c.channel_name AS channel_name,
                           m.city, m.location_name,
                           m.problem,
                           COALESCE(m.created_at, t.created_at) AS created_at,
                           t.status, t.resolved_by, t.resolved_at,
                           COALESCE(tas.is_processing, 0) AS ai_processing,
                           tr.responsible AS responsible,
                           COALESCE(m.created_date, substr(COALESCE(m.created_at, t.created_at), 1, 10)) AS created_date,
                           COALESCE(m.created_time, substr(COALESCE(m.created_at, t.created_at), 12, 8)) AS created_time,
                           cs.status AS client_status,
                           %s
                           (
                               SELECT sender
                                 FROM chat_history ch
                                WHERE ch.ticket_id = t.ticket_id
                                ORDER BY substr(ch.timestamp, 1, 19) DESC,
                                         COALESCE(ch.tg_message_id, 0) DESC,
                                         ch.id DESC
                                LIMIT 1
                           ) AS last_sender,
                           (
                               SELECT timestamp
                                 FROM chat_history ch
                                WHERE ch.ticket_id = t.ticket_id
                                ORDER BY substr(ch.timestamp, 1, 19) DESC,
                                         COALESCE(ch.tg_message_id, 0) DESC,
                                         ch.id DESC
                                LIMIT 1
                           ) AS last_sender_time,
                           (
                               SELECT GROUP_CONCAT(tc.category, ', ')
                                 FROM ticket_categories tc
                                WHERE tc.ticket_id = t.ticket_id
                           ) AS categories,
                           CASE
                               WHEN tr.responsible IS NULL OR trim(tr.responsible) = '' OR lower(COALESCE(tr.responsible, '')) = lower(?) THEN (
                                   SELECT COUNT(*)
                                     FROM chat_history ch
                                    WHERE ch.ticket_id = t.ticket_id
                                      AND lower(ch.sender) NOT IN ('operator', 'support', 'admin', 'system', 'ai_agent')
                                      AND ch.timestamp > COALESCE(
                                          tr.last_read_at,
                                          (
                                              SELECT MAX(op.timestamp)
                                                FROM chat_history op
                                               WHERE op.ticket_id = t.ticket_id
                                                 AND lower(op.sender) IN ('operator', 'support', 'admin', 'system', 'ai_agent')
                                          ),
                                          ''
                                      )
                               )
                               ELSE 0
                           END AS unread_count
                      FROM tickets t
                      LEFT JOIN messages m ON m.group_msg_id = (
                          SELECT m2.group_msg_id
                            FROM messages m2
                           WHERE m2.ticket_id = t.ticket_id
                           ORDER BY substr(m2.created_at, 1, 19) DESC,
                                    m2.group_msg_id DESC
                           LIMIT 1
                      )
                      LEFT JOIN channels c ON c.id = COALESCE(m.channel_id, t.channel_id)
                      LEFT JOIN ticket_responsibles tr ON tr.ticket_id = t.ticket_id
                      LEFT JOIN ticket_ai_agent_state tas ON tas.ticket_id = t.ticket_id
                      LEFT JOIN client_statuses cs ON cs.user_id = COALESCE(
                               m.user_id,
                               t.user_id,
                               (
                                   SELECT m3.user_id
                                     FROM messages m3
                                    WHERE m3.ticket_id = t.ticket_id
                                      AND m3.user_id IS NOT NULL
                                    ORDER BY substr(m3.created_at, 1, 19) DESC,
                                             m3.group_msg_id DESC
                                    LIMIT 1
                               )
                           )
                           AND cs.updated_at = (
                               SELECT MAX(updated_at) FROM client_statuses WHERE user_id = COALESCE(
                                   m.user_id,
                                   t.user_id,
                                   (
                                       SELECT m3.user_id
                                         FROM messages m3
                                        WHERE m3.ticket_id = t.ticket_id
                                          AND m3.user_id IS NOT NULL
                                        ORDER BY substr(m3.created_at, 1, 19) DESC,
                                                 m3.group_msg_id DESC
                                        LIMIT 1
                                   )
                               )
                           )
                      ORDER BY substr(COALESCE(
                                   (
                                       SELECT timestamp
                                         FROM chat_history ch
                                        WHERE ch.ticket_id = t.ticket_id
                                        ORDER BY substr(ch.timestamp, 1, 19) DESC,
                                                 COALESCE(ch.tg_message_id, 0) DESC,
                                                 ch.id DESC
                                        LIMIT 1
                                   ),
                                   COALESCE(m.created_at, t.created_at)
                               ), 1, 19) DESC,
                               t.ticket_id DESC
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
                    rs.getObject("ai_processing") != null && rs.getInt("ai_processing") > 0,
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
                    rs.getString("categories"),
                    null,
                    null
            ), currentOperator);
            return enrichResponsibleProfiles(items);
        } catch (DataAccessException ex) {
            log.warn("Unable to load dialogs, returning empty list: {}", DialogService.summarizeDataAccessException(ex));
            return List.of();
        }
    }

    public Optional<DialogListItem> findDialog(String ticketId, String operator) {
        try {
            Set<String> feedbackColumns = loadTableColumns("feedbacks");
            boolean feedbackHasTicketId = feedbackColumns.contains("ticket_id");
            boolean feedbackHasId = feedbackColumns.contains("id");
            String feedbackOrderBy = feedbackHasTicketId ? "f.timestamp DESC, f.id DESC" : "f.timestamp DESC";
            String ratingSelect = feedbackHasTicketId
                    ? """
                       (
                           SELECT rating
                             FROM feedbacks f
                            WHERE f.ticket_id = m.ticket_id
                            ORDER BY %s
                            LIMIT 1
                       ) AS rating,
                       """
                    .formatted(feedbackOrderBy)
                    : "NULL AS rating,";
            String sql = """
                    SELECT t.ticket_id, m.group_msg_id AS request_number,
                           COALESCE(
                               m.user_id,
                               t.user_id,
                               (
                                   SELECT m3.user_id
                                     FROM messages m3
                                    WHERE m3.ticket_id = t.ticket_id
                                      AND m3.user_id IS NOT NULL
                                    ORDER BY substr(m3.created_at, 1, 19) DESC,
                                             m3.group_msg_id DESC
                                    LIMIT 1
                               )
                           ) AS user_id,
                           m.username, m.client_name, m.business,
                           COALESCE(m.channel_id, t.channel_id) AS channel_id,
                           c.channel_name AS channel_name,
                           m.city, m.location_name,
                           m.problem,
                           COALESCE(m.created_at, t.created_at) AS created_at,
                           t.status, t.resolved_by, t.resolved_at,
                           COALESCE(tas.is_processing, 0) AS ai_processing,
                           tr.responsible AS responsible,
                           COALESCE(m.created_date, substr(COALESCE(m.created_at, t.created_at), 1, 10)) AS created_date,
                           COALESCE(m.created_time, substr(COALESCE(m.created_at, t.created_at), 12, 8)) AS created_time,
                           cs.status AS client_status,
                           %s
                           (
                               SELECT sender
                                 FROM chat_history ch
                                WHERE ch.ticket_id = t.ticket_id
                                ORDER BY substr(ch.timestamp, 1, 19) DESC,
                                         COALESCE(ch.tg_message_id, 0) DESC,
                                         ch.id DESC
                                LIMIT 1
                           ) AS last_sender,
                           (
                               SELECT timestamp
                                 FROM chat_history ch
                                WHERE ch.ticket_id = t.ticket_id
                                ORDER BY substr(ch.timestamp, 1, 19) DESC,
                                         COALESCE(ch.tg_message_id, 0) DESC,
                                         ch.id DESC
                                LIMIT 1
                           ) AS last_sender_time,
                           (
                               SELECT GROUP_CONCAT(tc.category, ', ')
                                 FROM ticket_categories tc
                                WHERE tc.ticket_id = t.ticket_id
                           ) AS categories,
                           CASE
                               WHEN tr.responsible IS NULL OR trim(tr.responsible) = '' OR lower(COALESCE(tr.responsible, '')) = lower(?) THEN (
                                   SELECT COUNT(*)
                                     FROM chat_history ch
                                    WHERE ch.ticket_id = t.ticket_id
                                      AND lower(ch.sender) NOT IN ('operator', 'support', 'admin', 'system', 'ai_agent')
                                      AND ch.timestamp > COALESCE(
                                          tr.last_read_at,
                                          (
                                              SELECT MAX(op.timestamp)
                                                FROM chat_history op
                                               WHERE op.ticket_id = t.ticket_id
                                                 AND lower(op.sender) IN ('operator', 'support', 'admin', 'system', 'ai_agent')
                                          ),
                                          ''
                                      )
                               )
                               ELSE 0
                           END AS unread_count
                      FROM tickets t
                      LEFT JOIN messages m ON m.group_msg_id = (
                          SELECT m2.group_msg_id
                            FROM messages m2
                           WHERE m2.ticket_id = t.ticket_id
                           ORDER BY substr(m2.created_at, 1, 19) DESC,
                                    m2.group_msg_id DESC
                           LIMIT 1
                      )
                      LEFT JOIN channels c ON c.id = COALESCE(m.channel_id, t.channel_id)
                      LEFT JOIN ticket_responsibles tr ON tr.ticket_id = t.ticket_id
                      LEFT JOIN ticket_ai_agent_state tas ON tas.ticket_id = t.ticket_id
                      LEFT JOIN client_statuses cs ON cs.user_id = COALESCE(
                               m.user_id,
                               t.user_id,
                               (
                                   SELECT m3.user_id
                                     FROM messages m3
                                    WHERE m3.ticket_id = t.ticket_id
                                      AND m3.user_id IS NOT NULL
                                    ORDER BY substr(m3.created_at, 1, 19) DESC,
                                             m3.group_msg_id DESC
                                    LIMIT 1
                               )
                           )
                           AND cs.updated_at = (
                               SELECT MAX(updated_at) FROM client_statuses WHERE user_id = COALESCE(
                                   m.user_id,
                                   t.user_id,
                                   (
                                       SELECT m3.user_id
                                         FROM messages m3
                                        WHERE m3.ticket_id = t.ticket_id
                                          AND m3.user_id IS NOT NULL
                                        ORDER BY substr(m3.created_at, 1, 19) DESC,
                                                 m3.group_msg_id DESC
                                        LIMIT 1
                                   )
                               )
                           )
                     WHERE t.ticket_id = ?
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
                    rs.getObject("ai_processing") != null && rs.getInt("ai_processing") > 0,
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
                    rs.getString("categories"),
                    null,
                    null
            ), operator, ticketId);
            List<DialogListItem> enriched = enrichResponsibleProfiles(items);
            return enriched.isEmpty() ? Optional.empty() : Optional.of(enriched.get(0));
        } catch (DataAccessException ex) {
            log.warn("Unable to load dialog {} details: {}", ticketId, DialogService.summarizeDataAccessException(ex));
            return Optional.empty();
        }
    }

    private List<DialogListItem> enrichResponsibleProfiles(List<DialogListItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<String, ResponsibleProfile> profileByIdentity = loadResponsibleProfiles(items);
        if (profileByIdentity.isEmpty()) {
            return items;
        }
        List<DialogListItem> enriched = new ArrayList<>(items.size());
        for (DialogListItem item : items) {
            String identity = resolveResponsibleIdentity(item);
            ResponsibleProfile profile = identity != null ? profileByIdentity.get(identity) : null;
            if (profile == null) {
                enriched.add(item);
                continue;
            }
            enriched.add(new DialogListItem(
                    item.ticketId(),
                    item.requestNumber(),
                    item.userId(),
                    item.username(),
                    item.clientName(),
                    item.business(),
                    item.channelId(),
                    item.channelName(),
                    item.city(),
                    item.locationName(),
                    item.problem(),
                    item.createdAt(),
                    item.status(),
                    item.aiProcessing(),
                    item.resolvedBy(),
                    item.resolvedAt(),
                    item.rawResponsible(),
                    item.createdDate(),
                    item.createdTime(),
                    item.clientStatus(),
                    item.lastMessageSender(),
                    item.lastMessageTimestamp(),
                    item.unreadCount(),
                    item.rating(),
                    item.categories(),
                    profile.displayName(),
                    profile.avatarUrl()
            ));
        }
        return enriched;
    }

    private Map<String, ResponsibleProfile> loadResponsibleProfiles(List<DialogListItem> items) {
        Set<String> identities = new LinkedHashSet<>();
        for (DialogListItem item : items) {
            String identity = resolveResponsibleIdentity(item);
            if (identity != null) {
                identities.add(identity);
            }
        }
        if (identities.isEmpty()) {
            return Map.of();
        }
        try {
            Set<String> userColumns = loadUsersTableColumns();
            String fullNameSelect = userColumns.contains("full_name")
                    ? "full_name"
                    : "NULL AS full_name";
            String photoSelect = userColumns.contains("photo")
                    ? "photo"
                    : "NULL AS photo";
            String placeholders = identities.stream().map(identity -> "?").collect(Collectors.joining(", "));
            String sql = """
                    SELECT username, %s, %s
                      FROM users
                     WHERE lower(username) IN (%s)
                    """.formatted(fullNameSelect, photoSelect, placeholders);
            Map<String, ResponsibleProfile> profiles = new LinkedHashMap<>();
            usersJdbcTemplate.query(sql, rs -> {
                while (rs.next()) {
                    String username = normalizeIdentity(rs.getString("username"));
                    if (username == null) {
                        continue;
                    }
                    String displayName = trimToNull(rs.getString("full_name"));
                    if (displayName == null) {
                        displayName = trimToNull(rs.getString("username"));
                    }
                    profiles.put(username, new ResponsibleProfile(displayName, resolveAvatarUrl(rs.getString("photo"))));
                }
            }, identities.toArray());
            return profiles;
        } catch (DataAccessException ex) {
            log.warn("Unable to load responsible profiles for dialog list: {}", DialogService.summarizeDataAccessException(ex));
            return Map.of();
        }
    }

    private Set<String> loadUsersTableColumns() {
        try {
            return usersJdbcTemplate.execute((ConnectionCallback<Set<String>>) connection -> {
                Set<String> columns = new java.util.HashSet<>();
                var metaData = connection.getMetaData();
                try (var resultSet = metaData.getColumns(null, null, "users", null)) {
                    while (resultSet.next()) {
                        columns.add(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                    }
                }
                if (!columns.isEmpty()) {
                    return columns;
                }
                try (var resultSet = metaData.getColumns(null, null, "USERS", null)) {
                    while (resultSet.next()) {
                        columns.add(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                    }
                }
                return columns;
            });
        } catch (DataAccessException ex) {
            log.warn("Unable to inspect users table columns: {}", DialogService.summarizeDataAccessException(ex));
            return Set.of();
        }
    }

    private Set<String> loadTableColumns(String tableName) {
        try {
            return jdbcTemplate.execute((ConnectionCallback<Set<String>>) connection -> {
                Set<String> columns = new java.util.HashSet<>();
                var metaData = connection.getMetaData();
                try (var resultSet = metaData.getColumns(null, null, tableName, null)) {
                    while (resultSet.next()) {
                        columns.add(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                    }
                }
                if (!columns.isEmpty()) {
                    return columns;
                }
                try (var resultSet = metaData.getColumns(null, null, tableName.toUpperCase(Locale.ROOT), null)) {
                    while (resultSet.next()) {
                        columns.add(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                    }
                }
                return columns;
            });
        } catch (DataAccessException ex) {
            log.warn("Unable to inspect {} columns: {}", tableName, DialogService.summarizeDataAccessException(ex));
            return Set.of();
        }
    }

    private String resolveResponsibleIdentity(DialogListItem item) {
        if (item == null) {
            return null;
        }
        String responsibleIdentity = normalizeIdentity(item.rawResponsible());
        if (responsibleIdentity != null) {
            return responsibleIdentity;
        }
        String resolvedByIdentity = normalizeIdentity(item.resolvedBy());
        if (resolvedByIdentity != null && !resolvedByIdentity.contains("auto") && !resolvedByIdentity.contains("авто")) {
            return resolvedByIdentity;
        }
        return null;
    }

    private String normalizeIdentity(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String resolveAvatarUrl(String photo) {
        if (!StringUtils.hasText(photo)) {
            return null;
        }
        String trimmed = photo.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("data:")) {
            return trimmed;
        }
        if (trimmed.startsWith("/")) {
            return trimmed;
        }
        return "/avatars/" + trimmed;
    }

    private record ResponsibleProfile(String displayName, String avatarUrl) {
    }
}
