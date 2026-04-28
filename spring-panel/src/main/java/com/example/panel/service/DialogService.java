package com.example.panel.service;

import com.example.panel.model.dialog.ChatMessageDto;
import com.example.panel.model.dialog.DialogChannelStat;
import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogPreviousHistoryBatch;
import com.example.panel.model.dialog.DialogPreviousHistoryPage;
import com.example.panel.model.dialog.DialogSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DialogService {

    private static final Logger log = LoggerFactory.getLogger(DialogService.class);
    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate usersJdbcTemplate;
    private final SharedConfigService sharedConfigService;
    private final DialogWorkspaceTelemetryDataService dialogWorkspaceTelemetryDataService;
    private final DialogWorkspaceTelemetryAnalyticsService dialogWorkspaceTelemetryAnalyticsService;
    private final DialogWorkspaceExternalKpiService dialogWorkspaceExternalKpiService;
    private final DialogMacroGovernanceAuditService dialogMacroGovernanceAuditService;
    private final DialogLookupReadService dialogLookupReadService;
    private final DialogResponsibilityService dialogResponsibilityService;
    private final DialogClientContextReadService dialogClientContextReadService;
    private final DialogConversationReadService dialogConversationReadService;
    private final DialogDetailsReadService dialogDetailsReadService;
    private final DialogAuditService dialogAuditService;
    private final DialogTicketLifecycleService dialogTicketLifecycleService;

    public DialogService(JdbcTemplate jdbcTemplate,
                         @Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate,
                         SharedConfigService sharedConfigService,
                         DialogWorkspaceTelemetryDataService dialogWorkspaceTelemetryDataService,
                         DialogWorkspaceTelemetryAnalyticsService dialogWorkspaceTelemetryAnalyticsService,
                         DialogWorkspaceExternalKpiService dialogWorkspaceExternalKpiService,
                         DialogMacroGovernanceAuditService dialogMacroGovernanceAuditService,
                         DialogLookupReadService dialogLookupReadService,
                         DialogResponsibilityService dialogResponsibilityService,
                         DialogClientContextReadService dialogClientContextReadService,
                         DialogConversationReadService dialogConversationReadService,
                         DialogDetailsReadService dialogDetailsReadService,
                         DialogAuditService dialogAuditService,
                         DialogTicketLifecycleService dialogTicketLifecycleService) {
        this.jdbcTemplate = jdbcTemplate;
        this.usersJdbcTemplate = usersJdbcTemplate;
        this.sharedConfigService = sharedConfigService;
        this.dialogWorkspaceTelemetryDataService = dialogWorkspaceTelemetryDataService;
        this.dialogWorkspaceTelemetryAnalyticsService = dialogWorkspaceTelemetryAnalyticsService;
        this.dialogWorkspaceExternalKpiService = dialogWorkspaceExternalKpiService;
        this.dialogMacroGovernanceAuditService = dialogMacroGovernanceAuditService;
        this.dialogLookupReadService = dialogLookupReadService;
        this.dialogResponsibilityService = dialogResponsibilityService;
        this.dialogClientContextReadService = dialogClientContextReadService;
        this.dialogConversationReadService = dialogConversationReadService;
        this.dialogDetailsReadService = dialogDetailsReadService;
        this.dialogAuditService = dialogAuditService;
        this.dialogTicketLifecycleService = dialogTicketLifecycleService;
    }

    public DialogSummary loadSummary() {
        return dialogLookupReadService.loadSummary();
    }

    public List<DialogListItem> loadDialogs(String currentOperator) {
        return dialogLookupReadService.loadDialogs(currentOperator);
    }

    private List<DialogListItem> loadDialogsLegacy(String currentOperator) {
        try {
            Set<String> feedbackColumns = loadTableColumns("feedbacks");
            boolean feedbackHasTicketId = feedbackColumns.contains("ticket_id");
            boolean feedbackHasId = feedbackColumns.contains("id");
            String feedbackOrderBy = feedbackHasId ? "f.timestamp DESC, f.id DESC" : "f.timestamp DESC";
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
                  ORDER BY substr(COALESCE(m.created_at, t.created_at), 1, 19) DESC,
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
            log.warn("Unable to load dialogs, returning empty list: {}", DialogDataAccessSupport.summarizeDataAccessException(ex));
            return List.of();
        }
    }

    public Optional<DialogListItem> findDialog(String ticketId, String operator) {
        return dialogLookupReadService.findDialog(ticketId, operator);
    }

    private Optional<DialogListItem> findDialogLegacy(String ticketId, String operator) {
        try {
            Set<String> feedbackColumns = loadTableColumns("feedbacks");
            boolean feedbackHasTicketId = feedbackColumns.contains("ticket_id");
            boolean feedbackHasId = feedbackColumns.contains("id");
            String feedbackOrderBy = feedbackHasId ? "f.timestamp DESC, f.id DESC" : "f.timestamp DESC";
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
                     ORDER BY substr(COALESCE(m.created_at, t.created_at), 1, 19) DESC
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
            log.warn("Unable to load dialog {} details: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
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
            log.warn("Unable to load responsible profiles for dialog list: {}", DialogDataAccessSupport.summarizeDataAccessException(ex));
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
            log.warn("Unable to inspect users table columns: {}", DialogDataAccessSupport.summarizeDataAccessException(ex));
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

    public void assignResponsibleIfMissing(String ticketId, String username) {
        dialogResponsibilityService.assignResponsibleIfMissing(ticketId, username);
    }

    private void assignResponsibleIfMissingLegacy(String ticketId, String username) {
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
            log.warn("Unable to assign responsible for ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
    }

    public void markDialogAsRead(String ticketId, String operator) {
        dialogResponsibilityService.markDialogAsRead(ticketId, operator);
    }

    private void markDialogAsReadLegacy(String ticketId, String operator) {
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
            log.warn("Unable to mark dialog {} as read for {}: {}", ticketId, operator, DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
    }

    public void assignResponsibleIfMissingOrRedirected(String ticketId, String newResponsible, String assignedBy) {
        dialogResponsibilityService.assignResponsibleIfMissingOrRedirected(ticketId, newResponsible, assignedBy);
    }

    private void assignResponsibleIfMissingOrRedirectedLegacy(String ticketId, String newResponsible, String assignedBy) {
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
            log.warn("Unable to update responsible for ticket {}: {}", ticketId, DialogDataAccessSupport.summarizeDataAccessException(ex));
        }
    }

    public List<ChatMessageDto> loadHistory(String ticketId, Long channelId) {
        return dialogConversationReadService.loadHistory(ticketId, channelId);
    }

    public Optional<DialogDetails> loadDialogDetails(String ticketId, Long channelId, String operator) {
        return dialogDetailsReadService.loadDialogDetails(ticketId, channelId, operator);
    }

    public Optional<DialogPreviousHistoryPage> loadPreviousDialogHistory(String ticketId, int offset) {
        return dialogConversationReadService.loadPreviousDialogHistory(ticketId, offset);
    }

    public List<Map<String, Object>> loadClientDialogHistory(Long userId, String currentTicketId, int limit) {
        return dialogClientContextReadService.loadClientDialogHistory(userId, currentTicketId, limit);
    }

    public Map<String, Object> loadClientProfileEnrichment(Long userId) {
        return dialogClientContextReadService.loadClientProfileEnrichment(userId);
    }

    public Map<String, Object> loadDialogProfileMatchCandidates(Map<String, String> incomingValues, int perFieldLimit) {
        return dialogClientContextReadService.loadDialogProfileMatchCandidates(incomingValues, perFieldLimit);
    }

    public List<Map<String, Object>> loadRelatedEvents(String ticketId, int limit) {
        return dialogClientContextReadService.loadRelatedEvents(ticketId, limit);
    }

    public void logDialogActionAudit(String ticketId, String actor, String action, String result, String detail) {
        dialogAuditService.logDialogActionAudit(ticketId, actor, action, result, detail);
    }

    public void logWorkspaceTelemetry(String actor,
                                      String eventType,
                                      String eventGroup,
                                      String ticketId,
                                      String reason,
                                      String errorCode,
                                      String contractVersion,
                                      Long durationMs,
                                      String experimentName,
                                      String experimentCohort,
                                      String operatorSegment,
                                      List<String> primaryKpis,
                                      List<String> secondaryKpis,
                                      String templateId,
                                      String templateName) {
        dialogAuditService.logWorkspaceTelemetry(
                actor,
                eventType,
                eventGroup,
                ticketId,
                reason,
                errorCode,
                contractVersion,
                durationMs,
                experimentName,
                experimentCohort,
                operatorSegment,
                primaryKpis,
                secondaryKpis,
                templateId,
                templateName);
    }

    public Map<String, Object> loadWorkspaceTelemetrySummary(int days, String experimentName) {
        Map<String, Object> workspaceTelemetryConfig = dialogWorkspaceTelemetryAnalyticsService.resolveWorkspaceTelemetryConfig();
        int windowDays = Math.max(1, Math.min(days, 30));
        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minusSeconds(windowDays * 24L * 60L * 60L);
        Instant previousWindowEnd = windowStart;
        Instant previousWindowStart = previousWindowEnd.minusSeconds(windowDays * 24L * 60L * 60L);
        List<Map<String, Object>> rows = dialogWorkspaceTelemetryDataService.loadWorkspaceTelemetryRows(windowStart, windowEnd, experimentName);
        List<Map<String, Object>> previousRows = dialogWorkspaceTelemetryDataService.loadWorkspaceTelemetryRows(previousWindowStart, previousWindowEnd, experimentName);
        List<Map<String, Object>> shiftRows = dialogWorkspaceTelemetryDataService.aggregateWorkspaceTelemetryRows(rows, "shift");
        List<Map<String, Object>> teamRows = dialogWorkspaceTelemetryDataService.aggregateWorkspaceTelemetryRows(rows, "team");
        Map<String, Object> totals = dialogWorkspaceTelemetryAnalyticsService.computeWorkspaceTelemetryTotals(rows);
        Map<String, Object> previousTotals = dialogWorkspaceTelemetryAnalyticsService.computeWorkspaceTelemetryTotals(previousRows);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("window_days", windowDays);
        payload.put("window_from_utc", windowStart.toString());
        payload.put("window_to_utc", windowEnd.toString());
        payload.put("generated_at", Instant.now().toString());
        payload.put("totals", totals);
        payload.put("previous_totals", previousTotals);
        payload.put("period_comparison", dialogWorkspaceTelemetryAnalyticsService.buildWorkspaceTelemetryComparison(totals, previousTotals));
        Map<String, Object> cohortComparison = dialogWorkspaceTelemetryAnalyticsService.buildWorkspaceCohortComparison(rows, workspaceTelemetryConfig);
        payload.put("cohort_comparison", cohortComparison);
        payload.put("rows", rows);
        payload.put("by_shift", shiftRows);
        payload.put("by_team", teamRows);
        payload.put("gap_breakdown", dialogWorkspaceTelemetryDataService.loadWorkspaceGapBreakdown(windowStart, windowEnd, experimentName));
        Map<String, Object> guardrails = dialogWorkspaceTelemetryAnalyticsService.buildWorkspaceGuardrails(
                totals, previousTotals, rows, shiftRows, teamRows, workspaceTelemetryConfig);
        payload.put("guardrails", guardrails);
        Map<String, Object> rolloutDecision = buildWorkspaceRolloutDecision(cohortComparison, guardrails);
        payload.put("rollout_decision", rolloutDecision);
        Map<String, Object> rolloutScorecard = buildWorkspaceRolloutScorecard(totals, cohortComparison, guardrails, rolloutDecision);
        payload.put("rollout_scorecard", rolloutScorecard);
        payload.put("rollout_packet", buildWorkspaceRolloutPacket(totals, guardrails, rolloutDecision, rolloutScorecard,
                payload.get("gap_breakdown"), windowDays, experimentName));
        return payload;
    }

    public Map<String, Object> loadWorkspaceTelemetrySummary(int days,
                                                             String experimentName,
                                                             Instant fromUtc,
                                                             Instant toUtc) {
        Instant resolvedEnd = toUtc != null ? toUtc : Instant.now();
        int fallbackWindowDays = Math.max(1, Math.min(days, 30));
        Instant resolvedStart = fromUtc != null
                ? fromUtc
                : resolvedEnd.minusSeconds(fallbackWindowDays * 24L * 60L * 60L);
        if (!resolvedStart.isBefore(resolvedEnd)) {
            resolvedStart = resolvedEnd.minusSeconds(fallbackWindowDays * 24L * 60L * 60L);
        }
        long rangeSeconds = Math.max(1L, Duration.between(resolvedStart, resolvedEnd).getSeconds());
        long windowDaysRaw = Math.max(1L, (long) Math.ceil((double) rangeSeconds / (24d * 60d * 60d)));
        int windowDays = (int) Math.max(1L, Math.min(30L, windowDaysRaw));

        Instant previousWindowEnd = resolvedStart;
        Instant previousWindowStart = previousWindowEnd.minusSeconds(rangeSeconds);

        Map<String, Object> workspaceTelemetryConfig = dialogWorkspaceTelemetryAnalyticsService.resolveWorkspaceTelemetryConfig();
        List<Map<String, Object>> rows = dialogWorkspaceTelemetryDataService.loadWorkspaceTelemetryRows(resolvedStart, resolvedEnd, experimentName);
        List<Map<String, Object>> previousRows = dialogWorkspaceTelemetryDataService.loadWorkspaceTelemetryRows(previousWindowStart, previousWindowEnd, experimentName);
        List<Map<String, Object>> shiftRows = dialogWorkspaceTelemetryDataService.aggregateWorkspaceTelemetryRows(rows, "shift");
        List<Map<String, Object>> teamRows = dialogWorkspaceTelemetryDataService.aggregateWorkspaceTelemetryRows(rows, "team");
        Map<String, Object> totals = dialogWorkspaceTelemetryAnalyticsService.computeWorkspaceTelemetryTotals(rows);
        Map<String, Object> previousTotals = dialogWorkspaceTelemetryAnalyticsService.computeWorkspaceTelemetryTotals(previousRows);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("window_days", windowDays);
        payload.put("window_from_utc", resolvedStart.toString());
        payload.put("window_to_utc", resolvedEnd.toString());
        payload.put("generated_at", Instant.now().toString());
        payload.put("totals", totals);
        payload.put("previous_totals", previousTotals);
        payload.put("period_comparison", dialogWorkspaceTelemetryAnalyticsService.buildWorkspaceTelemetryComparison(totals, previousTotals));
        Map<String, Object> cohortComparison = dialogWorkspaceTelemetryAnalyticsService.buildWorkspaceCohortComparison(rows, workspaceTelemetryConfig);
        payload.put("cohort_comparison", cohortComparison);
        payload.put("rows", rows);
        payload.put("by_shift", shiftRows);
        payload.put("by_team", teamRows);
        payload.put("gap_breakdown", dialogWorkspaceTelemetryDataService.loadWorkspaceGapBreakdown(resolvedStart, resolvedEnd, experimentName));
        Map<String, Object> guardrails = dialogWorkspaceTelemetryAnalyticsService.buildWorkspaceGuardrails(
                totals, previousTotals, rows, shiftRows, teamRows, workspaceTelemetryConfig);
        payload.put("guardrails", guardrails);
        Map<String, Object> rolloutDecision = buildWorkspaceRolloutDecision(cohortComparison, guardrails);
        payload.put("rollout_decision", rolloutDecision);
        Map<String, Object> rolloutScorecard = buildWorkspaceRolloutScorecard(totals, cohortComparison, guardrails, rolloutDecision);
        payload.put("rollout_scorecard", rolloutScorecard);
        payload.put("rollout_packet", buildWorkspaceRolloutPacket(
                totals,
                guardrails,
                rolloutDecision,
                rolloutScorecard,
                payload.get("gap_breakdown"),
                windowDays,
                experimentName));
        return payload;
    }

    public Map<String, Object> buildMacroGovernanceAudit(Map<String, Object> settings) {
        return dialogMacroGovernanceAuditService.buildAudit(settings);
    }

    private Map<String, Object> buildWorkspaceRolloutScorecard(Map<String, Object> totals,
                                                               Map<String, Object> cohortComparison,
                                                               Map<String, Object> guardrails,
                                                               Map<String, Object> rolloutDecision) {
        Map<String, Object> safeTotals = totals == null ? Map.of() : totals;
        Map<String, Object> safeCohortComparison = cohortComparison == null ? Map.of() : cohortComparison;
        Map<String, Object> safeGuardrails = guardrails == null ? Map.of() : guardrails;
        Map<String, Object> safeRolloutDecision = rolloutDecision == null ? Map.of() : rolloutDecision;
        Map<String, Object> kpiSignal = safeCohortComparison.get("kpi_signal") instanceof Map<?, ?> value
                ? castObjectMap(value)
                : Map.of();
        Map<String, Object> outcomeSignal = safeCohortComparison.get("kpi_outcome_signal") instanceof Map<?, ?> value
                ? castObjectMap(value)
                : Map.of();
        Map<String, Object> externalSignal = safeRolloutDecision.get("external_kpi_signal") instanceof Map<?, ?> value
                ? castObjectMap(value)
                : Map.of();

        List<Map<String, Object>> items = new ArrayList<>();
        long sampleMin = toLong(safeCohortComparison.get("sample_size_min_events"));
        items.add(buildScorecardItem(
                "sample_size",
                "experiment",
                "Control/Test sample size",
                toBoolean(safeCohortComparison.get("sample_size_ok")) ? "ok" : "hold",
                true,
                "Нужно достаточно событий в обеих когортах до принятия rollout decision.",
                "control=%d, test=%d".formatted(
                        toLong(safeCohortComparison.get("control_events")),
                        toLong(safeCohortComparison.get("test_events"))),
                sampleMin > 0 ? ">= %d событий на когорту".formatted(sampleMin) : "Дефолтный порог",
                null,
                null
        ));

        String guardrailStatus = "attention".equalsIgnoreCase(String.valueOf(safeGuardrails.get("status")))
                ? "attention"
                : "ok";
        items.add(buildScorecardItem(
                "guardrails",
                "guardrails",
                "Technical guardrails",
                guardrailStatus,
                "attention".equals(guardrailStatus),
                "Render error / fallback / abandon / slow open не должны уходить в attention.",
                "alerts=%d".formatted(safeListOfMaps(safeGuardrails.get("alerts")).size()),
                "status=ok",
                null,
                null
        ));

        long minKpiEvents = toLong(kpiSignal.get("min_events_per_cohort"));
        double minCoverage = kpiSignal.get("min_coverage_rate_per_cohort") instanceof Number number
                ? number.doubleValue()
                : 0d;
        items.add(buildScorecardItem(
                "primary_kpi_signal",
                "product_kpi",
                "Primary KPI coverage",
                toBoolean(kpiSignal.get("ready_for_decision")) ? "ok" : "hold",
                true,
                "FRT / TTR / SLA breach должны иметь достаточно покрытия и событий в control/test.",
                "required=%s".formatted(String.join(", ", safeStringList(kpiSignal.get("required_kpis")))),
                "events>=%d, coverage>=%.1f%%".formatted(minKpiEvents, minCoverage * 100d),
                null,
                null
        ));

        Map<String, Object> outcomeMetrics = outcomeSignal.get("metrics") instanceof Map<?, ?> value
                ? castObjectMap(value)
                : Map.of();
        appendOutcomeMetricScorecardItem(items, outcomeMetrics, "frt", "Outcome KPI: FRT");
        appendOutcomeMetricScorecardItem(items, outcomeMetrics, "ttr", "Outcome KPI: TTR");
        appendOutcomeMetricScorecardItem(items, outcomeMetrics, "sla_breach", "Outcome KPI: SLA breach");

        double contextReadyRate = safeDouble(safeTotals.get("context_profile_ready_rate"));
        items.add(buildScorecardItem(
                "customer_context",
                "workspace",
                "Customer context readiness",
                contextReadyRate >= 0.95d ? "ok" : "hold",
                false,
                "Контекст клиента должен быть готов без постоянного fallback в сторонние экраны.",
                "%.1f%% ready".formatted(contextReadyRate * 100d),
                ">= 95.0%",
                null,
                null
        ));

        double contextSourceReadyRate = safeDouble(safeTotals.get("context_source_ready_rate"));
        items.add(buildScorecardItem(
                "customer_context_sources",
                "workspace",
                "Customer context sources",
                contextSourceReadyRate >= 0.95d ? "ok" : "attention",
                false,
                "Обязательные CRM/contract/external источники должны быть подключены и не иметь source-gap.",
                "%.1f%% ready".formatted(contextSourceReadyRate * 100d),
                ">= 95.0%",
                null,
                null
        ));

        double contextAttributePolicyReadyRate = safeDouble(safeTotals.get("context_attribute_policy_ready_rate"));
        items.add(buildScorecardItem(
                "customer_context_attribute_policy",
                "workspace",
                "Customer profile source/freshness policy",
                contextAttributePolicyReadyRate >= 0.95d ? "ok" : "attention",
                false,
                "Mandatory customer profile должен иметь формализованный source-of-truth и валидную UTC freshness policy.",
                "%.1f%% ready".formatted(contextAttributePolicyReadyRate * 100d),
                ">= 95.0%",
                null,
                null
        ));

        double contextBlockReadyRate = safeDouble(safeTotals.get("context_block_ready_rate"));
        items.add(buildScorecardItem(
                "customer_context_blocks",
                "workspace",
                "Customer context block priority",
                contextBlockReadyRate >= 0.95d ? "ok" : "attention",
                false,
                "Приоритетные блоки customer context должны быть готовы и стандартизированы для оператора.",
                "%.1f%% ready".formatted(contextBlockReadyRate * 100d),
                ">= 95.0%",
                null,
                null
        ));

        double parityReadyRate = safeDouble(safeTotals.get("workspace_parity_ready_rate"));
        long parityGapEvents = toLong(safeTotals.get("workspace_parity_gap_events"));
        long workspaceOpenEvents = toLong(safeTotals.get("workspace_open_events"));
        items.add(buildScorecardItem(
                "workspace_parity",
                "workspace",
                "Workspace parity with legacy",
                workspaceOpenEvents <= 0 ? "hold" : (parityReadyRate >= 0.95d ? "ok" : "attention"),
                false,
                "Workspace должен покрывать основной operator-flow, а legacy modal оставаться rollback-only.",
                workspaceOpenEvents <= 0
                        ? "Недостаточно workspace_open_ms событий"
                        : "%.1f%% ready, gaps=%d".formatted(parityReadyRate * 100d, parityGapEvents),
                ">= 95.0% parity-ready opens",
                null,
                null
        ));

        String externalMeasuredAt = firstNonBlank(
                normalizeUtcTimestamp(externalSignal.get("reviewed_at")),
                normalizeUtcTimestamp(externalSignal.get("data_updated_at")),
                normalizeUtcTimestamp(externalSignal.get("datamart_health_updated_at")),
                normalizeUtcTimestamp(externalSignal.get("datamart_program_updated_at")),
                normalizeUtcTimestamp(externalSignal.get("datamart_dependency_ticket_updated_at")),
                normalizeUtcTimestamp(externalSignal.get("datamart_target_ready_at"))
        );
        boolean externalEnabled = toBoolean(externalSignal.get("enabled"));
        boolean externalReady = toBoolean(externalSignal.get("ready_for_decision"));
        items.add(buildScorecardItem(
                "external_kpi_gate",
                "external_dependencies",
                "External KPI checkpoint",
                externalEnabled ? (externalReady ? "ok" : "hold") : "off",
                externalEnabled && !externalReady,
                "Omni-channel / finance / data-mart зависимости не должны блокировать rollout.",
                "omni=%s, finance=%s".formatted(
                        toBoolean(externalSignal.get("omnichannel_ready")) ? "ready" : "pending",
                        toBoolean(externalSignal.get("finance_ready")) ? "ready" : "pending"),
                externalEnabled ? "ready_for_decision=true" : "gate disabled",
                externalMeasuredAt,
                String.valueOf(externalSignal.getOrDefault("note", "")).trim()
        ));
        appendExternalCheckpointScorecardItems(items, externalSignal, externalEnabled);

        return Map.of(
                "generated_at", Instant.now().toString(),
                "decision_action", String.valueOf(safeRolloutDecision.getOrDefault("action", "hold")),
                "items", items
        );
    }

    private Map<String, Object> buildWorkspaceRolloutPacket(Map<String, Object> totals,
                                                            Map<String, Object> guardrails,
                                                            Map<String, Object> rolloutDecision,
                                                            Map<String, Object> rolloutScorecard,
                                                            Object gapBreakdownRaw,
                                                            int windowDays,
                                                            String experimentName) {
        Map<String, Object> safeTotals = totals == null ? Map.of() : totals;
        Map<String, Object> safeGuardrails = guardrails == null ? Map.of() : guardrails;
        Map<String, Object> safeRolloutDecision = rolloutDecision == null ? Map.of() : rolloutDecision;
        Map<String, Object> safeRolloutScorecard = rolloutScorecard == null ? Map.of() : rolloutScorecard;
        Map<String, Object> gapBreakdown = gapBreakdownRaw instanceof Map<?, ?> map ? castObjectMap(map) : Map.of();
        Map<String, Object> externalSignal = safeRolloutDecision.get("external_kpi_signal") instanceof Map<?, ?> map
                ? castObjectMap(map)
                : Map.of();
        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minusSeconds(Math.max(1, windowDays) * 24L * 60L * 60L);
        Instant previousWindowEnd = windowStart;
        Instant previousWindowStart = previousWindowEnd.minusSeconds(Math.max(1, windowDays) * 24L * 60L * 60L);

        boolean packetRequired = resolveBooleanDialogConfigValue("workspace_rollout_governance_packet_required", false);
        boolean ownerSignoffRequired = resolveBooleanDialogConfigValue("workspace_rollout_governance_owner_signoff_required", false);
        String ownerSignoffBy = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_owner_signoff_by")));
        String ownerSignoffAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_owner_signoff_at"));
        long ownerSignoffTtlHours = resolveLongDialogConfigValue(
                "workspace_rollout_governance_owner_signoff_ttl_hours", 168, 1, 24 * 90L);
        long reviewCadenceDays = resolveLongDialogConfigValue(
                "workspace_rollout_governance_review_cadence_days", 0, 0, 90);
        String reviewCadenceBy = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_reviewed_by")));
        String reviewCadenceAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_reviewed_at"));
        String reviewCadenceNote = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_review_note")));
        String reviewDecisionAction = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_review_decision_action")));
        if (reviewDecisionAction != null) {
            reviewDecisionAction = reviewDecisionAction.toLowerCase(Locale.ROOT);
            if (!"go".equals(reviewDecisionAction) && !"hold".equals(reviewDecisionAction) && !"rollback".equals(reviewDecisionAction)) {
                reviewDecisionAction = null;
            }
        }
        String reviewIncidentFollowup = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_review_incident_followup")));
        List<String> reviewRequiredCriteria = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_governance_review_required_criteria"));
        List<String> reviewCheckedCriteria = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_governance_review_checked_criteria"));
        boolean reviewDecisionRequired = resolveBooleanDialogConfigValue("workspace_rollout_governance_review_decision_required", false);
        boolean reviewIncidentFollowupRequired = resolveBooleanDialogConfigValue("workspace_rollout_governance_incident_followup_required", false);
        boolean reviewFollowupForNonGoRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_governance_followup_for_non_go_required", false);
        String previousDecisionAction = normalizeNullString(String.valueOf(
                resolveDialogConfigValue("workspace_rollout_governance_previous_decision_action")));
        if (previousDecisionAction != null) {
            previousDecisionAction = previousDecisionAction.toLowerCase(Locale.ROOT);
            if (!"go".equals(previousDecisionAction)
                    && !"hold".equals(previousDecisionAction)
                    && !"rollback".equals(previousDecisionAction)) {
                previousDecisionAction = null;
            }
        }
        String previousDecisionAtRaw = String.valueOf(
                resolveDialogConfigValue("workspace_rollout_governance_previous_decision_at"));
        long parityExitDays = resolveLongDialogConfigValue(
                "workspace_rollout_governance_parity_exit_days", 0, 0, 90);
        List<String> parityCriticalReasons = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_governance_parity_critical_reasons"));
        List<String> legacyOnlyScenarios = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_governance_legacy_only_scenarios"));
        Map<String, Map<String, Object>> legacyOnlyScenarioMetadata = resolveLegacyOnlyScenarioMetadataMap(
                resolveDialogConfigValue("workspace_rollout_governance_legacy_only_scenario_metadata"));
        String legacyInventoryReviewedBy = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_legacy_inventory_reviewed_by")));
        String legacyInventoryReviewedAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_legacy_inventory_reviewed_at"));
        String legacyInventoryReviewNote = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_legacy_inventory_review_note")));
        String legacyUsageReviewedBy = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_legacy_usage_reviewed_by")));
        String legacyUsageReviewedAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_legacy_usage_reviewed_at"));
        String legacyUsageReviewNote = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_legacy_usage_review_note")));
        String legacyUsageDecision = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_legacy_usage_decision")));
        if (legacyUsageDecision != null) {
            legacyUsageDecision = legacyUsageDecision.toLowerCase(Locale.ROOT);
            if (!"go".equals(legacyUsageDecision) && !"hold".equals(legacyUsageDecision)) {
                legacyUsageDecision = null;
            }
        }
        long legacyUsageReviewTtlHours = resolveLongDialogConfigValue(
                "workspace_rollout_governance_legacy_usage_review_ttl_hours", 168, 1, 24 * 90L);
        Long legacyUsageMaxSharePct = resolveNullableLongDialogConfigValue(
                "workspace_rollout_governance_legacy_manual_share_max_pct", 0, 100);
        Long legacyUsageMinWorkspaceOpenEvents = resolveNullableLongDialogConfigValue(
                "workspace_rollout_governance_legacy_usage_min_workspace_open_events", 0, 100_000);
        Long legacyUsageMaxShareDeltaPct = resolveNullableLongDialogConfigValue(
                "workspace_rollout_governance_legacy_usage_max_share_delta_pct", 0, 100);
        Long legacyUsageMaxBlockedShareDeltaPct = resolveNullableLongDialogConfigValue(
                "workspace_rollout_governance_legacy_usage_max_blocked_share_delta_pct", 0, 100);
        List<String> legacyManualAllowedReasons = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_legacy_manual_open_allowed_reasons"));
        boolean legacyManualReasonCatalogRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_legacy_manual_open_reason_catalog_required", false);
        boolean legacyBlockedReasonsReviewRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_governance_legacy_blocked_reasons_review_required", false);
        long legacyBlockedReasonsTopN = resolveLongDialogConfigValue(
                "workspace_rollout_governance_legacy_blocked_reasons_top_n", 3, 1, 10);
        List<String> legacyBlockedReasonsReviewed = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_governance_legacy_blocked_reasons_reviewed"));
        String legacyBlockedReasonsFollowup = normalizeNullString(
                String.valueOf(resolveDialogConfigValue("workspace_rollout_governance_legacy_blocked_reasons_followup")));
        boolean legacyUsageDecisionRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_governance_legacy_usage_decision_required", false);
        boolean contextContractRequired = resolveBooleanDialogConfigValue("workspace_rollout_context_contract_required", false);
        List<String> contextContractScenarios = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_context_contract_scenarios"));
        List<String> contextContractMandatoryFields = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_context_contract_mandatory_fields"));
        Map<String, List<String>> contextContractMandatoryFieldsByScenario = resolveDialogConfigStringListMap(
                resolveDialogConfigValue("workspace_rollout_context_contract_mandatory_fields_by_scenario"));
        List<String> contextContractSourceOfTruth = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_context_contract_source_of_truth"));
        Map<String, List<String>> contextContractSourceOfTruthByScenario = resolveDialogConfigStringListMap(
                resolveDialogConfigValue("workspace_rollout_context_contract_source_of_truth_by_scenario"));
        List<String> contextContractPriorityBlocks = resolveDialogConfigStringList(
                resolveDialogConfigValue("workspace_rollout_context_contract_priority_blocks"));
        Map<String, List<String>> contextContractPriorityBlocksByScenario = resolveDialogConfigStringListMap(
                resolveDialogConfigValue("workspace_rollout_context_contract_priority_blocks_by_scenario"));
        Map<String, Map<String, String>> contextContractPlaybooks = resolveContextContractPlaybooks(
                resolveDialogConfigValue("workspace_rollout_context_contract_playbooks"));
        String contextContractReviewedBy = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_context_contract_reviewed_by")));
        String contextContractReviewedAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_context_contract_reviewed_at"));
        String contextContractReviewNote = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_context_contract_review_note")));
        long contextContractReviewTtlHours = resolveLongDialogConfigValue(
                "workspace_rollout_context_contract_review_ttl_hours", 168, 1, 24 * 90L);

        OffsetDateTime ownerSignoffAt = parseReviewTimestamp(ownerSignoffAtRaw);
        boolean ownerSignoffTimestampInvalid = StringUtils.hasText(normalizeNullString(ownerSignoffAtRaw)) && ownerSignoffAt == null;
        boolean ownerSignoffPresent = ownerSignoffAt != null && StringUtils.hasText(ownerSignoffBy);
        boolean ownerSignoffFresh = false;
        long ownerSignoffAgeHours = -1L;
        if (ownerSignoffAt != null) {
            ownerSignoffAgeHours = Math.max(0, java.time.Duration.between(ownerSignoffAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
            ownerSignoffFresh = ownerSignoffAgeHours <= ownerSignoffTtlHours;
        }
        boolean ownerSignoffReady = !ownerSignoffRequired || (ownerSignoffPresent && ownerSignoffFresh && !ownerSignoffTimestampInvalid);
        OffsetDateTime reviewCadenceAt = parseReviewTimestamp(reviewCadenceAtRaw);
        boolean reviewCadenceTimestampInvalid = StringUtils.hasText(normalizeNullString(reviewCadenceAtRaw)) && reviewCadenceAt == null;
        boolean reviewCadenceEnabled = reviewCadenceDays > 0;
        boolean reviewCadencePresent = reviewCadenceAt != null && StringUtils.hasText(reviewCadenceBy);
        boolean reviewCadenceFresh = false;
        long reviewCadenceAgeDays = -1L;
        if (reviewCadenceAt != null) {
            reviewCadenceAgeDays = Math.max(0, java.time.Duration.between(reviewCadenceAt, OffsetDateTime.now(ZoneOffset.UTC)).toDays());
            reviewCadenceFresh = reviewCadenceAgeDays <= reviewCadenceDays;
        }
        long reviewConfirmedEvents = toLong(safeTotals.get("workspace_rollout_review_confirmed_events"));
        long reviewDecisionGoEvents = toLong(safeTotals.get("workspace_rollout_review_decision_go_events"));
        long reviewDecisionHoldEvents = toLong(safeTotals.get("workspace_rollout_review_decision_hold_events"));
        long reviewDecisionRollbackEvents = toLong(safeTotals.get("workspace_rollout_review_decision_rollback_events"));
        long reviewIncidentFollowupLinkedEvents = toLong(safeTotals.get("workspace_rollout_review_incident_followup_linked_events"));

        List<Map<String, Object>> scorecardItems = safeListOfMaps(safeRolloutScorecard.get("items"));
        boolean scorecardSnapshotReady = !scorecardItems.isEmpty();
        long workspaceOpenEvents = toLong(safeTotals.get("workspace_open_events"));
        double parityReadyRate = safeDouble(safeTotals.get("workspace_parity_ready_rate"));
        long parityGapEvents = toLong(safeTotals.get("workspace_parity_gap_events"));
        List<Map<String, Object>> parityRows = safeListOfMaps(gapBreakdown.get("parity"));
        boolean paritySnapshotReady = workspaceOpenEvents > 0 || !parityRows.isEmpty();
        String topParityReasons = parityRows.stream()
                .limit(3)
                .map(row -> {
                    String reason = normalizeNullString(String.valueOf(row.getOrDefault("reason", "unspecified")));
                    long events = toLong(row.get("events"));
                    return StringUtils.hasText(reason) ? "%s(%d)".formatted(reason, events) : "unspecified(%d)".formatted(events);
                })
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(", "));

        List<Map<String, Object>> alerts = safeListOfMaps(safeGuardrails.get("alerts"));
        long renderErrorAlerts = alerts.stream().filter(alert -> "render_error_rate".equals(String.valueOf(alert.get("metric")))).count();
        long fallbackAlerts = alerts.stream().filter(alert -> "fallback_rate".equals(String.valueOf(alert.get("metric")))).count();
        long abandonAlerts = alerts.stream().filter(alert -> "abandon_rate".equals(String.valueOf(alert.get("metric")))).count();
        long slowOpenAlerts = alerts.stream().filter(alert -> "slow_open_rate".equals(String.valueOf(alert.get("metric")))).count();
        boolean reviewDecisionPresent = StringUtils.hasText(reviewDecisionAction);
        boolean reviewIncidentFollowupPresent = StringUtils.hasText(reviewIncidentFollowup);
        boolean reviewDecisionGo = "go".equals(reviewDecisionAction);
        OffsetDateTime previousDecisionAt = parseReviewTimestamp(previousDecisionAtRaw);
        boolean previousDecisionTimestampInvalid = StringUtils.hasText(normalizeNullString(previousDecisionAtRaw))
                && previousDecisionAt == null;
        boolean previousDecisionNonGo = "hold".equals(previousDecisionAction)
                || "rollback".equals(previousDecisionAction);
        boolean followupForNonGoReady = !reviewFollowupForNonGoRequired
                || !reviewDecisionGo
                || !previousDecisionNonGo
                || reviewIncidentFollowupPresent;
        List<String> reviewMissingCriteria = reviewRequiredCriteria.stream()
                .filter(criteria -> !reviewCheckedCriteria.contains(criteria))
                .toList();
        boolean reviewCriteriaReady = reviewMissingCriteria.isEmpty();
        boolean incidentActionRequiredNow = reviewIncidentFollowupRequired && !alerts.isEmpty();
        boolean reviewCadenceReady = !reviewCadenceEnabled
                || (reviewCadencePresent && reviewCadenceFresh && !reviewCadenceTimestampInvalid
                && (!reviewDecisionRequired || reviewDecisionPresent)
                && reviewCriteriaReady
                && (!reviewFollowupForNonGoRequired || !previousDecisionTimestampInvalid)
                && followupForNonGoReady
                && (!incidentActionRequiredNow || reviewIncidentFollowupPresent));
        boolean incidentHistoryReady = true;
        boolean externalGateSnapshotReady = !externalSignal.isEmpty();
        Map<String, Object> parityExitCriteria = buildWorkspaceParityExitCriteria(
                parityExitDays,
                experimentName,
                parityCriticalReasons);
        boolean parityExitCriteriaEnabled = toBoolean(parityExitCriteria.get("enabled"));
        boolean parityExitCriteriaReady = toBoolean(parityExitCriteria.get("ready"));
        boolean legacyInventoryEnabled = packetRequired || !legacyOnlyScenarios.isEmpty();
        OffsetDateTime legacyInventoryReviewedAt = parseReviewTimestamp(legacyInventoryReviewedAtRaw);
        boolean legacyInventoryReviewTimestampInvalid = StringUtils.hasText(normalizeNullString(legacyInventoryReviewedAtRaw))
                && legacyInventoryReviewedAt == null;
        Instant now = Instant.now();
        List<Map<String, Object>> legacyOnlyScenarioDetails = legacyOnlyScenarios.stream()
                .map(scenario -> {
                    Map<String, Object> metadata = legacyOnlyScenarioMetadata.getOrDefault(scenario.toLowerCase(Locale.ROOT), Map.of());
                    String owner = normalizeNullString(String.valueOf(metadata.get("owner")));
                    String deadlineAt = normalizeNullString(String.valueOf(metadata.get("deadline_at_utc")));
                    boolean deadlineTimestampInvalid = toBoolean(metadata.get("deadline_timestamp_invalid"));
                    Instant deadlineInstant = null;
                    if (StringUtils.hasText(deadlineAt)) {
                        OffsetDateTime parsedDeadline = parseReviewTimestamp(deadlineAt);
                        if (parsedDeadline != null) {
                            deadlineInstant = parsedDeadline.toInstant();
                        } else {
                            deadlineTimestampInvalid = true;
                        }
                    }
                    boolean deadlinePresent = StringUtils.hasText(deadlineAt);
                    boolean deadlineOverdue = deadlineInstant != null && deadlineInstant.isBefore(now);
                    boolean ownerReady = StringUtils.hasText(owner);
                    boolean detailReady = ownerReady && deadlinePresent && !deadlineTimestampInvalid;
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("scenario", scenario);
                    item.put("owner", owner == null ? "" : owner);
                    item.put("owner_ready", ownerReady);
                    item.put("deadline_at_utc", deadlineAt == null ? "" : deadlineAt);
                    item.put("deadline_present", deadlinePresent);
                    item.put("deadline_timestamp_invalid", deadlineTimestampInvalid);
                    item.put("deadline_overdue", deadlineOverdue);
                    item.put("ready", detailReady);
                    item.put("note", normalizeNullString(String.valueOf(metadata.get("note"))));
                    return item;
                })
                .toList();
        long legacyOwnerAssignedCount = legacyOnlyScenarioDetails.stream()
                .filter(item -> toBoolean(item.get("owner_ready")))
                .count();
        long legacyDeadlineAssignedCount = legacyOnlyScenarioDetails.stream()
                .filter(item -> toBoolean(item.get("deadline_present")) && !toBoolean(item.get("deadline_timestamp_invalid")))
                .count();
        long legacyDeadlineInvalidCount = legacyOnlyScenarioDetails.stream()
                .filter(item -> toBoolean(item.get("deadline_timestamp_invalid")))
                .count();
        long legacyDeadlineOverdueCount = legacyOnlyScenarioDetails.stream()
                .filter(item -> toBoolean(item.get("deadline_overdue")))
                .count();
        List<String> legacyOverdueScenarios = legacyOnlyScenarioDetails.stream()
                .filter(item -> toBoolean(item.get("deadline_overdue")))
                .map(item -> normalizeNullString(String.valueOf(item.get("scenario"))))
                .filter(StringUtils::hasText)
                .toList();
        long legacyManagedScenarioCount = legacyOnlyScenarioDetails.stream()
                .filter(item -> toBoolean(item.get("ready")) && !toBoolean(item.get("deadline_overdue")))
                .count();
        long legacyUnmanagedScenarioCount = Math.max(0, legacyOnlyScenarios.size() - legacyManagedScenarioCount);
        List<String> legacyReviewQueueScenarios = legacyOnlyScenarioDetails.stream()
                .filter(item -> !toBoolean(item.get("ready"))
                        || toBoolean(item.get("deadline_overdue"))
                        || toBoolean(item.get("deadline_timestamp_invalid")))
                .map(item -> normalizeNullString(String.valueOf(item.get("scenario"))))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Instant legacyReviewQueueOldestDeadline = legacyOnlyScenarioDetails.stream()
                .filter(item -> legacyReviewQueueScenarios.contains(String.valueOf(item.get("scenario"))))
                .map(item -> normalizeNullString(String.valueOf(item.get("deadline_at_utc"))))
                .filter(StringUtils::hasText)
                .map(value -> {
                    try {
                        return Instant.parse(value);
                    } catch (Exception ignored) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .min(Instant::compareTo)
                .orElse(null);
        boolean legacyInventoryReady = legacyOnlyScenarios.isEmpty();
        boolean legacyInventoryManaged = !legacyInventoryReady
                && legacyUnmanagedScenarioCount == 0
                && legacyDeadlineInvalidCount == 0
                && legacyDeadlineOverdueCount == 0;
        String legacyInventoryStatus = !legacyInventoryEnabled
                ? "off"
                : legacyInventoryReady ? "ok" : (legacyInventoryManaged ? "attention" : "hold");
        boolean contextContractEnabled = contextContractRequired
                || !contextContractScenarios.isEmpty()
                || !contextContractMandatoryFields.isEmpty()
                || !contextContractMandatoryFieldsByScenario.isEmpty()
                || !contextContractSourceOfTruth.isEmpty()
                || !contextContractSourceOfTruthByScenario.isEmpty()
                || !contextContractPriorityBlocks.isEmpty()
                || !contextContractPriorityBlocksByScenario.isEmpty()
                || !contextContractPlaybooks.isEmpty();
        OffsetDateTime contextContractReviewedAt = parseReviewTimestamp(contextContractReviewedAtRaw);
        boolean contextContractReviewTimestampInvalid = StringUtils.hasText(normalizeNullString(contextContractReviewedAtRaw))
                && contextContractReviewedAt == null;
        boolean contextContractReviewPresent = contextContractReviewedAt != null
                && StringUtils.hasText(contextContractReviewedBy);
        boolean contextContractReviewFresh = false;
        long contextContractReviewAgeHours = -1L;
        if (contextContractReviewedAt != null) {
            contextContractReviewAgeHours = Math.max(0, java.time.Duration
                    .between(contextContractReviewedAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
            contextContractReviewFresh = contextContractReviewAgeHours <= contextContractReviewTtlHours;
        }
        boolean contextContractDefinitionReady = !contextContractScenarios.isEmpty()
                && (!contextContractMandatoryFields.isEmpty() || !contextContractMandatoryFieldsByScenario.isEmpty())
                && (!contextContractSourceOfTruth.isEmpty() || !contextContractSourceOfTruthByScenario.isEmpty())
                && (!contextContractPriorityBlocks.isEmpty() || !contextContractPriorityBlocksByScenario.isEmpty());
        List<String> contextContractPlaybookExpectedKeys = buildContextContractPlaybookExpectedKeys(
                contextContractMandatoryFields,
                contextContractMandatoryFieldsByScenario,
                contextContractSourceOfTruth,
                contextContractSourceOfTruthByScenario,
                contextContractPriorityBlocks,
                contextContractPriorityBlocksByScenario);
        List<String> contextContractPlaybookMissingKeys = contextContractPlaybookExpectedKeys.stream()
                .filter(key -> !hasContextContractPlaybookCoverage(contextContractPlaybooks, key))
                .toList();
        int contextContractPlaybookExpectedCount = contextContractPlaybookExpectedKeys.size();
        int contextContractPlaybookCoveredCount = Math.max(0,
                contextContractPlaybookExpectedCount - contextContractPlaybookMissingKeys.size());
        long contextContractPlaybookCoveragePct = contextContractPlaybookExpectedCount > 0
                ? Math.round((contextContractPlaybookCoveredCount * 100d) / contextContractPlaybookExpectedCount)
                : 100L;
        boolean contextContractReady = !contextContractEnabled
                || (contextContractDefinitionReady
                && contextContractReviewPresent
                && contextContractReviewFresh
                && !contextContractReviewTimestampInvalid);
        long legacyManagedCoveragePct = legacyOnlyScenarios.isEmpty()
                ? 100L
                : Math.round((legacyManagedScenarioCount * 100d) / legacyOnlyScenarios.size());
        long legacyOwnerCoveragePct = legacyOnlyScenarios.isEmpty()
                ? 100L
                : Math.round((legacyOwnerAssignedCount * 100d) / legacyOnlyScenarios.size());
        long legacyDeadlineCoveragePct = legacyOnlyScenarios.isEmpty()
                ? 100L
                : Math.round((legacyDeadlineAssignedCount * 100d) / legacyOnlyScenarios.size());
        long legacyDeadlineOverduePct = legacyOnlyScenarios.isEmpty()
                ? 0L
                : Math.round((legacyDeadlineOverdueCount * 100d) / legacyOnlyScenarios.size());
        long legacyInventoryReviewAgeHours = legacyInventoryReviewedAt != null
                ? Math.max(0L, java.time.Duration.between(legacyInventoryReviewedAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours())
                : -1L;
        long legacyRepeatReviewCadenceDays = reviewCadenceDays > 0 ? reviewCadenceDays : 7L;
        OffsetDateTime legacyRepeatReviewDueAt = legacyInventoryReviewedAt != null
                ? legacyInventoryReviewedAt.plusDays(legacyRepeatReviewCadenceDays)
                : null;
        boolean legacyInventoryReviewFresh = legacyOnlyScenarios.isEmpty()
                || (legacyInventoryReviewedAt != null
                && legacyInventoryReviewAgeHours <= legacyRepeatReviewCadenceDays * 24L
                && !legacyInventoryReviewTimestampInvalid);
        long legacyRepeatReviewOverdueDays = !legacyOnlyScenarios.isEmpty()
                && legacyRepeatReviewDueAt != null
                && legacyRepeatReviewDueAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC))
                ? Math.max(0L, java.time.Duration.between(legacyRepeatReviewDueAt, OffsetDateTime.now(ZoneOffset.UTC)).toDays())
                : 0L;
        boolean legacyRepeatReviewRequired = !legacyOnlyScenarios.isEmpty()
                && (legacyInventoryReviewedAt == null
                || legacyInventoryReviewAgeHours > legacyRepeatReviewCadenceDays * 24L
                || legacyDeadlineOverdueCount > 0);
        String legacyRepeatReviewReason = legacyDeadlineOverdueCount > 0
                ? "overdue_commitments"
                : legacyInventoryReviewedAt == null
                ? "review_missing"
                : legacyInventoryReviewAgeHours > legacyRepeatReviewCadenceDays * 24L
                ? "review_stale"
                : "";
        long legacyReviewQueueRepeatCycles = !legacyReviewQueueScenarios.isEmpty() && legacyRepeatReviewCadenceDays > 0
                ? Math.max(1L, Math.max(
                legacyRepeatReviewOverdueDays > 0
                        ? 1L + (legacyRepeatReviewOverdueDays / Math.max(1L, legacyRepeatReviewCadenceDays))
                        : 0L,
                legacyInventoryReviewedAt != null && legacyInventoryReviewAgeHours > 0
                        ? Math.max(0L, legacyInventoryReviewAgeHours / Math.max(24L, legacyRepeatReviewCadenceDays * 24L))
                        : 0L))
                : 0L;
        long legacyReviewQueueOldestOverdueDays = legacyReviewQueueOldestDeadline != null && legacyReviewQueueOldestDeadline.isBefore(now)
                ? Math.max(0L, java.time.Duration.between(legacyReviewQueueOldestDeadline, now).toDays())
                : 0L;
        boolean legacyReviewQueueFollowupRequired = !legacyReviewQueueScenarios.isEmpty()
                && (legacyRepeatReviewRequired
                || legacyDeadlineOverdueCount > 0
                || legacyDeadlineInvalidCount > 0
                || legacyReviewQueueRepeatCycles > 1);
        List<String> legacyReviewQueueEscalatedScenarios = legacyOnlyScenarioDetails.stream()
                .filter(item -> legacyReviewQueueScenarios.contains(String.valueOf(item.get("scenario"))))
                .filter(item -> toBoolean(item.get("deadline_overdue"))
                        || toBoolean(item.get("deadline_timestamp_invalid"))
                        || legacyReviewQueueRepeatCycles >= 3)
                .map(item -> normalizeNullString(String.valueOf(item.get("scenario"))))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        boolean legacyReviewQueueEscalationRequired = !legacyReviewQueueEscalatedScenarios.isEmpty()
                || legacyReviewQueueOldestOverdueDays >= 7L;
        long legacyReviewQueueEscalatedCount = legacyReviewQueueEscalatedScenarios.size();
        List<String> legacyReviewQueueConsolidationCandidates = legacyOnlyScenarioDetails.stream()
                .filter(item -> legacyReviewQueueScenarios.contains(String.valueOf(item.get("scenario"))))
                .filter(item -> !toBoolean(item.get("owner_ready"))
                        || !toBoolean(item.get("deadline_present"))
                        || toBoolean(item.get("deadline_timestamp_invalid")))
                .map(item -> normalizeNullString(String.valueOf(item.get("scenario"))))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        boolean legacyReviewQueueConsolidationRequired = !legacyReviewQueueConsolidationCandidates.isEmpty()
                && legacyReviewQueueRepeatCycles > 1;
        String legacyReviewQueueClosurePressure = legacyReviewQueueScenarios.isEmpty()
                ? "none"
                : legacyReviewQueueEscalationRequired ? "high"
                : legacyReviewQueueFollowupRequired ? "moderate" : "controlled";
        String legacyReviewQueueManagementReviewSummary = !legacyReviewQueueEscalationRequired
                ? ""
                : "Management review нужен для %d queue-сценария(ев); oldest overdue=%dд."
                .formatted(
                        Math.max(1L, legacyReviewQueueEscalatedCount),
                        Math.max(0L, legacyReviewQueueOldestOverdueDays));
        String legacyReviewQueueSummary = legacyReviewQueueScenarios.isEmpty()
                ? ""
                : legacyReviewQueueFollowupRequired
                ? "В weekly closure review остаются %d сценария(ев); oldest due=%s; repeat cycles=%d."
                .formatted(
                        legacyReviewQueueScenarios.size(),
                        legacyReviewQueueOldestDeadline != null ? legacyReviewQueueOldestDeadline.toString() : "n/a",
                        legacyReviewQueueRepeatCycles)
                : "Review queue под контролем: %d сценария(ев) ещё в работе.".formatted(legacyReviewQueueScenarios.size());
        List<String> legacyInventoryActionItems = new ArrayList<>();
        if (!legacyOnlyScenarios.isEmpty()) {
            if (legacyOwnerAssignedCount < legacyOnlyScenarios.size()) {
                legacyInventoryActionItems.add("Назначьте owner для всех legacy-only сценариев.");
            }
            if (legacyDeadlineAssignedCount < legacyOnlyScenarios.size() || legacyDeadlineInvalidCount > 0) {
                legacyInventoryActionItems.add("Заполните корректные UTC sunset deadline для каждого открытого сценария.");
            }
            if (legacyDeadlineOverdueCount > 0) {
                legacyInventoryActionItems.add("Закройте или перепланируйте просроченные sunset commitments.");
            }
            if (!StringUtils.hasText(legacyInventoryReviewedBy) || legacyInventoryReviewedAt == null) {
                legacyInventoryActionItems.add("Зафиксируйте последний UTC review owner/deadline inventory.");
            }
            if (legacyReviewQueueEscalationRequired) {
                legacyInventoryActionItems.add("Эскалируйте долгоживущие legacy review-queue сценарии на management review.");
            }
            if (legacyReviewQueueConsolidationRequired) {
                legacyInventoryActionItems.add("Сконсолидируйте queue-сценарии без owner/deadline в единый weekly closure plan.");
            }
            if (legacyReviewQueueFollowupRequired) {
                legacyInventoryActionItems.add("Закройте weekly closure-loop для сценариев, которые повторно остаются в legacy review-queue.");
            }
        }
        String legacyReviewQueueNextActionSummary = !legacyInventoryActionItems.isEmpty()
                ? legacyInventoryActionItems.get(0)
                : (legacyReviewQueueScenarios.isEmpty()
                ? "Legacy review-queue не требует follow-up."
                : "Продолжайте weekly closure review без дополнительных escalation.");
        Set<String> legacyReviewQueueScenarioSet = new LinkedHashSet<>(legacyReviewQueueScenarios);
        Set<String> legacyReviewQueueEscalatedScenarioSet = new LinkedHashSet<>(legacyReviewQueueEscalatedScenarios);
        Set<String> legacyReviewQueueConsolidationSet = new LinkedHashSet<>(legacyReviewQueueConsolidationCandidates);
        legacyOnlyScenarioDetails = legacyOnlyScenarioDetails.stream()
                .map(item -> {
                    Map<String, Object> enriched = new LinkedHashMap<>(item);
                    String scenario = String.valueOf(item.getOrDefault("scenario", ""));
                    enriched.put("queue_candidate", legacyReviewQueueScenarioSet.contains(scenario));
                    enriched.put("escalation_candidate", legacyReviewQueueEscalatedScenarioSet.contains(scenario));
                    enriched.put("consolidation_candidate", legacyReviewQueueConsolidationSet.contains(scenario));
                    return enriched;
                })
                .toList();
        List<String> contextContractDefinitionGaps = Stream.of(
                        contextContractScenarios.isEmpty() ? "scenarios" : null,
                        (contextContractMandatoryFields.isEmpty() && contextContractMandatoryFieldsByScenario.isEmpty())
                                ? "mandatory_fields" : null,
                        (contextContractSourceOfTruth.isEmpty() && contextContractSourceOfTruthByScenario.isEmpty())
                                ? "source_of_truth" : null,
                        (contextContractPriorityBlocks.isEmpty() && contextContractPriorityBlocksByScenario.isEmpty())
                                ? "priority_blocks" : null)
                .filter(StringUtils::hasText)
                .toList();
        List<String> contextContractOperatorFocusBlocks = Stream.concat(
                        contextContractPriorityBlocks.stream(),
                        contextContractPriorityBlocksByScenario.values().stream().flatMap(List::stream))
                .map(value -> value == null ? null : value.trim())
                .filter(StringUtils::hasText)
                .distinct()
                .limit(4)
                .toList();
        List<String> contextContractActionItems = new ArrayList<>();
        if (!contextContractDefinitionGaps.isEmpty()) {
            contextContractActionItems.add("Заполните missing contract definitions: " + String.join(", ", contextContractDefinitionGaps) + ".");
        }
        if (!contextContractPlaybookMissingKeys.isEmpty()) {
            contextContractActionItems.add("Добавьте playbooks для gap-ключей: " + String.join(", ", contextContractPlaybookMissingKeys.stream().limit(3).toList()) + ".");
        }
        if (contextContractReviewTimestampInvalid) {
            contextContractActionItems.add("Исправьте reviewed_at на валидный UTC timestamp.");
        } else if (contextContractEnabled && !contextContractReviewPresent) {
            contextContractActionItems.add("Подтвердите context contract через UTC review-checkpoint.");
        } else if (contextContractEnabled && !contextContractReviewFresh) {
            contextContractActionItems.add("Обновите review context contract: текущий sign-off устарел.");
        }
        if (contextContractOperatorFocusBlocks.isEmpty() && contextContractEnabled) {
            contextContractActionItems.add("Задайте priority blocks, чтобы снизить шум в sidebar и сделать раскрытие progressive.");
        }
        String contextContractOperatorSummary = contextContractReady
                ? "Minimum profile соблюдён."
                : !contextContractDefinitionGaps.isEmpty()
                ? "Contract definitions требуют cleanup."
                : !contextContractPlaybookMissingKeys.isEmpty()
                ? "Playbook coverage неполный для operator-flow."
                : contextContractReviewTimestampInvalid
                ? "Review checkpoint содержит невалидный UTC timestamp."
                : (contextContractEnabled && !contextContractReviewPresent)
                ? "Context contract ещё не подтверждён review-checkpoint."
                : (contextContractEnabled && !contextContractReviewFresh)
                ? "Context contract review устарел."
                : !contextContractOperatorFocusBlocks.isEmpty()
                ? "Operator focus blocks требуют приоритизации."
                : "Context contract требует action-oriented follow-up.";
        String contextContractNextStepSummary = contextContractActionItems.isEmpty()
                ? ""
                : contextContractActionItems.get(0);

        OffsetDateTime legacyUsageReviewedAt = parseReviewTimestamp(legacyUsageReviewedAtRaw);
        boolean legacyUsageReviewTimestampInvalid = StringUtils.hasText(normalizeNullString(legacyUsageReviewedAtRaw))
                && legacyUsageReviewedAt == null;
        boolean legacyUsageReviewPresent = legacyUsageReviewedAt != null && StringUtils.hasText(legacyUsageReviewedBy);
        boolean legacyUsageReviewFresh = false;
        long legacyUsageReviewAgeHours = -1L;
        if (legacyUsageReviewedAt != null) {
            legacyUsageReviewAgeHours = Math.max(0, java.time.Duration
                    .between(legacyUsageReviewedAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
            legacyUsageReviewFresh = legacyUsageReviewAgeHours <= legacyUsageReviewTtlHours;
        }
        long legacyUsagePolicyUpdatedEvents = toLong(safeTotals.get("workspace_legacy_usage_policy_updated_events"));
        long manualLegacyOpenEvents = toLong(safeTotals.get("manual_legacy_open_events"));
        long manualLegacyBlockedEvents = toLong(safeTotals.get("workspace_open_legacy_blocked_events"));
        List<Map<String, Object>> manualLegacyReasonBreakdown = dialogWorkspaceTelemetryDataService.loadWorkspaceEventReasonBreakdown(
                "workspace_open_legacy_manual",
                windowStart,
                windowEnd,
                experimentName,
                5);
        List<Map<String, Object>> blockedLegacyReasonBreakdown = dialogWorkspaceTelemetryDataService.loadWorkspaceEventReasonBreakdown(
                "workspace_open_legacy_blocked",
                windowStart,
                windowEnd,
                experimentName,
                5);
        long unknownManualLegacyReasons = manualLegacyReasonBreakdown.stream()
                .filter(row -> {
                    String reason = normalizeNullString(String.valueOf(row.getOrDefault("reason", "")));
                    return !StringUtils.hasText(reason)
                            || (legacyManualReasonCatalogRequired && !legacyManualAllowedReasons.contains(reason));
                })
                .mapToLong(row -> toLong(row.get("events")))
                .sum();
        List<Map<String, Object>> previousRows = dialogWorkspaceTelemetryDataService.loadWorkspaceTelemetryRows(previousWindowStart, previousWindowEnd, experimentName);
        Map<String, Object> previousTotals = dialogWorkspaceTelemetryAnalyticsService.computeWorkspaceTelemetryTotals(previousRows);
        long previousWorkspaceOpenEvents = toLong(previousTotals.get("workspace_open_events"));
        long previousManualLegacyOpenEvents = toLong(previousTotals.get("manual_legacy_open_events"));
        long previousManualLegacyBlockedEvents = toLong(previousTotals.get("workspace_open_legacy_blocked_events"));
        boolean contextSecondaryDetailsFollowupRequired = toBoolean(safeTotals.get("context_secondary_details_followup_required"));
        boolean contextSecondaryDetailsManagementReviewRequired = toBoolean(safeTotals.get("context_secondary_details_management_review_required"));
        String contextSecondaryDetailsSummary = String.valueOf(
                safeTotals.getOrDefault("context_secondary_details_summary", ""));
        String contextSecondaryDetailsCompactionSummary = String.valueOf(
                safeTotals.getOrDefault("context_secondary_details_compaction_summary", ""));
        String contextSecondaryDetailsUsageLevel = String.valueOf(
                safeTotals.getOrDefault("context_secondary_details_usage_level", "rare"));
        String contextSecondaryDetailsTopSection = String.valueOf(
                safeTotals.getOrDefault("context_secondary_details_top_section", ""));
        boolean contextExtraAttributesCompactionCandidate = toBoolean(
                safeTotals.get("context_extra_attributes_compaction_candidate"));
        long contextExtraAttributesOpenRatePct = toLong(safeTotals.get("context_extra_attributes_open_rate_pct"));
        long contextExtraAttributesSharePctOfSecondary = toLong(
                safeTotals.get("context_extra_attributes_share_pct_of_secondary"));
        String contextExtraAttributesUsageLevel = String.valueOf(
                safeTotals.getOrDefault("context_extra_attributes_usage_level", "rare"));
        String contextExtraAttributesSummary = String.valueOf(
                safeTotals.getOrDefault("context_extra_attributes_summary", ""));
        double previousManualLegacyShareRatio = previousWorkspaceOpenEvents > 0
                ? (double) previousManualLegacyOpenEvents / previousWorkspaceOpenEvents
                : 0d;
        double previousManualLegacyBlockedShareRatio = previousWorkspaceOpenEvents > 0
                ? (double) previousManualLegacyBlockedEvents / previousWorkspaceOpenEvents
                : 0d;
        double manualLegacyShareRatio = workspaceOpenEvents > 0 ? (double) manualLegacyOpenEvents / workspaceOpenEvents : 0d;
        double manualLegacyBlockedShareRatio = workspaceOpenEvents > 0 ? (double) manualLegacyBlockedEvents / workspaceOpenEvents : 0d;
        double manualLegacyShareDeltaPct = (manualLegacyShareRatio - previousManualLegacyShareRatio) * 100d;
        double manualLegacyBlockedShareDeltaPct = (manualLegacyBlockedShareRatio - previousManualLegacyBlockedShareRatio) * 100d;
        boolean legacyUsageThresholdConfigured = legacyUsageMaxSharePct != null;
        double legacyUsageThresholdShare = legacyUsageThresholdConfigured ? legacyUsageMaxSharePct / 100d : 1d;
        boolean legacyUsageThresholdReady = !legacyUsageThresholdConfigured || manualLegacyShareRatio <= legacyUsageThresholdShare;
        boolean legacyUsageMinWorkspaceOpenEventsConfigured = legacyUsageMinWorkspaceOpenEvents != null;
        boolean legacyUsageVolumeReady = !legacyUsageMinWorkspaceOpenEventsConfigured
                || workspaceOpenEvents >= legacyUsageMinWorkspaceOpenEvents;
        boolean legacyUsageShareDeltaConfigured = legacyUsageMaxShareDeltaPct != null;
        boolean legacyUsageTrendReady = !legacyUsageShareDeltaConfigured || manualLegacyShareDeltaPct <= legacyUsageMaxShareDeltaPct;
        boolean legacyUsageBlockedShareDeltaConfigured = legacyUsageMaxBlockedShareDeltaPct != null;
        boolean legacyUsageBlockedTrendReady = !legacyUsageBlockedShareDeltaConfigured
                || manualLegacyBlockedShareDeltaPct <= legacyUsageMaxBlockedShareDeltaPct;
        List<String> blockedReasonsTopKeys = blockedLegacyReasonBreakdown.stream()
                .limit(legacyBlockedReasonsTopN)
                .map(row -> normalizeNullString(String.valueOf(row.getOrDefault("reason", "unspecified"))))
                .map(reason -> StringUtils.hasText(reason) ? reason.toLowerCase(Locale.ROOT) : "unspecified")
                .distinct()
                .toList();
        List<String> blockedReasonsMissing = blockedReasonsTopKeys.stream()
                .filter(reason -> !legacyBlockedReasonsReviewed.contains(reason))
                .toList();
        boolean blockedReasonsReviewConfigured = !legacyBlockedReasonsReviewed.isEmpty()
                || StringUtils.hasText(legacyBlockedReasonsFollowup);
        boolean blockedReasonsReviewNeeded = legacyBlockedReasonsReviewRequired && manualLegacyBlockedEvents > 0;
        boolean blockedReasonsFollowupPresent = StringUtils.hasText(legacyBlockedReasonsFollowup);
        boolean blockedReasonsReviewReady = !blockedReasonsReviewNeeded
                || (blockedReasonsMissing.isEmpty() && blockedReasonsFollowupPresent);
        boolean legacyUsageDecisionPresent = StringUtils.hasText(legacyUsageDecision);
        boolean legacyUsagePolicyEnabled = legacyUsageThresholdConfigured
                || legacyUsageMinWorkspaceOpenEventsConfigured
                || legacyUsageShareDeltaConfigured
                || legacyUsageBlockedShareDeltaConfigured
                || legacyBlockedReasonsReviewRequired
                || blockedReasonsReviewConfigured
                || legacyUsageDecisionRequired
                || legacyUsageReviewPresent
                || StringUtils.hasText(legacyUsageReviewNote);
        boolean legacyUsagePolicyReady = !legacyUsagePolicyEnabled
                || (legacyUsageReviewPresent
                && legacyUsageReviewFresh
                && !legacyUsageReviewTimestampInvalid
                && legacyUsageThresholdReady
                && legacyUsageVolumeReady
                && legacyUsageTrendReady
                && legacyUsageBlockedTrendReady
                && blockedReasonsReviewReady
                && (!legacyUsageDecisionRequired || legacyUsageDecisionPresent));
        List<Map<String, Object>> packetItems = new ArrayList<>();
        packetItems.add(buildScorecardItem(
                "scorecard_snapshot",
                "workspace",
                "Rollout scorecard snapshot",
                scorecardSnapshotReady ? "ok" : (packetRequired ? "hold" : "attention"),
                packetRequired && !scorecardSnapshotReady,
                "Пакет rollout должен включать актуальный scorecard для формального решения.",
                scorecardSnapshotReady
                        ? "items=%d, action=%s".formatted(scorecardItems.size(), String.valueOf(safeRolloutScorecard.getOrDefault("decision_action", safeRolloutDecision.getOrDefault("action", "hold"))))
                        : "missing",
                "scorecard available",
                normalizeUtcTimestamp(safeRolloutScorecard.get("generated_at")),
                null
        ));
        packetItems.add(buildScorecardItem(
                "parity_snapshot",
                "workspace",
                "Workspace parity snapshot",
                paritySnapshotReady ? "ok" : (packetRequired ? "hold" : "attention"),
                packetRequired && !paritySnapshotReady,
                "Пакет rollout должен фиксировать parity-gap snapshot по workspace vs legacy.",
                paritySnapshotReady
                        ? "opens=%d, ready=%.1f%%, gaps=%d".formatted(workspaceOpenEvents, parityReadyRate * 100d, parityGapEvents)
                        : "missing",
                "workspace_open_events > 0",
                normalizeUtcTimestamp(safeRolloutScorecard.get("generated_at")),
                StringUtils.hasText(topParityReasons) ? "top_reasons=" + topParityReasons : ""
        ));
        packetItems.add(buildScorecardItem(
                "external_gate_snapshot",
                "external_dependencies",
                "External KPI gate snapshot",
                externalGateSnapshotReady ? "ok" : (packetRequired ? "hold" : "attention"),
                packetRequired && !externalGateSnapshotReady,
                "Пакет rollout должен содержать статус external KPI gate и его риск-сигналы.",
                externalGateSnapshotReady
                        ? "enabled=%s, ready=%s, risk=%s".formatted(
                                toBoolean(externalSignal.get("enabled")),
                                toBoolean(externalSignal.get("ready_for_decision")),
                                String.valueOf(externalSignal.getOrDefault("datamart_risk_level", "low")))
                        : "missing",
                "external gate status present",
                firstNonBlank(
                        normalizeUtcTimestamp(externalSignal.get("reviewed_at")),
                        normalizeUtcTimestamp(externalSignal.get("data_updated_at")),
                        normalizeUtcTimestamp(safeRolloutScorecard.get("generated_at"))),
                String.valueOf(externalSignal.getOrDefault("note", "")).trim()
        ));
        packetItems.add(buildScorecardItem(
                "incident_history",
                "guardrails",
                "Incident history snapshot",
                incidentHistoryReady ? "ok" : (packetRequired ? "hold" : "attention"),
                packetRequired && !incidentHistoryReady,
                "Пакет rollout должен содержать сводку guardrails/incident history за текущее UTC-окно.",
                "alerts=%d, render=%d, fallback=%d, abandon=%d, slow_open=%d".formatted(
                        alerts.size(), renderErrorAlerts, fallbackAlerts, abandonAlerts, slowOpenAlerts),
                "window=%d days UTC".formatted(Math.max(1, windowDays)),
                normalizeUtcTimestamp(safeRolloutScorecard.get("generated_at")),
                String.valueOf(safeGuardrails.getOrDefault("status", "ok"))
        ));
        packetItems.add(buildScorecardItem(
                "owner_signoff",
                "workspace",
                "Owner sign-off",
                !ownerSignoffRequired ? "off" : (ownerSignoffReady ? "ok" : "hold"),
                ownerSignoffRequired && !ownerSignoffReady,
                "Owner sign-off закрепляет единый decision loop для go / hold / rollback.",
                !ownerSignoffRequired
                        ? "not required"
                        : ownerSignoffTimestampInvalid
                                ? "invalid_utc"
                                : ownerSignoffPresent
                                        ? "signed_by=%s".formatted(ownerSignoffBy)
                                        : "missing",
                ownerSignoffRequired ? "present & <= %d h".formatted(ownerSignoffTtlHours) : "optional",
                ownerSignoffAt != null ? ownerSignoffAt.toString() : "",
                ownerSignoffPresent
                        ? "age_hours=%d".formatted(ownerSignoffAgeHours)
                        : ""
        ));
        packetItems.add(buildScorecardItem(
                "weekly_review",
                "workspace",
                "Weekly parity review cadence",
                !reviewCadenceEnabled ? "off" : (reviewCadenceReady ? "ok" : "hold"),
                reviewCadenceEnabled && !reviewCadenceReady,
                "Parity-gap breakdown должен регулярно подтверждаться review в UTC, чтобы dual-run не оставался без владельца.",
                !reviewCadenceEnabled
                        ? "not required"
                        : reviewCadenceTimestampInvalid
                                ? "invalid_utc"
                                : reviewCadencePresent
                                        ? "reviewed_by=%s%s%s".formatted(
                                        reviewCadenceBy,
                                        reviewDecisionPresent ? ", decision=%s".formatted(reviewDecisionAction) : "",
                                        reviewIncidentFollowupPresent ? ", incident_followup=present" : "")
                                        : "missing",
                reviewCadenceEnabled
                        ? "present & <= %d days%s%s".formatted(
                        reviewCadenceDays,
                        reviewDecisionRequired ? ", decision required" : "",
                        incidentActionRequiredNow ? ", incident follow-up required when alerts>0" : "")
                        + (reviewFollowupForNonGoRequired
                        ? ", incident follow-up required for go after hold/rollback"
                        : "")
                        + (!reviewRequiredCriteria.isEmpty()
                        ? ", criteria required=%s".formatted(String.join("|", reviewRequiredCriteria))
                        : "")
                        : "optional",
                reviewCadenceAt != null ? reviewCadenceAt.toString() : "",
                reviewCadencePresent
                        ? StringUtils.hasText(reviewCadenceNote)
                                ? "age_days=%d; note=%s".formatted(reviewCadenceAgeDays, reviewCadenceNote)
                                : "age_days=%d".formatted(reviewCadenceAgeDays)
                                + (!reviewMissingCriteria.isEmpty()
                                ? "; missing_criteria=%s".formatted(String.join("|", reviewMissingCriteria))
                                : "")
                        : reviewCadenceNote
        ));
        packetItems.add(buildScorecardItem(
                "parity_exit_criteria",
                "workspace",
                "Parity exit criteria",
                !parityExitCriteriaEnabled ? "off" : (parityExitCriteriaReady ? "ok" : "hold"),
                parityExitCriteriaEnabled && !parityExitCriteriaReady,
                "Legacy modal перестаёт считаться штатным UX только после окна без критичных parity-gap в UTC.",
                !parityExitCriteriaEnabled
                        ? "not required"
                        : "critical_gaps=%d".formatted(toLong(parityExitCriteria.get("critical_gap_events"))),
                parityExitCriteriaEnabled
                        ? "0 critical gaps in last %d days UTC".formatted(toLong(parityExitCriteria.get("window_days")))
                        : "optional",
                String.valueOf(parityExitCriteria.getOrDefault("last_seen_at", "")),
                StringUtils.hasText(String.valueOf(parityExitCriteria.getOrDefault("top_reasons_summary", "")))
                        ? "top_reasons=" + parityExitCriteria.get("top_reasons_summary")
                        : StringUtils.hasText(String.valueOf(parityExitCriteria.getOrDefault("critical_reasons_summary", "")))
                                ? "critical=" + parityExitCriteria.get("critical_reasons_summary")
                                : null
        ));
        packetItems.add(buildScorecardItem(
                "legacy_only_inventory",
                "workspace",
                "Legacy-only scenario inventory",
                legacyInventoryStatus,
                packetRequired && !legacyInventoryReady && !legacyInventoryManaged,
                "Явный список legacy-only сценариев нужен, чтобы контролируемо завершить dual-run и не потерять edge-cases.",
                !legacyInventoryEnabled
                        ? "not required"
                        : legacyInventoryReady
                                ? "none"
                                : "open=%d, managed=%d/%d, owner=%d/%d, deadline=%d/%d%s%s".formatted(
                                legacyOnlyScenarios.size(),
                                legacyManagedScenarioCount,
                                legacyOnlyScenarios.size(),
                                legacyOwnerAssignedCount,
                                legacyOnlyScenarios.size(),
                                legacyDeadlineAssignedCount,
                                legacyOnlyScenarios.size(),
                                legacyDeadlineInvalidCount > 0 ? ", invalid_deadlines=%d".formatted(legacyDeadlineInvalidCount) : "",
                                legacyDeadlineOverdueCount > 0 ? ", overdue=%d".formatted(legacyDeadlineOverdueCount) : ""),
                legacyInventoryEnabled ? "inventory empty or every open scenario has owner + UTC deadline" : "optional",
                legacyInventoryReviewedAt != null ? legacyInventoryReviewedAt.toString() : normalizeUtcTimestamp(safeRolloutScorecard.get("generated_at")),
                legacyInventoryReady
                        ? firstNonBlank(legacyInventoryReviewNote, legacyInventoryReviewedBy)
                        : Stream.of(
                                String.join(", ", legacyOnlyScenarios),
                                legacyInventoryManaged ? "sunset_plan=managed" : null,
                                legacyOwnerAssignedCount < legacyOnlyScenarios.size()
                                        ? "missing_owner=%d".formatted(legacyOnlyScenarios.size() - legacyOwnerAssignedCount) : null,
                                legacyDeadlineAssignedCount < legacyOnlyScenarios.size()
                                        ? "missing_deadline=%d".formatted(legacyOnlyScenarios.size() - legacyDeadlineAssignedCount) : null,
                                legacyDeadlineInvalidCount > 0 ? "invalid_deadline=%d".formatted(legacyDeadlineInvalidCount) : null,
                                legacyDeadlineOverdueCount > 0 ? "overdue_deadline=%d".formatted(legacyDeadlineOverdueCount) : null,
                                legacyInventoryReviewNote,
                                legacyInventoryReviewTimestampInvalid ? "invalid_utc" : null)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.joining(" · "))
        ));
        packetItems.add(buildScorecardItem(
                "legacy_usage_policy",
                "workspace",
                "Legacy manual-open policy",
                !legacyUsagePolicyEnabled ? "off" : (legacyUsagePolicyReady ? "ok" : "hold"),
                legacyUsagePolicyEnabled && !legacyUsagePolicyReady,
                "Переход к primary-flow требует контролировать долю manual legacy-open в UTC-окне и зафиксировать review-решение.",
                !legacyUsagePolicyEnabled
                        ? "not required"
                        : legacyUsageReviewTimestampInvalid
                                ? "invalid_utc"
                                : "manual_legacy_share=%.1f%% (events=%d/%d)%s%s%s%s%s".formatted(
                                manualLegacyShareRatio * 100d,
                                manualLegacyOpenEvents,
                                workspaceOpenEvents,
                                legacyUsageThresholdConfigured ? ", max=%d%%".formatted(legacyUsageMaxSharePct) : "",
                                legacyUsageShareDeltaConfigured ? ", delta=%.1fpp (max +%dpp)".formatted(manualLegacyShareDeltaPct, legacyUsageMaxShareDeltaPct) : "",
                                legacyUsageBlockedShareDeltaConfigured
                                        ? ", blocked_delta=%.1fpp (max +%dpp)".formatted(
                                        manualLegacyBlockedShareDeltaPct, legacyUsageMaxBlockedShareDeltaPct) : "",
                                blockedReasonsReviewNeeded
                                        ? ", blocked_review=%d/%d".formatted(
                                        blockedReasonsTopKeys.size() - blockedReasonsMissing.size(),
                                        blockedReasonsTopKeys.size()) : "",
                                legacyUsageDecisionPresent ? ", decision=%s".formatted(legacyUsageDecision) : ""),
                legacyUsagePolicyEnabled
                        ? "review <= %d h UTC%s%s%s%s%s%s".formatted(
                        legacyUsageReviewTtlHours,
                        legacyUsageThresholdConfigured ? ", manual share <= %d%%".formatted(legacyUsageMaxSharePct) : "",
                        legacyUsageMinWorkspaceOpenEventsConfigured
                                ? ", workspace opens >= %d".formatted(legacyUsageMinWorkspaceOpenEvents) : "",
                        legacyUsageShareDeltaConfigured
                                ? ", share delta <= +%dpp vs previous window".formatted(legacyUsageMaxShareDeltaPct) : "",
                        legacyUsageBlockedShareDeltaConfigured
                                ? ", blocked share delta <= +%dpp vs previous window".formatted(legacyUsageMaxBlockedShareDeltaPct) : "",
                        legacyBlockedReasonsReviewRequired
                                ? ", blocked top-%d reasons reviewed + follow-up".formatted(legacyBlockedReasonsTopN) : "",
                        legacyUsageDecisionRequired ? ", decision required" : "")
                        : "optional",
                legacyUsageReviewedAt != null ? legacyUsageReviewedAt.toString() : "",
                firstNonBlank(
                        legacyUsageReviewNote,
                        blockedReasonsReviewNeeded
                                ? "blocked_missing=%s%s".formatted(
                                blockedReasonsMissing.isEmpty() ? "none" : String.join(", ", blockedReasonsMissing),
                                blockedReasonsFollowupPresent ? "; followup=linked" : "; followup=missing")
                                : (legacyUsageReviewPresent ? "reviewed_by=%s; age_hours=%d".formatted(legacyUsageReviewedBy, legacyUsageReviewAgeHours) : ""))
        ));
        packetItems.add(buildScorecardItem(
                "context_minimum_profile",
                "context",
                "Customer context minimum profile",
                !contextContractEnabled ? "off" : (contextContractReady ? "ok" : (contextContractRequired ? "hold" : "attention")),
                contextContractRequired && !contextContractReady,
                "Minimum customer context должен быть формализован по сценариям: mandatory fields, source-of-truth, priority blocks и UTC-review.",
                !contextContractEnabled
                        ? "not required"
                        : contextContractReviewTimestampInvalid
                                ? "invalid_utc"
                                : "scenarios=%d, fields=%d, scenario_profiles=%d, sources=%d, blocks=%d, playbooks=%d/%d (%d%%)".formatted(
                                contextContractScenarios.size(),
                                contextContractMandatoryFields.size(),
                                contextContractMandatoryFieldsByScenario.size(),
                                contextContractSourceOfTruth.size() + contextContractSourceOfTruthByScenario.size(),
                                contextContractPriorityBlocks.size() + contextContractPriorityBlocksByScenario.size(),
                                contextContractPlaybookCoveredCount,
                                contextContractPlaybookExpectedCount,
                                contextContractPlaybookCoveragePct),
                contextContractEnabled
                        ? "scenarios + mandatory/source/priority definitions + review <= %d h UTC".formatted(contextContractReviewTtlHours)
                        : "optional",
                contextContractReviewedAt != null ? contextContractReviewedAt.toString() : "",
                contextContractDefinitionReady
                        ? firstNonBlank(
                        contextContractReviewNote,
                        "reviewed_by=%s; age_hours=%d".formatted(
                                StringUtils.hasText(contextContractReviewedBy) ? contextContractReviewedBy : "n/a",
                                contextContractReviewAgeHours))
                        : "missing=" + Stream.of(
                                contextContractScenarios.isEmpty() ? "scenarios" : null,
                                (contextContractMandatoryFields.isEmpty() && contextContractMandatoryFieldsByScenario.isEmpty())
                                        ? "mandatory_fields" : null,
                                (contextContractSourceOfTruth.isEmpty() && contextContractSourceOfTruthByScenario.isEmpty())
                                        ? "source_of_truth" : null,
                                (contextContractPriorityBlocks.isEmpty() && contextContractPriorityBlocksByScenario.isEmpty())
                                        ? "priority_blocks" : null)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.joining(", "))
        ));

        List<String> missingItems = packetItems.stream()
                .filter(item -> {
                    String status = String.valueOf(item.getOrDefault("status", "hold"));
                    return !"ok".equals(status) && !"off".equals(status);
                })
                .map(item -> String.valueOf(item.get("key")))
                .toList();
        long blockingCount = packetItems.stream()
                .filter(item -> "hold".equals(String.valueOf(item.getOrDefault("status", "hold"))))
                .count();
        long attentionCount = packetItems.stream()
                .filter(item -> "attention".equals(String.valueOf(item.getOrDefault("status", "attention"))))
                .count();
        long readyCount = packetItems.stream()
                .filter(item -> "ok".equals(String.valueOf(item.getOrDefault("status", "hold"))))
                .count();
        long offCount = packetItems.stream()
                .filter(item -> "off".equals(String.valueOf(item.getOrDefault("status", "hold"))))
                .count();
        List<String> invalidUtcItems = packetItems.stream()
                .filter(item -> String.valueOf(item.getOrDefault("current_value", "")).contains("invalid_utc"))
                .map(item -> String.valueOf(item.get("key")))
                .toList();
        boolean packetReady = packetItems.stream().allMatch(item -> {
            String status = String.valueOf(item.getOrDefault("status", "hold"));
            return "ok".equals(status) || "off".equals(status);
        });
        String packetStatus;
        if (packetReady) {
            packetStatus = "ok";
        } else if (packetRequired) {
            packetStatus = "hold";
        } else if (!scorecardSnapshotReady && workspaceOpenEvents <= 0 && alerts.isEmpty()
                && !ownerSignoffRequired && !reviewCadenceEnabled && !parityExitCriteriaEnabled && legacyOnlyScenarios.isEmpty()) {
            packetStatus = "off";
        } else {
            packetStatus = "attention";
        }

        Map<String, Object> ownerSignoff = new LinkedHashMap<>();
        ownerSignoff.put("required", ownerSignoffRequired);
        ownerSignoff.put("ready", ownerSignoffReady);
        ownerSignoff.put("signed_by", ownerSignoffBy);
        ownerSignoff.put("signed_at", ownerSignoffAt != null ? ownerSignoffAt.toString() : "");
        ownerSignoff.put("ttl_hours", ownerSignoffTtlHours);
        ownerSignoff.put("age_hours", ownerSignoffAgeHours);
        ownerSignoff.put("timestamp_invalid", ownerSignoffTimestampInvalid);

        Map<String, Object> reviewCadence = new LinkedHashMap<>();
        reviewCadence.put("enabled", reviewCadenceEnabled);
        reviewCadence.put("ready", reviewCadenceReady);
        reviewCadence.put("reviewed_by", reviewCadenceBy);
        reviewCadence.put("reviewed_at", reviewCadenceAt != null ? reviewCadenceAt.toString() : "");
        reviewCadence.put("cadence_days", reviewCadenceDays);
        reviewCadence.put("age_days", reviewCadenceAgeDays);
        reviewCadence.put("timestamp_invalid", reviewCadenceTimestampInvalid);
        reviewCadence.put("confirmed_events_in_window", reviewConfirmedEvents);
        reviewCadence.put("decision_go_events_in_window", reviewDecisionGoEvents);
        reviewCadence.put("decision_hold_events_in_window", reviewDecisionHoldEvents);
        reviewCadence.put("decision_rollback_events_in_window", reviewDecisionRollbackEvents);
        reviewCadence.put("incident_followup_linked_events_in_window", reviewIncidentFollowupLinkedEvents);
        reviewCadence.put("review_note", reviewCadenceNote == null ? "" : reviewCadenceNote);
        reviewCadence.put("decision_action", reviewDecisionAction == null ? "" : reviewDecisionAction);
        reviewCadence.put("incident_followup", reviewIncidentFollowup == null ? "" : reviewIncidentFollowup);
        reviewCadence.put("decision_required", reviewDecisionRequired);
        reviewCadence.put("incident_followup_required", reviewIncidentFollowupRequired);
        reviewCadence.put("followup_after_non_go_required", reviewFollowupForNonGoRequired);
        reviewCadence.put("previous_decision_action", previousDecisionAction == null ? "" : previousDecisionAction);
        reviewCadence.put("previous_decision_at", previousDecisionAt != null ? previousDecisionAt.toString() : "");
        reviewCadence.put("previous_decision_timestamp_invalid", previousDecisionTimestampInvalid);
        reviewCadence.put("followup_after_non_go_ready", followupForNonGoReady);
        reviewCadence.put("required_criteria", reviewRequiredCriteria);
        reviewCadence.put("checked_criteria", reviewCheckedCriteria);
        reviewCadence.put("missing_criteria", reviewMissingCriteria);
        reviewCadence.put("criteria_ready", reviewCriteriaReady);

        Map<String, Object> paritySnapshot = new LinkedHashMap<>();
        paritySnapshot.put("ready", paritySnapshotReady);
        paritySnapshot.put("workspace_open_events", workspaceOpenEvents);
        paritySnapshot.put("parity_gap_events", parityGapEvents);
        paritySnapshot.put("parity_ready_rate", parityReadyRate);
        paritySnapshot.put("top_reasons", parityRows.stream().limit(3).toList());

        Map<String, Object> incidentHistory = new LinkedHashMap<>();
        incidentHistory.put("ready", incidentHistoryReady);
        incidentHistory.put("window_days", Math.max(1, windowDays));
        incidentHistory.put("guardrail_status", String.valueOf(safeGuardrails.getOrDefault("status", "ok")));
        incidentHistory.put("alert_count", alerts.size());
        incidentHistory.put("render_error_alerts", renderErrorAlerts);
        incidentHistory.put("fallback_alerts", fallbackAlerts);
        incidentHistory.put("abandon_alerts", abandonAlerts);
        incidentHistory.put("slow_open_alerts", slowOpenAlerts);
        Map<String, Object> contextContract = new LinkedHashMap<>();
        Map<String, Object> legacyUsagePolicy = new LinkedHashMap<>();
        legacyUsagePolicy.put("enabled", legacyUsagePolicyEnabled);
        legacyUsagePolicy.put("ready", legacyUsagePolicyReady);
        legacyUsagePolicy.put("reviewed_by", legacyUsageReviewedBy == null ? "" : legacyUsageReviewedBy);
        legacyUsagePolicy.put("reviewed_at", legacyUsageReviewedAt != null ? legacyUsageReviewedAt.toString() : "");
        legacyUsagePolicy.put("review_note", legacyUsageReviewNote == null ? "" : legacyUsageReviewNote);
        legacyUsagePolicy.put("review_ttl_hours", legacyUsageReviewTtlHours);
        legacyUsagePolicy.put("review_age_hours", legacyUsageReviewAgeHours);
        legacyUsagePolicy.put("review_timestamp_invalid", legacyUsageReviewTimestampInvalid);
        legacyUsagePolicy.put("manual_legacy_open_events", manualLegacyOpenEvents);
        legacyUsagePolicy.put("manual_legacy_blocked_events", manualLegacyBlockedEvents);
        legacyUsagePolicy.put("manual_legacy_reasons_top", manualLegacyReasonBreakdown);
        legacyUsagePolicy.put("manual_legacy_blocked_reasons_top", blockedLegacyReasonBreakdown);
        legacyUsagePolicy.put("allowed_reasons", legacyManualAllowedReasons);
        legacyUsagePolicy.put("reason_catalog_required", legacyManualReasonCatalogRequired);
        legacyUsagePolicy.put("unknown_manual_reason_events", unknownManualLegacyReasons);
        legacyUsagePolicy.put("blocked_reasons_review_required", legacyBlockedReasonsReviewRequired);
        legacyUsagePolicy.put("blocked_reasons_top_n", legacyBlockedReasonsTopN);
        legacyUsagePolicy.put("blocked_reasons_reviewed", legacyBlockedReasonsReviewed);
        legacyUsagePolicy.put("blocked_reasons_followup", legacyBlockedReasonsFollowup == null ? "" : legacyBlockedReasonsFollowup);
        legacyUsagePolicy.put("blocked_reasons_missing", blockedReasonsMissing);
        legacyUsagePolicy.put("blocked_reasons_review_ready", blockedReasonsReviewReady);
        legacyUsagePolicy.put("workspace_open_events", workspaceOpenEvents);
        legacyUsagePolicy.put("manual_legacy_share_pct", Math.round(manualLegacyShareRatio * 1000d) / 10d);
        legacyUsagePolicy.put("max_manual_legacy_share_pct", legacyUsageMaxSharePct);
        legacyUsagePolicy.put("threshold_ready", legacyUsageThresholdReady);
        legacyUsagePolicy.put("min_workspace_open_events", legacyUsageMinWorkspaceOpenEvents);
        legacyUsagePolicy.put("volume_ready", legacyUsageVolumeReady);
        legacyUsagePolicy.put("max_manual_legacy_share_delta_pct", legacyUsageMaxShareDeltaPct);
        legacyUsagePolicy.put("previous_window_manual_legacy_share_pct", Math.round(previousManualLegacyShareRatio * 1000d) / 10d);
        legacyUsagePolicy.put("manual_legacy_share_delta_pct", Math.round(manualLegacyShareDeltaPct * 10d) / 10d);
        legacyUsagePolicy.put("trend_ready", legacyUsageTrendReady);
        legacyUsagePolicy.put("max_manual_legacy_blocked_share_delta_pct", legacyUsageMaxBlockedShareDeltaPct);
        legacyUsagePolicy.put("previous_window_manual_legacy_blocked_share_pct", Math.round(previousManualLegacyBlockedShareRatio * 1000d) / 10d);
        legacyUsagePolicy.put("manual_legacy_blocked_share_pct", Math.round(manualLegacyBlockedShareRatio * 1000d) / 10d);
        legacyUsagePolicy.put("manual_legacy_blocked_share_delta_pct", Math.round(manualLegacyBlockedShareDeltaPct * 10d) / 10d);
        legacyUsagePolicy.put("blocked_trend_ready", legacyUsageBlockedTrendReady);
        legacyUsagePolicy.put("decision_required", legacyUsageDecisionRequired);
        legacyUsagePolicy.put("decision", legacyUsageDecision == null ? "" : legacyUsageDecision);
        legacyUsagePolicy.put("policy_updated_events_in_window", legacyUsagePolicyUpdatedEvents);
        contextContract.put("enabled", contextContractEnabled);
        contextContract.put("required", contextContractRequired);
        contextContract.put("ready", contextContractReady);
        contextContract.put("reviewed_by", contextContractReviewedBy == null ? "" : contextContractReviewedBy);
        contextContract.put("reviewed_at", contextContractReviewedAt != null ? contextContractReviewedAt.toString() : "");
        contextContract.put("review_note", contextContractReviewNote == null ? "" : contextContractReviewNote);
        contextContract.put("review_ttl_hours", contextContractReviewTtlHours);
        contextContract.put("review_age_hours", contextContractReviewAgeHours);
        contextContract.put("review_timestamp_invalid", contextContractReviewTimestampInvalid);
        contextContract.put("scenarios", contextContractScenarios);
        contextContract.put("mandatory_fields", contextContractMandatoryFields);
        contextContract.put("mandatory_fields_by_scenario", contextContractMandatoryFieldsByScenario);
        contextContract.put("source_of_truth", contextContractSourceOfTruth);
        contextContract.put("source_of_truth_by_scenario", contextContractSourceOfTruthByScenario);
        contextContract.put("priority_blocks", contextContractPriorityBlocks);
        contextContract.put("priority_blocks_by_scenario", contextContractPriorityBlocksByScenario);
        contextContract.put("playbooks", contextContractPlaybooks);
        contextContract.put("playbook_count", contextContractPlaybooks.size());
        contextContract.put("playbook_expected_count", contextContractPlaybookExpectedCount);
        contextContract.put("playbook_covered_count", contextContractPlaybookCoveredCount);
        contextContract.put("playbook_coverage_pct", contextContractPlaybookCoveragePct);
        contextContract.put("playbook_missing_keys", contextContractPlaybookMissingKeys);
        contextContract.put("definition_ready", contextContractDefinitionReady);
        contextContract.put("definition_gaps", contextContractDefinitionGaps);
        contextContract.put("operator_focus_blocks", contextContractOperatorFocusBlocks);
        contextContract.put("progressive_disclosure_ready", !contextContractOperatorFocusBlocks.isEmpty());
        contextContract.put("operator_summary", contextContractOperatorSummary);
        contextContract.put("next_step_summary", contextContractNextStepSummary);
        contextContract.put("action_items", contextContractActionItems);
        contextContract.put("secondary_noise_followup_required", contextSecondaryDetailsFollowupRequired);
        contextContract.put("secondary_noise_management_review_required", contextSecondaryDetailsManagementReviewRequired);
        contextContract.put("secondary_noise_summary", contextSecondaryDetailsSummary);
        contextContract.put("secondary_noise_compaction_summary", contextSecondaryDetailsCompactionSummary);
        contextContract.put("secondary_noise_usage_level", contextSecondaryDetailsUsageLevel);
        contextContract.put("secondary_noise_top_section", contextSecondaryDetailsTopSection);
        contextContract.put("extra_attributes_compaction_candidate", contextExtraAttributesCompactionCandidate);
        contextContract.put("extra_attributes_open_rate_pct", contextExtraAttributesOpenRatePct);
        contextContract.put("extra_attributes_share_pct_of_secondary", contextExtraAttributesSharePctOfSecondary);
        contextContract.put("extra_attributes_usage_level", contextExtraAttributesUsageLevel);
        contextContract.put("extra_attributes_summary", contextExtraAttributesSummary);

        Map<String, Object> packet = new LinkedHashMap<>();
        packet.put("generated_at", Instant.now().toString());
        packet.put("required", packetRequired);
        packet.put("packet_ready", packetReady);
        packet.put("status", packetStatus);
        packet.put("summary", packetReady
                ? "Governance packet complete."
                : (packetRequired ? "Governance packet has blocking gaps." : "Governance packet is informative and has pending items."));
        packet.put("decision_action", String.valueOf(safeRolloutDecision.getOrDefault("action", "hold")));
        packet.put("missing_items", missingItems);
        packet.put("blocking_count", blockingCount);
        packet.put("attention_count", attentionCount);
        packet.put("ready_count", readyCount);
        packet.put("off_count", offCount);
        packet.put("invalid_utc_items", invalidUtcItems);
        packet.put("items", packetItems);
        packet.put("owner_signoff", ownerSignoff);
        packet.put("review_cadence", reviewCadence);
        packet.put("owner_signoff_expires_at_utc", ownerSignoffAt != null ? ownerSignoffAt.plusHours(ownerSignoffTtlHours).toString() : "");
        packet.put("review_due_at_utc", reviewCadenceEnabled && reviewCadenceAt != null ? reviewCadenceAt.plusDays(reviewCadenceDays).toString() : "");
        packet.put("next_review_at_utc", reviewCadenceEnabled && reviewCadenceAt != null ? reviewCadenceAt.plusDays(reviewCadenceDays).toString() : "");
        packet.put("parity_snapshot", paritySnapshot);
        packet.put("parity_exit_criteria", parityExitCriteria);
        packet.put("legacy_only_scenarios", legacyOnlyScenarios);
        packet.put("legacy_only_inventory", Map.ofEntries(
                Map.entry("status", legacyInventoryStatus),
                Map.entry("ready", legacyInventoryReady),
                Map.entry("managed", legacyInventoryManaged),
                Map.entry("reviewed_by", legacyInventoryReviewedBy == null ? "" : legacyInventoryReviewedBy),
                Map.entry("reviewed_at", legacyInventoryReviewedAt != null ? legacyInventoryReviewedAt.toString() : ""),
                Map.entry("review_note", legacyInventoryReviewNote == null ? "" : legacyInventoryReviewNote),
                Map.entry("review_age_hours", legacyInventoryReviewAgeHours),
                Map.entry("review_timestamp_invalid", legacyInventoryReviewTimestampInvalid),
                Map.entry("repeat_review_cadence_days", legacyRepeatReviewCadenceDays),
                Map.entry("review_fresh", legacyInventoryReviewFresh),
                Map.entry("repeat_review_due_at_utc", legacyRepeatReviewDueAt != null ? legacyRepeatReviewDueAt.toString() : ""),
                Map.entry("repeat_review_overdue_days", legacyRepeatReviewOverdueDays),
                Map.entry("repeat_review_required", legacyRepeatReviewRequired),
                Map.entry("repeat_review_reason", legacyRepeatReviewReason),
                Map.entry("review_queue_followup_required", legacyReviewQueueFollowupRequired),
                Map.entry("review_queue_repeat_cycles", legacyReviewQueueRepeatCycles),
                Map.entry("review_queue_oldest_deadline_at_utc", legacyReviewQueueOldestDeadline != null ? legacyReviewQueueOldestDeadline.toString() : ""),
                Map.entry("review_queue_oldest_overdue_days", legacyReviewQueueOldestOverdueDays),
                Map.entry("review_queue_closure_pressure", legacyReviewQueueClosurePressure),
                Map.entry("review_queue_escalation_required", legacyReviewQueueEscalationRequired),
                Map.entry("review_queue_management_review_required", legacyReviewQueueEscalationRequired),
                Map.entry("review_queue_management_review_summary", legacyReviewQueueManagementReviewSummary),
                Map.entry("review_queue_escalated_count", legacyReviewQueueEscalatedCount),
                Map.entry("review_queue_escalated_scenarios", legacyReviewQueueEscalatedScenarios),
                Map.entry("review_queue_consolidation_required", legacyReviewQueueConsolidationRequired),
                Map.entry("review_queue_consolidation_count", legacyReviewQueueConsolidationCandidates.size()),
                Map.entry("review_queue_consolidation_candidates", legacyReviewQueueConsolidationCandidates),
                Map.entry("review_queue_next_action_summary", legacyReviewQueueNextActionSummary),
                Map.entry("review_queue_summary", legacyReviewQueueSummary),
                Map.entry("open_count", legacyOnlyScenarios.size()),
                Map.entry("managed_count", legacyManagedScenarioCount),
                Map.entry("closure_rate_pct", legacyManagedCoveragePct),
                Map.entry("managed_coverage_pct", legacyManagedCoveragePct),
                Map.entry("unmanaged_count", legacyUnmanagedScenarioCount),
                Map.entry("owners_ready_count", legacyOwnerAssignedCount),
                Map.entry("owner_coverage_pct", legacyOwnerCoveragePct),
                Map.entry("deadlines_ready_count", legacyDeadlineAssignedCount),
                Map.entry("deadline_coverage_pct", legacyDeadlineCoveragePct),
                Map.entry("deadline_invalid_count", legacyDeadlineInvalidCount),
                Map.entry("deadline_overdue_count", legacyDeadlineOverdueCount),
                Map.entry("deadline_overdue_pct", legacyDeadlineOverduePct),
                Map.entry("overdue_scenarios", legacyOverdueScenarios),
                Map.entry("review_queue_count", legacyReviewQueueScenarios.size()),
                Map.entry("review_queue_scenarios", legacyReviewQueueScenarios),
                Map.entry("action_items", legacyInventoryActionItems),
                Map.entry("scenario_details", legacyOnlyScenarioDetails)
        ));
        packet.put("incident_history", incidentHistory);
        packet.put("context_contract", contextContract);
        packet.put("legacy_usage_policy", legacyUsagePolicy);
        packet.put("external_gate", Map.of(
                "ready", externalGateSnapshotReady,
                "enabled", toBoolean(externalSignal.get("enabled")),
                "decision_ready", toBoolean(externalSignal.get("ready_for_decision")),
                "risk_level", String.valueOf(externalSignal.getOrDefault("datamart_risk_level", "low")),
                "reviewed_at", normalizeUtcTimestamp(externalSignal.get("reviewed_at"))
        ));
        return packet;
    }

    private Map<String, Object> buildWorkspaceParityExitCriteria(long parityExitDays,
                                                                 String experimentName,
                                                                 List<String> criticalReasons) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        boolean enabled = parityExitDays > 0;
        List<String> normalizedCriticalReasons = criticalReasons == null ? List.of() : criticalReasons.stream()
                .map(this::normalizeNullString)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        snapshot.put("enabled", enabled);
        snapshot.put("window_days", parityExitDays);
        snapshot.put("critical_reasons", normalizedCriticalReasons);
        if (!enabled) {
            snapshot.put("ready", true);
            snapshot.put("critical_gap_events", 0L);
            snapshot.put("last_seen_at", "");
            snapshot.put("top_reasons", List.of());
            snapshot.put("critical_reasons_summary", "");
            snapshot.put("top_reasons_summary", "");
            return snapshot;
        }

        String filterExperiment = StringUtils.hasText(experimentName) ? experimentName.trim() : null;
        String sql = """
                SELECT reason, ticket_id, created_at
                  FROM workspace_telemetry_audit
                 WHERE created_at >= ?
                   AND created_at < ?
                   AND event_type = 'workspace_parity_gap'
                   AND (? IS NULL OR experiment_name = ?)
                 ORDER BY created_at DESC
                """;
        try {
            Instant windowEnd = Instant.now();
            Instant windowStart = windowEnd.minusSeconds(parityExitDays * 24L * 60L * 60L);
            List<Map<String, Object>> rawRows = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("reason", rs.getString("reason"));
                row.put("ticket_id", rs.getString("ticket_id"));
                Timestamp createdAt = rs.getTimestamp("created_at");
                row.put("created_at", createdAt != null ? createdAt.toInstant() : null);
                return row;
            }, Timestamp.from(windowStart), Timestamp.from(windowEnd), filterExperiment, filterExperiment);

            List<Map<String, Object>> filteredRows;
            if (normalizedCriticalReasons.isEmpty()) {
                filteredRows = rawRows;
            } else {
                Set<String> criticalReasonSet = new LinkedHashSet<>(normalizedCriticalReasons);
                filteredRows = rawRows.stream()
                        .filter(row -> dialogWorkspaceTelemetryDataService.normalizeWorkspaceGapReasons(row.get("reason")).stream()
                                .map(value -> value.toLowerCase(Locale.ROOT))
                                .anyMatch(criticalReasonSet::contains))
                        .toList();
            }

            List<Map<String, Object>> topReasons = dialogWorkspaceTelemetryDataService.aggregateWorkspaceGapReasons(filteredRows);
            Instant lastSeenAt = filteredRows.stream()
                    .map(row -> row.get("created_at") instanceof Instant instant ? instant : null)
                    .filter(Objects::nonNull)
                    .max(Instant::compareTo)
                    .orElse(null);
            snapshot.put("ready", filteredRows.isEmpty());
            snapshot.put("critical_gap_events", (long) filteredRows.size());
            snapshot.put("last_seen_at", lastSeenAt != null ? lastSeenAt.toString() : "");
            snapshot.put("top_reasons", topReasons);
            snapshot.put("critical_reasons_summary", String.join(", ", normalizedCriticalReasons));
            snapshot.put("top_reasons_summary", topReasons.stream()
                    .limit(3)
                    .map(row -> "%s(%d)".formatted(
                            String.valueOf(row.getOrDefault("reason", "unspecified")),
                            toLong(row.get("events"))))
                    .collect(Collectors.joining(", ")));
            return snapshot;
        } catch (DataAccessException ex) {
            log.warn("Unable to load parity exit criteria snapshot: {}", DialogDataAccessSupport.summarizeDataAccessException(ex));
            snapshot.put("ready", false);
            snapshot.put("critical_gap_events", 0L);
            snapshot.put("last_seen_at", "");
            snapshot.put("top_reasons", List.of());
            snapshot.put("critical_reasons_summary", String.join(", ", normalizedCriticalReasons));
            snapshot.put("top_reasons_summary", "");
            snapshot.put("error", "telemetry_unavailable");
            return snapshot;
        }
    }

    private void appendOutcomeMetricScorecardItem(List<Map<String, Object>> items,
                                                  Map<String, Object> outcomeMetrics,
                                                  String metricKey,
                                                  String label) {
        Map<String, Object> metric = outcomeMetrics.get(metricKey) instanceof Map<?, ?> value
                ? castObjectMap(value)
                : Map.of();
        if (metric.isEmpty()) {
            return;
        }
        boolean ready = toBoolean(metric.get("ready"));
        boolean regression = toBoolean(metric.get("regression"));
        String status = !ready ? "hold" : (regression ? "attention" : "ok");
        boolean blocking = !ready || regression;
        String currentValue;
        String threshold;
        if ("latency_ms".equals(String.valueOf(metric.get("type")))) {
            currentValue = "control=%s ms, test=%s ms".formatted(
                    formatNullableLong(metric.get("control_value")),
                    formatNullableLong(metric.get("test_value")));
            threshold = "Δ <= %.1f%%".formatted(safeDouble(metric.get("max_relative_regression")) * 100d);
        } else {
            currentValue = "control=%s, test=%s".formatted(
                    formatNullableLong(metric.get("control_value")),
                    formatNullableLong(metric.get("test_value")));
            threshold = "multiplier <= %.2f".formatted(safeDouble(metric.get("max_relative_multiplier")));
        }
        items.add(buildScorecardItem(
                "outcome_" + metricKey,
                "product_outcome",
                label,
                status,
                blocking,
                "Проверка outcome-метрик после включения workspace в cohort.",
                currentValue,
                threshold,
                null,
                null
        ));
    }

    private void appendExternalCheckpointScorecardItems(List<Map<String, Object>> items,
                                                        Map<String, Object> externalSignal,
                                                        boolean externalEnabled) {
        if (items == null || externalSignal == null) {
            return;
        }

        appendBinaryExternalCheckpointScorecardItem(
                items,
                "external_review",
                "External review freshness",
                externalEnabled,
                true,
                toBoolean(externalSignal.get("review_present")),
                toBoolean(externalSignal.get("review_fresh")),
                toBoolean(externalSignal.get("review_timestamp_invalid")),
                normalizeUtcTimestamp(externalSignal.get("reviewed_at")),
                "reviewed_by=%s".formatted(String.valueOf(externalSignal.getOrDefault("reviewed_by", "")).trim()),
                "review present & <= %s h".formatted(formatNullableLong(externalSignal.get("review_ttl_hours"))),
                "Ручной review перед rollout должен быть подтверждён и оставаться свежим."
        );

        appendBinaryExternalCheckpointScorecardItem(
                items,
                "external_data_freshness",
                "External KPI data freshness",
                externalEnabled,
                toBoolean(externalSignal.get("data_freshness_required")),
                toBoolean(externalSignal.get("data_updated_present")),
                toBoolean(externalSignal.get("data_fresh")),
                toBoolean(externalSignal.get("data_updated_timestamp_invalid")),
                normalizeUtcTimestamp(externalSignal.get("data_updated_at")),
                externalDataFreshnessCurrentValue(externalSignal),
                "fresh <= %s h".formatted(formatNullableLong(externalSignal.get("data_freshness_ttl_hours"))),
                "Omni-channel / finance KPI должны опираться на свежий UTC-срез данных."
        );

        appendExternalCompositeCheckpointScorecardItem(
                items,
                "external_dashboards",
                "External dashboards readiness",
                externalEnabled,
                toBoolean(externalSignal.get("dashboard_links_required")) || toBoolean(externalSignal.get("dashboard_status_required")),
                toBoolean(externalSignal.get("dashboard_links_ready")) && toBoolean(externalSignal.get("dashboard_status_ready")),
                "links=%s, status=%s".formatted(
                        toBoolean(externalSignal.get("dashboard_links_present")) ? "ready" : "missing",
                        String.valueOf(externalSignal.getOrDefault("dashboard_status", "off")).trim()),
                buildExternalDashboardThresholdLabel(externalSignal),
                null,
                String.valueOf(externalSignal.getOrDefault("dashboard_status_note", "")).trim(),
                "Ссылки на дашборды и статус витрин должны быть валидны до расширения rollout."
        );

        appendBinaryExternalCheckpointScorecardItem(
                items,
                "external_datamart_health",
                "Data-mart health",
                externalEnabled,
                toBoolean(externalSignal.get("datamart_health_required")) || toBoolean(externalSignal.get("datamart_health_freshness_required")),
                toBoolean(externalSignal.get("datamart_health_ready")),
                toBoolean(externalSignal.get("datamart_health_freshness_ready")),
                toBoolean(externalSignal.get("datamart_health_timestamp_invalid"))
                        || toBoolean(externalSignal.get("datamart_health_updated_timestamp_invalid")),
                normalizeUtcTimestamp(externalSignal.get("datamart_health_updated_at")),
                "status=%s, freshness=%s".formatted(
                        String.valueOf(externalSignal.getOrDefault("datamart_health_status", "unknown")).trim(),
                        toBoolean(externalSignal.get("datamart_health_fresh")) ? "fresh" : "stale"),
                buildDatamartHealthThresholdLabel(externalSignal),
                "Data-mart health и свежесть его статуса не должны блокировать продуктовый rollout."
        );

        appendExternalCompositeCheckpointScorecardItem(
                items,
                "external_datamart_program",
                "Data-mart delivery program",
                externalEnabled,
                toBoolean(externalSignal.get("datamart_program_blocker_required"))
                        || toBoolean(externalSignal.get("datamart_program_freshness_required"))
                        || toBoolean(externalSignal.get("datamart_timeline_required")),
                toBoolean(externalSignal.get("datamart_program_ready"))
                        && toBoolean(externalSignal.get("datamart_program_freshness_ready"))
                        && toBoolean(externalSignal.get("datamart_timeline_ready")),
                buildDatamartProgramCurrentValue(externalSignal),
                buildDatamartProgramThresholdLabel(externalSignal),
                firstNonBlank(
                        normalizeUtcTimestamp(externalSignal.get("datamart_program_updated_at")),
                        normalizeUtcTimestamp(externalSignal.get("datamart_target_ready_at"))
                ),
                buildDatamartProgramNote(externalSignal),
                "Программа data-mart должна быть без blockers, со свежим статусом и в пределах timeline."
        );

        appendExternalCompositeCheckpointScorecardItem(
                items,
                "external_dependency_ticket",
                "Dependency ticket readiness",
                externalEnabled,
                toBoolean(externalSignal.get("datamart_dependency_ticket_required"))
                        || toBoolean(externalSignal.get("datamart_dependency_ticket_freshness_required"))
                        || toBoolean(externalSignal.get("datamart_dependency_ticket_owner_required"))
                        || toBoolean(externalSignal.get("datamart_dependency_ticket_owner_contact_required"))
                        || toBoolean(externalSignal.get("datamart_dependency_ticket_owner_contact_actionable_required")),
                toBoolean(externalSignal.get("datamart_dependency_ticket_ready"))
                        && toBoolean(externalSignal.get("datamart_dependency_ticket_freshness_ready"))
                        && toBoolean(externalSignal.get("datamart_dependency_ticket_owner_ready"))
                        && toBoolean(externalSignal.get("datamart_dependency_ticket_owner_contact_ready"))
                        && toBoolean(externalSignal.get("datamart_dependency_ticket_owner_contact_actionable_ready")),
                buildDependencyTicketCurrentValue(externalSignal),
                buildDependencyTicketThresholdLabel(externalSignal),
                normalizeUtcTimestamp(externalSignal.get("datamart_dependency_ticket_updated_at")),
                buildDependencyTicketNote(externalSignal),
                "Критические внешние зависимости должны иметь ticket, owner и actionable contact."
        );

        appendExternalCompositeCheckpointScorecardItem(
                items,
                "external_datamart_contract",
                "Data-mart contract coverage",
                externalEnabled,
                toBoolean(externalSignal.get("datamart_contract_required"))
                        || toBoolean(externalSignal.get("datamart_contract_optional_coverage_required")),
                toBoolean(externalSignal.get("datamart_contract_ready")),
                buildDatamartContractCurrentValue(externalSignal),
                buildDatamartContractThresholdLabel(externalSignal),
                null,
                buildDatamartContractNote(externalSignal),
                "Контракт внешних KPI должен покрывать обязательные поля и не иметь конфликтов."
        );
    }

    private void appendBinaryExternalCheckpointScorecardItem(List<Map<String, Object>> items,
                                                             String key,
                                                             String label,
                                                             boolean externalEnabled,
                                                             boolean required,
                                                             boolean present,
                                                             boolean ready,
                                                             boolean invalidTimestamp,
                                                             String measuredAtUtc,
                                                             String currentValue,
                                                             String threshold,
                                                             String summary) {
        if (!externalEnabled) {
            items.add(buildScorecardItem(key, "external_dependencies", label, "off", false, summary, "gate disabled", "off", null, null));
            return;
        }
        if (!required) {
            items.add(buildScorecardItem(key, "external_dependencies", label, "off", false, summary, "off", "not required", null, null));
            return;
        }
        String normalizedCurrentValue = StringUtils.hasText(currentValue) ? currentValue : (present ? "present" : "missing");
        if (invalidTimestamp) {
            normalizedCurrentValue = "invalid_utc";
        } else if (!ready && !present) {
            normalizedCurrentValue = "missing";
        } else if (ready) {
            normalizedCurrentValue = normalizedCurrentValue + " (ready)";
        }
        items.add(buildScorecardItem(
                key,
                "external_dependencies",
                label,
                invalidTimestamp ? "hold" : (ready ? "ok" : "hold"),
                true,
                summary,
                normalizedCurrentValue,
                threshold,
                invalidTimestamp ? null : measuredAtUtc,
                invalidTimestamp ? "Ожидается корректная UTC timestamp." : null
        ));
    }

    private void appendExternalCompositeCheckpointScorecardItem(List<Map<String, Object>> items,
                                                                String key,
                                                                String label,
                                                                boolean externalEnabled,
                                                                boolean required,
                                                                boolean ready,
                                                                String currentValue,
                                                                String threshold,
                                                                String measuredAtUtc,
                                                                String note,
                                                                String summary) {
        if (!externalEnabled) {
            items.add(buildScorecardItem(key, "external_dependencies", label, "off", false, summary, "gate disabled", "off", null, null));
            return;
        }
        if (!required) {
            items.add(buildScorecardItem(key, "external_dependencies", label, "off", false, summary, "off", "not required", null, null));
            return;
        }
        items.add(buildScorecardItem(
                key,
                "external_dependencies",
                label,
                ready ? "ok" : "hold",
                true,
                summary,
                StringUtils.hasText(currentValue) ? currentValue : "pending",
                StringUtils.hasText(threshold) ? threshold : "ready",
                measuredAtUtc,
                StringUtils.hasText(note) ? note : null
        ));
    }

    private String externalDataFreshnessCurrentValue(Map<String, Object> externalSignal) {
        return "updated=%s, freshness=%s".formatted(
                toBoolean(externalSignal.get("data_updated_present")) ? "present" : "missing",
                toBoolean(externalSignal.get("data_fresh")) ? "fresh" : "stale");
    }

    private String buildExternalDashboardThresholdLabel(Map<String, Object> externalSignal) {
        List<String> requirements = new ArrayList<>();
        if (toBoolean(externalSignal.get("dashboard_links_required"))) {
            requirements.add("links=ready");
        }
        if (toBoolean(externalSignal.get("dashboard_status_required"))) {
            requirements.add("status=healthy");
        }
        return requirements.isEmpty() ? "not required" : String.join(", ", requirements);
    }

    private String buildDatamartHealthThresholdLabel(Map<String, Object> externalSignal) {
        List<String> requirements = new ArrayList<>();
        if (toBoolean(externalSignal.get("datamart_health_required"))) {
            requirements.add("status=healthy");
        }
        if (toBoolean(externalSignal.get("datamart_health_freshness_required"))) {
            requirements.add("fresh <= %s h".formatted(formatNullableLong(externalSignal.get("datamart_health_ttl_hours"))));
        }
        return requirements.isEmpty() ? "not required" : String.join(", ", requirements);
    }

    private String buildDatamartProgramCurrentValue(Map<String, Object> externalSignal) {
        return "status=%s, freshness=%s, timeline=%s".formatted(
                String.valueOf(externalSignal.getOrDefault("datamart_program_status", "unknown")).trim(),
                toBoolean(externalSignal.get("datamart_program_fresh")) ? "fresh" : "stale",
                toBoolean(externalSignal.get("datamart_timeline_ready")) ? "ready" : "hold");
    }

    private String buildDatamartProgramThresholdLabel(Map<String, Object> externalSignal) {
        List<String> requirements = new ArrayList<>();
        if (toBoolean(externalSignal.get("datamart_program_blocker_required"))) {
            requirements.add("status!=blocked");
        }
        if (toBoolean(externalSignal.get("datamart_program_freshness_required"))) {
            requirements.add("fresh <= %s h".formatted(formatNullableLong(externalSignal.get("datamart_program_ttl_hours"))));
        }
        if (toBoolean(externalSignal.get("datamart_timeline_required"))) {
            requirements.add("target within grace");
        }
        return requirements.isEmpty() ? "not required" : String.join(", ", requirements);
    }

    private String buildDatamartProgramNote(Map<String, Object> externalSignal) {
        List<String> notes = new ArrayList<>();
        String programNote = String.valueOf(externalSignal.getOrDefault("datamart_program_note", "")).trim();
        if (StringUtils.hasText(programNote)) {
            notes.add(programNote);
        }
        String blockerUrl = String.valueOf(externalSignal.getOrDefault("datamart_program_blocker_url", "")).trim();
        if (StringUtils.hasText(blockerUrl)) {
            notes.add("blocker=" + blockerUrl);
        }
        List<String> riskReasons = safeStringList(externalSignal.get("datamart_risk_reasons"));
        if (!riskReasons.isEmpty()) {
            notes.add("risk=" + String.join("|", riskReasons));
        }
        return notes.isEmpty() ? null : String.join(", ", notes);
    }

    private String buildDependencyTicketCurrentValue(Map<String, Object> externalSignal) {
        return "ticket=%s, freshness=%s, owner=%s, contact=%s".formatted(
                toBoolean(externalSignal.get("datamart_dependency_ticket_present")) ? "ready" : "missing",
                toBoolean(externalSignal.get("datamart_dependency_ticket_fresh")) ? "fresh" : "stale",
                toBoolean(externalSignal.get("datamart_dependency_ticket_owner_present")) ? "ready" : "missing",
                toBoolean(externalSignal.get("datamart_dependency_ticket_owner_contact_actionable")) ? "actionable" : "not_actionable");
    }

    private String buildDependencyTicketThresholdLabel(Map<String, Object> externalSignal) {
        List<String> requirements = new ArrayList<>();
        if (toBoolean(externalSignal.get("datamart_dependency_ticket_required"))) {
            requirements.add("ticket=ready");
        }
        if (toBoolean(externalSignal.get("datamart_dependency_ticket_freshness_required"))) {
            requirements.add("fresh <= %s h".formatted(formatNullableLong(externalSignal.get("datamart_dependency_ticket_ttl_hours"))));
        }
        if (toBoolean(externalSignal.get("datamart_dependency_ticket_owner_required"))) {
            requirements.add("owner=ready");
        }
        if (toBoolean(externalSignal.get("datamart_dependency_ticket_owner_contact_required"))) {
            requirements.add("contact=ready");
        }
        if (toBoolean(externalSignal.get("datamart_dependency_ticket_owner_contact_actionable_required"))) {
            requirements.add("contact=actionable");
        }
        return requirements.isEmpty() ? "not required" : String.join(", ", requirements);
    }

    private String buildDependencyTicketNote(Map<String, Object> externalSignal) {
        List<String> notes = new ArrayList<>();
        String url = String.valueOf(externalSignal.getOrDefault("datamart_dependency_ticket_url", "")).trim();
        if (StringUtils.hasText(url)) {
            notes.add("url=" + url);
        }
        String owner = String.valueOf(externalSignal.getOrDefault("datamart_dependency_ticket_owner", "")).trim();
        if (StringUtils.hasText(owner)) {
            notes.add("owner=" + owner);
        }
        String contact = String.valueOf(externalSignal.getOrDefault("datamart_dependency_ticket_owner_contact", "")).trim();
        if (StringUtils.hasText(contact)) {
            notes.add("contact=" + contact);
        }
        return notes.isEmpty() ? null : String.join(", ", notes);
    }

    private String buildDatamartContractCurrentValue(Map<String, Object> externalSignal) {
        return "mandatory=%s%%, optional=%s%%, blocking_gaps=%s, non_blocking_gaps=%s".formatted(
                formatNullableLong(externalSignal.get("datamart_contract_mandatory_coverage_pct")),
                formatNullableLong(externalSignal.get("datamart_contract_optional_coverage_pct")),
                formatNullableLong(externalSignal.get("datamart_contract_blocking_gap_count")),
                formatNullableLong(externalSignal.get("datamart_contract_non_blocking_gap_count")));
    }

    private String buildDatamartContractThresholdLabel(Map<String, Object> externalSignal) {
        List<String> requirements = new ArrayList<>();
        if (toBoolean(externalSignal.get("datamart_contract_required"))) {
            requirements.add("mandatory coverage=100%");
        }
        if (toBoolean(externalSignal.get("datamart_contract_optional_coverage_required"))
                && toBoolean(externalSignal.get("datamart_contract_optional_coverage_gate_active"))) {
            requirements.add("optional >= %s%%".formatted(formatNullableLong(externalSignal.get("datamart_contract_optional_min_coverage_pct"))));
        }
        requirements.add("no configuration conflict");
        return String.join(", ", requirements);
    }

    private String buildDatamartContractNote(Map<String, Object> externalSignal) {
        List<String> notes = new ArrayList<>();
        List<String> missingMandatory = safeStringList(externalSignal.get("datamart_contract_missing_mandatory_fields"));
        if (!missingMandatory.isEmpty()) {
            notes.add("missing_mandatory=" + String.join("|", missingMandatory));
        }
        List<String> missingOptional = safeStringList(externalSignal.get("datamart_contract_missing_optional_fields"));
        if (!missingOptional.isEmpty()) {
            notes.add("missing_optional=" + String.join("|", missingOptional));
        }
        List<String> overlaps = safeStringList(externalSignal.get("datamart_contract_overlapping_fields"));
        if (!overlaps.isEmpty()) {
            notes.add("overlap=" + String.join("|", overlaps));
        }
        if (toBoolean(externalSignal.get("datamart_contract_configuration_conflict"))) {
            notes.add("configuration_conflict");
        }
        return notes.isEmpty() ? null : String.join(", ", notes);
    }

    private Map<String, Object> buildScorecardItem(String key,
                                                   String category,
                                                   String label,
                                                   String status,
                                                   boolean blocking,
                                                   String summary,
                                                   String currentValue,
                                                   String threshold,
                                                   String measuredAtUtc,
                                                   String note) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", key);
        item.put("category", category);
        item.put("label", label);
        item.put("status", normalizeScorecardStatus(status));
        item.put("blocking", blocking);
        item.put("summary", normalizeNullString(summary));
        item.put("current_value", normalizeNullString(currentValue));
        item.put("threshold", normalizeNullString(threshold));
        item.put("measured_at", normalizeUtcTimestamp(measuredAtUtc));
        item.put("note", normalizeNullString(note));
        return item;
    }

    private String normalizeScorecardStatus(String value) {
        String normalized = normalizeNullString(value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ok", "attention", "hold", "off" -> normalized;
            default -> "hold";
        };
    }

    private List<Map<String, Object>> safeListOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add(castObjectMap(map));
            }
        }
        return result;
    }

    private List<String> safeStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .map(this::normalizeNullString)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<String> resolveDialogConfigStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(this::normalizeNullString)
                    .map(item -> item.toLowerCase(Locale.ROOT))
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();
        }
        String normalized = normalizeNullString(value == null ? null : String.valueOf(value));
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        return Arrays.stream(normalized.split("[,;\n]"))
                .map(String::trim)
                .map(item -> item.toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private Map<String, List<String>> resolveDialogConfigStringListMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = normalizeNullString(entry.getKey() == null ? null : String.valueOf(entry.getKey()));
            if (!StringUtils.hasText(key)) {
                continue;
            }
            List<String> items = resolveDialogConfigStringList(entry.getValue());
            if (!items.isEmpty()) {
                normalized.put(key.toLowerCase(Locale.ROOT), items);
            }
        }
        return normalized;
    }

    private Map<String, Map<String, Object>> resolveLegacyOnlyScenarioMetadataMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Map<String, Object>> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String scenario = normalizeNullString(entry.getKey() == null ? null : String.valueOf(entry.getKey()));
            if (!StringUtils.hasText(scenario) || !(entry.getValue() instanceof Map<?, ?> item)) {
                continue;
            }
            Object ownerRaw = item.get("owner");
            String owner = normalizeNullString(ownerRaw == null ? null : String.valueOf(ownerRaw));
            Object deadlineValue = item.get("deadline_at_utc");
            String deadlineRaw = normalizeNullString(deadlineValue == null ? null : String.valueOf(deadlineValue));
            OffsetDateTime deadline = parseReviewTimestamp(deadlineRaw);
            boolean deadlineTimestampInvalid = StringUtils.hasText(deadlineRaw) && deadline == null;
            Object noteRaw = item.get("note");
            String note = normalizeNullString(noteRaw == null ? null : String.valueOf(noteRaw));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("owner", owner == null ? "" : owner);
            payload.put("deadline_at_utc", deadline != null ? deadline.toString() : "");
            payload.put("deadline_timestamp_invalid", deadlineTimestampInvalid);
            payload.put("note", note == null ? "" : note);
            normalized.put(scenario.toLowerCase(Locale.ROOT), payload);
        }
        return normalized;
    }

    private Map<String, Map<String, String>> resolveContextContractPlaybooks(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Map<String, String>> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = normalizeNullString(entry.getKey() == null ? null : String.valueOf(entry.getKey()));
            if (!StringUtils.hasText(key) || !(entry.getValue() instanceof Map<?, ?> item)) {
                continue;
            }
            Object labelRaw = item.get("label");
            Object urlRaw = item.get("url");
            Object summaryRaw = item.get("summary");
            String label = normalizeNullString(labelRaw == null ? null : String.valueOf(labelRaw));
            String url = normalizeNullString(urlRaw == null ? null : String.valueOf(urlRaw));
            String summary = normalizeNullString(summaryRaw == null ? null : String.valueOf(summaryRaw));
            if (!StringUtils.hasText(url) || (!url.startsWith("https://") && !url.startsWith("http://"))) {
                continue;
            }
            normalized.put(key.toLowerCase(Locale.ROOT), Map.of(
                    "label", label == null ? "Playbook" : label,
                    "url", url,
                    "summary", summary == null ? "" : summary));
        }
        return normalized;
    }

    private List<String> buildContextContractPlaybookExpectedKeys(List<String> mandatoryFields,
                                                                  Map<String, List<String>> mandatoryFieldsByScenario,
                                                                  List<String> sourceOfTruth,
                                                                  Map<String, List<String>> sourceOfTruthByScenario,
                                                                  List<String> priorityBlocks,
                                                                  Map<String, List<String>> priorityBlocksByScenario) {
        LinkedHashSet<String> expected = new LinkedHashSet<>();
        mandatoryFields.forEach(field -> {
            String normalizedField = normalizeNullString(field);
            if (StringUtils.hasText(normalizedField)) {
                expected.add("mandatory_field:" + normalizedField.toLowerCase(Locale.ROOT));
            }
        });
        mandatoryFieldsByScenario.values().forEach(values -> values.forEach(field -> {
            String normalizedField = normalizeNullString(field);
            if (StringUtils.hasText(normalizedField)) {
                expected.add("mandatory_field:" + normalizedField.toLowerCase(Locale.ROOT));
            }
        }));
        Stream.concat(sourceOfTruth.stream(), sourceOfTruthByScenario.values().stream().flatMap(Collection::stream))
                .map(this::normalizeContextContractPlaybookScopedSourceKey)
                .filter(StringUtils::hasText)
                .forEach(expected::add);
        priorityBlocks.forEach(block -> {
            String normalizedBlock = normalizeNullString(block);
            if (StringUtils.hasText(normalizedBlock)) {
                expected.add("priority_block:" + normalizedBlock.toLowerCase(Locale.ROOT));
            }
        });
        priorityBlocksByScenario.values().forEach(values -> values.forEach(block -> {
            String normalizedBlock = normalizeNullString(block);
            if (StringUtils.hasText(normalizedBlock)) {
                expected.add("priority_block:" + normalizedBlock.toLowerCase(Locale.ROOT));
            }
        }));
        return new ArrayList<>(expected);
    }

    private String normalizeContextContractPlaybookScopedSourceKey(String sourceRule) {
        String normalized = normalizeNullString(sourceRule);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String[] parts = normalized.split(":");
        if (parts.length < 2) {
            return null;
        }
        String field = normalizeNullString(parts[0]);
        String source = normalizeNullString(parts[1]);
        if (!StringUtils.hasText(field) || !StringUtils.hasText(source)) {
            return null;
        }
        return "source_of_truth:%s:%s".formatted(
                field.toLowerCase(Locale.ROOT),
                source.toLowerCase(Locale.ROOT));
    }

    private boolean hasContextContractPlaybookCoverage(Map<String, Map<String, String>> playbooks, String key) {
        if (playbooks.isEmpty()) {
            return false;
        }
        String normalizedKey = normalizeNullString(key);
        if (!StringUtils.hasText(normalizedKey)) {
            return false;
        }
        String lowerKey = normalizedKey.toLowerCase(Locale.ROOT);
        if (playbooks.containsKey(lowerKey)) {
            return true;
        }
        int separatorIndex = lowerKey.indexOf(':');
        if (separatorIndex <= 0) {
            return false;
        }
        String typeKey = lowerKey.substring(0, separatorIndex);
        return playbooks.containsKey(typeKey);
    }

    private double safeDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return 0d;
        }
    }

    private String formatNullableLong(Object value) {
        Long number = extractNullableLong(value);
        return number != null ? String.valueOf(number) : "—";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String normalizeUtcTimestamp(Object rawValue) {
        OffsetDateTime parsed = parseReviewTimestamp(rawValue == null ? null : String.valueOf(rawValue));
        return parsed != null ? parsed.withOffsetSameInstant(ZoneOffset.UTC).toString() : "";
    }

    private Map<String, Object> buildWorkspaceRolloutDecision(Map<String, Object> cohortComparison,
                                                               Map<String, Object> guardrails) {
        Map<String, Object> decision = new LinkedHashMap<>();
        Map<String, Object> safeCohortComparison = cohortComparison == null ? Map.of() : cohortComparison;
        Map<String, Object> safeGuardrails = guardrails == null ? Map.of() : guardrails;
        String winner = String.valueOf(safeCohortComparison.getOrDefault("winner", "insufficient_data"));
        boolean sampleSizeOk = toBoolean(safeCohortComparison.get("sample_size_ok"));
        Map<String, Object> kpiSignal = safeCohortComparison.get("kpi_signal") instanceof Map<?, ?> kpi
                ? castObjectMap(kpi)
                : Map.of();
        Map<String, Object> kpiOutcomeSignal = safeCohortComparison.get("kpi_outcome_signal") instanceof Map<?, ?> kpiOutcome
                ? castObjectMap(kpiOutcome)
                : Map.of();
        boolean kpiSignalReady = toBoolean(kpiSignal.get("ready_for_decision"));
        boolean kpiOutcomeReady = toBoolean(kpiOutcomeSignal.get("ready_for_decision"));
        boolean kpiOutcomeRegressions = toBoolean(kpiOutcomeSignal.get("has_regression"));
        Map<String, Object> externalKpiSignal = dialogWorkspaceExternalKpiService.buildExternalKpiSignal();
        boolean externalKpiReady = toBoolean(externalKpiSignal.get("ready_for_decision"));
        String guardrailStatus = String.valueOf(safeGuardrails.getOrDefault("status", "ok"));
        boolean hasGuardrailIssues = "attention".equalsIgnoreCase(guardrailStatus);

        String action;
        String rationale;
        if (!sampleSizeOk) {
            action = "hold";
            rationale = "Недостаточно данных в control/test выборках для безопасного rollout decision.";
        } else if (!kpiSignalReady) {
            action = "hold";
            rationale = "Недостаточно продуктовых KPI-сигналов (FRT/TTR/SLA breach) для автоматического rollout decision.";
        } else if (!kpiOutcomeReady) {
            action = "hold";
            rationale = "Недостаточно измерений продуктовых KPI-результатов (FRT/TTR/SLA breach) для автоматического rollout decision.";
        } else if (!externalKpiReady) {
            action = "hold";
            rationale = "Внешние omni-channel/финансовые KPI не подтверждены: rollout остаётся на hold до прохождения data-mart checkpoint.";
        } else if (kpiOutcomeRegressions) {
            action = "hold";
            rationale = "Зафиксирована деградация по FRT/TTR/SLA breach в test cohort: rollout оставлен на hold до стабилизации.";
        } else if (hasGuardrailIssues) {
            action = "rollback";
            rationale = "Guardrails в статусе attention: rollout нужно приостановить и разобрать отклонения.";
        } else if ("test".equalsIgnoreCase(winner)) {
            action = "scale_up";
            rationale = "Test cohort выигрывает без технических регрессий: можно расширять долю workspace_v1.";
        } else {
            action = "hold";
            rationale = "Control cohort остаётся стабильнее: оставляем текущий охват и продолжаем наблюдение.";
        }

        decision.put("action", action);
        decision.put("winner", winner);
        decision.put("guardrails_status", guardrailStatus);
        decision.put("sample_size_ok", sampleSizeOk);
        decision.put("kpi_signal_ready", kpiSignalReady);
        decision.put("kpi_outcome_ready", kpiOutcomeReady);
        decision.put("kpi_outcome_regressions", kpiOutcomeRegressions);
        decision.put("external_kpi_signal", externalKpiSignal);
        decision.put("rationale", rationale);
        return decision;
    }

    private String normalizeNullString(String value) {
        if (value == null || "null".equalsIgnoreCase(value)) {
            return "";
        }
        return value.trim();
    }

    private OffsetDateTime parseReviewTimestamp(String rawValue) {
        String value = normalizeNullString(rawValue);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).withOffsetSameInstant(ZoneOffset.UTC);
        } catch (Exception ignored) {
            // fallback to legacy datetime-local without timezone
        }
        try {
            return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC);
        } catch (Exception ignored) {
            return null;
        }
    }

    private long resolveLongDialogConfigValue(String key, long fallback, long minInclusive, long maxInclusive) {
        Object value = resolveDialogConfigValue(key);
        if (value == null) {
            return fallback;
        }
        long parsed;
        if (value instanceof Number number) {
            parsed = number.longValue();
        } else {
            try {
                parsed = Long.parseLong(String.valueOf(value).trim());
            } catch (Exception ignored) {
                return fallback;
            }
        }
        if (parsed < minInclusive || parsed > maxInclusive) {
            return fallback;
        }
        return parsed;
    }

    private Long resolveNullableLongDialogConfigValue(String key, long minInclusive, long maxInclusive) {
        Object value = resolveDialogConfigValue(key);
        if (value == null) {
            return null;
        }
        long parsed;
        if (value instanceof Number number) {
            parsed = number.longValue();
        } else {
            try {
                parsed = Long.parseLong(String.valueOf(value).trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        if (parsed < minInclusive || parsed > maxInclusive) {
            return null;
        }
        return parsed;
    }

    private boolean resolveBooleanDialogConfigValue(String key, boolean fallback) {
        Object value = resolveDialogConfigValue(key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            return fallback;
        }
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    private Object resolveDialogConfigValue(String key) {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        if (settings == null || settings.isEmpty()) {
            return null;
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> map)) {
            return null;
        }
        return map.get(key);
    }

    private Map<String, Object> castObjectMap(Map<?, ?> source) {
        Map<String, Object> payload = new LinkedHashMap<>();
        source.forEach((key, value) -> payload.put(String.valueOf(key), value));
        return payload;
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value == null) {
            return false;
        }
        String normalized = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(normalized) || "1".equals(normalized);
    }


    private Long extractNullableLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
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
            log.warn("Unable to inspect {} columns: {}", tableName, DialogDataAccessSupport.summarizeDataAccessException(ex));
            return Set.of();
        }
    }

    public List<String> loadTicketCategories(String ticketId) {
        return dialogConversationReadService.loadTicketCategories(ticketId);
    }

    public void setTicketCategories(String ticketId, List<String> categories) {
        dialogTicketLifecycleService.setTicketCategories(ticketId, categories);
    }

    public DialogResolveResult resolveTicket(String ticketId, String operator, List<String> categories) {
        return dialogTicketLifecycleService.resolveTicket(ticketId, operator, categories);
    }

    public DialogResolveResult reopenTicket(String ticketId, String operator) {
        return dialogTicketLifecycleService.reopenTicket(ticketId, operator);
    }
}


