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
    private final DialogWorkspaceRolloutAssessmentService dialogWorkspaceRolloutAssessmentService;
    private final DialogWorkspaceRolloutGovernanceService dialogWorkspaceRolloutGovernanceService;
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
                         DialogWorkspaceRolloutAssessmentService dialogWorkspaceRolloutAssessmentService,
                         DialogWorkspaceRolloutGovernanceService dialogWorkspaceRolloutGovernanceService,
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
        this.dialogWorkspaceRolloutAssessmentService = dialogWorkspaceRolloutAssessmentService;
        this.dialogWorkspaceRolloutGovernanceService = dialogWorkspaceRolloutGovernanceService;
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
        Map<String, Object> rolloutDecision = dialogWorkspaceRolloutAssessmentService.buildRolloutDecision(cohortComparison, guardrails);
        payload.put("rollout_decision", rolloutDecision);
        Map<String, Object> rolloutScorecard = dialogWorkspaceRolloutAssessmentService.buildRolloutScorecard(totals, cohortComparison, guardrails, rolloutDecision);
        payload.put("rollout_scorecard", rolloutScorecard);
        payload.put("rollout_packet", dialogWorkspaceRolloutGovernanceService.buildWorkspaceRolloutPacket(
                totals,
                guardrails,
                rolloutDecision,
                rolloutScorecard,
                payload.get("gap_breakdown"),
                windowDays,
                experimentName));
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
        Map<String, Object> rolloutDecision = dialogWorkspaceRolloutAssessmentService.buildRolloutDecision(cohortComparison, guardrails);
        payload.put("rollout_decision", rolloutDecision);
        Map<String, Object> rolloutScorecard = dialogWorkspaceRolloutAssessmentService.buildRolloutScorecard(totals, cohortComparison, guardrails, rolloutDecision);
        payload.put("rollout_scorecard", rolloutScorecard);
        payload.put("rollout_packet", dialogWorkspaceRolloutGovernanceService.buildWorkspaceRolloutPacket(
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


