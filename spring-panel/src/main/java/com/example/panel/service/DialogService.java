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
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    public List<Map<String, Object>> loadClientDialogHistory(Long userId, String currentTicketId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        try {
            String sql = """
                    SELECT m.ticket_id, COALESCE(t.status, 'pending') AS status, m.created_at,
                           COALESCE(m.problem, '') AS problem
                      FROM messages m
                      LEFT JOIN tickets t ON t.ticket_id = m.ticket_id
                     WHERE m.user_id = ?
                       AND (? IS NULL OR m.ticket_id <> ?)
                     ORDER BY substr(m.created_at, 1, 19) DESC
                     LIMIT ?
                    """;
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> historyItem = new LinkedHashMap<>();
                historyItem.put("ticket_id", rs.getString("ticket_id"));
                historyItem.put("status", rs.getString("status"));
                historyItem.put("created_at", rs.getString("created_at"));
                historyItem.put("problem", rs.getString("problem"));
                return historyItem;
            }, userId, currentTicketId, currentTicketId, limit);
        } catch (DataAccessException ex) {
            log.warn("Unable to load client dialog history for user {}: {}", userId, ex.getMessage());
            return List.of();
        }
    }

    public Map<String, Object> loadClientProfileEnrichment(Long userId) {
        if (userId == null) {
            return Map.of();
        }
        try {
            String sql = """
                    SELECT COUNT(DISTINCT m.ticket_id) AS total_dialogs,
                           COUNT(DISTINCT CASE
                               WHEN lower(COALESCE(t.status, 'pending')) NOT IN ('resolved', 'closed') THEN m.ticket_id
                           END) AS open_dialogs,
                           COUNT(DISTINCT CASE
                               WHEN lower(COALESCE(t.status, 'pending')) IN ('resolved', 'closed')
                                    AND datetime(substr(COALESCE(t.resolved_at, t.created_at, m.created_at), 1, 19)) >= datetime('now', '-30 day')
                               THEN m.ticket_id
                           END) AS resolved_30d,
                           MIN(m.created_at) AS first_seen_at,
                           MAX(COALESCE(t.resolved_at, t.created_at, m.created_at)) AS last_ticket_activity_at
                      FROM messages m
                      LEFT JOIN tickets t ON t.ticket_id = m.ticket_id
                     WHERE m.user_id = ?
                    """;
            return jdbcTemplate.query(sql, rs -> {
                if (!rs.next()) {
                    return Map.<String, Object>of();
                }
                Map<String, Object> enrichment = new LinkedHashMap<>();
                enrichment.put("total_dialogs", rs.getInt("total_dialogs"));
                enrichment.put("open_dialogs", rs.getInt("open_dialogs"));
                enrichment.put("resolved_30d", rs.getInt("resolved_30d"));
                enrichment.put("first_seen_at", rs.getString("first_seen_at"));
                enrichment.put("last_ticket_activity_at", rs.getString("last_ticket_activity_at"));
                return enrichment;
            }, userId);
        } catch (DataAccessException ex) {
            log.warn("Unable to load client profile enrichment for user {}: {}", userId, ex.getMessage());
            return Map.of();
        }
    }

    public List<Map<String, Object>> loadRelatedEvents(String ticketId, int limit) {
        if (!StringUtils.hasText(ticketId) || limit <= 0) {
            return List.of();
        }
        try {
            String sqlWithAudit = """
                    SELECT actor, event_at, event_type, action, result, detail
                      FROM (
                            SELECT COALESCE(sender, 'system') AS actor,
                                   COALESCE(timestamp, CURRENT_TIMESTAMP) AS event_at,
                                   COALESCE(message_type, 'event') AS event_type,
                                   NULL AS action,
                                   NULL AS result,
                                   message AS detail,
                                   id AS sort_id
                              FROM chat_history
                             WHERE ticket_id = ?
                               AND (lower(COALESCE(sender, '')) IN ('operator', 'support', 'admin', 'system')
                                    OR lower(COALESCE(message_type, '')) IN ('system', 'status', 'event'))
                            UNION ALL
                            SELECT COALESCE(t.assignee, t.creator, 'system') AS actor,
                                   COALESCE(th.at, t.last_activity_at, t.created_at, CURRENT_TIMESTAMP) AS event_at,
                                   'workflow' AS event_type,
                                   NULL AS action,
                                   NULL AS result,
                                   COALESCE(th.text, 'Обновление workflow') AS detail,
                                   th.id AS sort_id
                              FROM task_links tl
                              JOIN tasks t ON t.id = tl.task_id
                              LEFT JOIN task_history th ON th.task_id = t.id
                             WHERE tl.ticket_id = ?
                               AND COALESCE(trim(th.text), '') <> ''
                            UNION ALL
                            SELECT COALESCE(actor, 'system') AS actor,
                                   COALESCE(created_at, CURRENT_TIMESTAMP) AS event_at,
                                   'audit' AS event_type,
                                   action AS action,
                                   result AS result,
                                   detail AS detail,
                                   id AS sort_id
                              FROM dialog_action_audit
                             WHERE ticket_id = ?
                       ) events
                     ORDER BY substr(event_at, 1, 19) DESC, COALESCE(sort_id, 0) DESC
                     LIMIT ?
                    """;
            return mapRelatedEvents(sqlWithAudit, ticketId, ticketId, ticketId, limit);
        } catch (DataAccessException ex) {
            log.warn("Unable to load related events with audit trail for ticket {}: {}. Fallback to legacy events.", ticketId, ex.getMessage());
            try {
            String sql = """
                    SELECT actor, event_at, event_type, action, result, detail
                      FROM (
                            SELECT COALESCE(sender, 'system') AS actor,
                                   COALESCE(timestamp, CURRENT_TIMESTAMP) AS event_at,
                                   COALESCE(message_type, 'event') AS event_type,
                                   NULL AS action,
                                   NULL AS result,
                                   message AS detail,
                                   id AS sort_id
                              FROM chat_history
                             WHERE ticket_id = ?
                               AND (lower(COALESCE(sender, '')) IN ('operator', 'support', 'admin', 'system')
                                    OR lower(COALESCE(message_type, '')) IN ('system', 'status', 'event'))
                            UNION ALL
                            SELECT COALESCE(t.assignee, t.creator, 'system') AS actor,
                                   COALESCE(th.at, t.last_activity_at, t.created_at, CURRENT_TIMESTAMP) AS event_at,
                                   'workflow' AS event_type,
                                   NULL AS action,
                                   NULL AS result,
                                   COALESCE(th.text, 'Обновление workflow') AS detail,
                                   th.id AS sort_id
                              FROM task_links tl
                              JOIN tasks t ON t.id = tl.task_id
                              LEFT JOIN task_history th ON th.task_id = t.id
                             WHERE tl.ticket_id = ?
                               AND COALESCE(trim(th.text), '') <> ''
                       ) events
                     ORDER BY substr(event_at, 1, 19) DESC, COALESCE(sort_id, 0) DESC
                     LIMIT ?
                    """;
                return mapRelatedEvents(sql, ticketId, ticketId, limit);
            } catch (DataAccessException fallbackEx) {
                log.warn("Unable to load related events for ticket {}: {}", ticketId, fallbackEx.getMessage());
                return List.of();
            }
        }
    }

    public void logDialogActionAudit(String ticketId, String actor, String action, String result, String detail) {
        if (!StringUtils.hasText(ticketId) || !StringUtils.hasText(action)) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO dialog_action_audit (ticket_id, actor, action, result, detail, created_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    ticketId,
                    StringUtils.hasText(actor) ? actor.trim() : "anonymous",
                    action.trim(),
                    StringUtils.hasText(result) ? result.trim() : "unknown",
                    StringUtils.hasText(detail) ? detail.trim() : null);
        } catch (DataAccessException ex) {
            log.warn("Unable to persist dialog action audit for ticket {}: {}", ticketId, ex.getMessage());
        }
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
        if (!StringUtils.hasText(eventType)) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO workspace_telemetry_audit (
                        actor, event_type, event_group, ticket_id, reason, error_code, contract_version,
                        duration_ms, experiment_name, experiment_cohort, operator_segment,
                        primary_kpis, secondary_kpis, template_id, template_name, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    trimOrNull(actor),
                    trimOrNull(eventType),
                    trimOrNull(eventGroup),
                    trimOrNull(ticketId),
                    trimOrNull(reason),
                    trimOrNull(errorCode),
                    trimOrNull(contractVersion),
                    durationMs,
                    trimOrNull(experimentName),
                    trimOrNull(experimentCohort),
                    trimOrNull(operatorSegment),
                    joinCsv(primaryKpis),
                    joinCsv(secondaryKpis),
                    trimOrNull(templateId),
                    trimOrNull(templateName));
        } catch (DataAccessException ex) {
            log.warn("Unable to persist workspace telemetry event '{}': {}", eventType, ex.getMessage());
        }
    }

    public Map<String, Object> loadWorkspaceTelemetrySummary(int days, String experimentName) {
        int windowDays = Math.max(1, Math.min(days, 30));
        List<Map<String, Object>> rows = loadWorkspaceTelemetryRows(windowDays, experimentName, 0);
        List<Map<String, Object>> previousRows = loadWorkspaceTelemetryRows(windowDays, experimentName, windowDays);
        List<Map<String, Object>> shiftRows = aggregateWorkspaceTelemetryRows(rows, "shift");
        List<Map<String, Object>> teamRows = aggregateWorkspaceTelemetryRows(rows, "team");
        Map<String, Object> totals = computeWorkspaceTelemetryTotals(rows);
        Map<String, Object> previousTotals = computeWorkspaceTelemetryTotals(previousRows);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("window_days", windowDays);
        payload.put("generated_at", Instant.now().toString());
        payload.put("totals", totals);
        payload.put("previous_totals", previousTotals);
        payload.put("period_comparison", buildWorkspaceTelemetryComparison(totals, previousTotals));
        payload.put("rows", rows);
        payload.put("by_shift", shiftRows);
        payload.put("by_team", teamRows);
        payload.put("guardrails", buildWorkspaceGuardrails(totals, previousTotals, rows, shiftRows, teamRows));
        return payload;
    }

    private Map<String, Object> computeWorkspaceTelemetryTotals(List<Map<String, Object>> rows) {
        Map<String, Object> totals = new LinkedHashMap<>();
        long events = rows.stream().mapToLong(row -> toLong(row.get("events"))).sum();
        long renderErrors = rows.stream().mapToLong(row -> toLong(row.get("render_errors"))).sum();
        long fallbacks = rows.stream().mapToLong(row -> toLong(row.get("fallbacks"))).sum();
        long abandons = rows.stream().mapToLong(row -> toLong(row.get("abandons"))).sum();
        long slowOpenEvents = rows.stream().mapToLong(row -> toLong(row.get("slow_open_events"))).sum();

        long weightedOpenCount = rows.stream()
                .mapToLong(row -> Math.max(toLong(row.get("events"))
                        - toLong(row.get("render_errors"))
                        - toLong(row.get("fallbacks"))
                        - toLong(row.get("abandons")), 0L))
                .sum();
        long weightedOpenSum = rows.stream()
                .mapToLong(row -> {
                    Long avgOpenMs = extractNullableLong(row.get("avg_open_ms"));
                    if (avgOpenMs == null) {
                        return 0L;
                    }
                    long rowWeight = Math.max(toLong(row.get("events"))
                            - toLong(row.get("render_errors"))
                            - toLong(row.get("fallbacks"))
                            - toLong(row.get("abandons")), 0L);
                    return avgOpenMs * rowWeight;
                })
                .sum();
        Long avgOpenMs = weightedOpenCount > 0 ? Math.round((double) weightedOpenSum / weightedOpenCount) : null;

        totals.put("events", events);
        totals.put("render_errors", renderErrors);
        totals.put("fallbacks", fallbacks);
        totals.put("abandons", abandons);
        totals.put("slow_open_events", slowOpenEvents);
        totals.put("avg_open_ms", avgOpenMs);
        return totals;
    }

    private Map<String, Object> buildWorkspaceTelemetryComparison(Map<String, Object> currentTotals,
                                                                  Map<String, Object> previousTotals) {
        long currentEvents = toLong(currentTotals.get("events"));
        long previousEvents = toLong(previousTotals.get("events"));

        double currentRenderRate = safeRate(toLong(currentTotals.get("render_errors")), currentEvents);
        double previousRenderRate = safeRate(toLong(previousTotals.get("render_errors")), previousEvents);
        double currentFallbackRate = safeRate(toLong(currentTotals.get("fallbacks")), currentEvents);
        double previousFallbackRate = safeRate(toLong(previousTotals.get("fallbacks")), previousEvents);
        double currentAbandonRate = safeRate(toLong(currentTotals.get("abandons")), currentEvents);
        double previousAbandonRate = safeRate(toLong(previousTotals.get("abandons")), previousEvents);
        double currentSlowOpenRate = safeRate(toLong(currentTotals.get("slow_open_events")), currentEvents);
        double previousSlowOpenRate = safeRate(toLong(previousTotals.get("slow_open_events")), previousEvents);

        Long currentAvgOpenMs = extractNullableLong(currentTotals.get("avg_open_ms"));
        Long previousAvgOpenMs = extractNullableLong(previousTotals.get("avg_open_ms"));
        Long avgOpenMsDelta = currentAvgOpenMs != null && previousAvgOpenMs != null
                ? currentAvgOpenMs - previousAvgOpenMs
                : null;

        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("current_events", currentEvents);
        comparison.put("previous_events", previousEvents);
        comparison.put("render_error_rate_delta", currentRenderRate - previousRenderRate);
        comparison.put("fallback_rate_delta", currentFallbackRate - previousFallbackRate);
        comparison.put("abandon_rate_delta", currentAbandonRate - previousAbandonRate);
        comparison.put("slow_open_rate_delta", currentSlowOpenRate - previousSlowOpenRate);
        comparison.put("avg_open_ms_delta", avgOpenMsDelta);
        return comparison;
    }

    private Map<String, Object> buildWorkspaceGuardrails(Map<String, Object> totals,
                                                          Map<String, Object> previousTotals,
                                                          List<Map<String, Object>> cohortRows,
                                                          List<Map<String, Object>> shiftRows,
                                                          List<Map<String, Object>> teamRows) {
        long events = Math.max(1L, toLong(totals.get("events")));
        long renderErrors = toLong(totals.get("render_errors"));
        long fallbacks = toLong(totals.get("fallbacks"));
        long abandons = toLong(totals.get("abandons"));
        long slowOpenEvents = toLong(totals.get("slow_open_events"));

        double renderErrorRate = (double) renderErrors / events;
        double fallbackRate = (double) fallbacks / events;
        double abandonRate = (double) abandons / events;
        double slowOpenRate = (double) slowOpenEvents / events;

        Map<String, Object> rates = new LinkedHashMap<>();
        rates.put("render_error", renderErrorRate);
        rates.put("fallback", fallbackRate);
        rates.put("abandon", abandonRate);
        rates.put("slow_open", slowOpenRate);

        List<Map<String, Object>> alerts = new ArrayList<>();
        appendGuardrailAlert(alerts,
                "render_error",
                "Доля workspace_render_error превышает SLO 1%.",
                renderErrorRate,
                0.01d,
                "below_or_equal");
        appendGuardrailAlert(alerts,
                "fallback",
                "Доля fallback в legacy превышает SLO 3%.",
                fallbackRate,
                0.03d,
                "below_or_equal");
        appendGuardrailAlert(alerts,
                "abandon",
                "Доля abandon в workspace превышает рабочий порог 10%.",
                abandonRate,
                0.10d,
                "below_or_equal");
        appendGuardrailAlert(alerts,
                "slow_open",
                "Доля медленных workspace_open_ms (>2000ms) превышает рабочий порог 5%.",
                slowOpenRate,
                0.05d,
                "below_or_equal");
        appendRegressionGuardrailAlerts(alerts, totals, previousTotals);

        int minDimensionEvents = 20;
        appendDimensionGuardrailAlerts(alerts, cohortRows, "cohort", "experiment_cohort", minDimensionEvents);
        appendDimensionGuardrailAlerts(alerts, shiftRows, "shift", "shift", minDimensionEvents);
        appendDimensionGuardrailAlerts(alerts, teamRows, "team", "team", minDimensionEvents);

        Map<String, Object> guardrails = new LinkedHashMap<>();
        guardrails.put("status", alerts.isEmpty() ? "ok" : "attention");
        guardrails.put("rates", rates);
        guardrails.put("alerts", alerts);
        return guardrails;
    }

    private void appendRegressionGuardrailAlerts(List<Map<String, Object>> alerts,
                                                 Map<String, Object> currentTotals,
                                                 Map<String, Object> previousTotals) {
        long currentEvents = toLong(currentTotals.get("events"));
        long previousEvents = toLong(previousTotals.get("events"));
        if (currentEvents < 20 || previousEvents < 20) {
            return;
        }

        appendRegressionAlert(
                alerts,
                "render_error",
                "Регрессия render_error относительно предыдущего окна.",
                safeRate(toLong(currentTotals.get("render_errors")), currentEvents),
                safeRate(toLong(previousTotals.get("render_errors")), previousEvents),
                0.005d,
                1.35d,
                currentEvents,
                previousEvents);
        appendRegressionAlert(
                alerts,
                "fallback",
                "Регрессия fallback относительно предыдущего окна.",
                safeRate(toLong(currentTotals.get("fallbacks")), currentEvents),
                safeRate(toLong(previousTotals.get("fallbacks")), previousEvents),
                0.01d,
                1.35d,
                currentEvents,
                previousEvents);
        appendRegressionAlert(
                alerts,
                "abandon",
                "Регрессия abandon относительно предыдущего окна.",
                safeRate(toLong(currentTotals.get("abandons")), currentEvents),
                safeRate(toLong(previousTotals.get("abandons")), previousEvents),
                0.02d,
                1.25d,
                currentEvents,
                previousEvents);
        appendRegressionAlert(
                alerts,
                "slow_open",
                "Регрессия slow_open относительно предыдущего окна.",
                safeRate(toLong(currentTotals.get("slow_open_events")), currentEvents),
                safeRate(toLong(previousTotals.get("slow_open_events")), previousEvents),
                0.015d,
                1.3d,
                currentEvents,
                previousEvents);
    }

    private void appendRegressionAlert(List<Map<String, Object>> alerts,
                                       String metric,
                                       String message,
                                       double current,
                                       double previous,
                                       double minAbsoluteDelta,
                                       double minRelativeMultiplier,
                                       long currentEvents,
                                       long previousEvents) {
        double delta = current - previous;
        if (delta <= minAbsoluteDelta) {
            return;
        }
        double safeBase = Math.max(previous, 0.0001d);
        double multiplier = current / safeBase;
        if (multiplier <= minRelativeMultiplier) {
            return;
        }
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("metric", metric);
        alert.put("message", message);
        alert.put("value", current);
        alert.put("previous_value", previous);
        alert.put("delta", delta);
        alert.put("threshold", minAbsoluteDelta);
        alert.put("expected", "regression_delta_below_threshold");
        alert.put("scope", "global");
        alert.put("segment", "all");
        alert.put("events", currentEvents);
        alert.put("previous_events", previousEvents);
        alerts.add(alert);
    }

    private void appendDimensionGuardrailAlerts(List<Map<String, Object>> alerts,
                                                List<Map<String, Object>> rows,
                                                String scope,
                                                String field,
                                                int minEvents) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : rows) {
            long events = toLong(row.get("events"));
            if (events < minEvents) {
                continue;
            }
            double renderErrorRate = safeRate(toLong(row.get("render_errors")), events);
            double fallbackRate = safeRate(toLong(row.get("fallbacks")), events);
            double slowOpenRate = safeRate(toLong(row.get("slow_open_events")), events);
            double abandonRate = safeRate(toLong(row.get("abandons")), events);
            String dimensionValue = String.valueOf(row.getOrDefault(field, "unknown"));

            appendGuardrailAlert(alerts,
                    "render_error",
                    "Отклонение render_error в срезе " + scope + ": " + dimensionValue + ".",
                    renderErrorRate,
                    0.01d,
                    "below_or_equal",
                    scope,
                    dimensionValue,
                    events);
            appendGuardrailAlert(alerts,
                    "fallback",
                    "Отклонение fallback в срезе " + scope + ": " + dimensionValue + ".",
                    fallbackRate,
                    0.03d,
                    "below_or_equal",
                    scope,
                    dimensionValue,
                    events);
            appendGuardrailAlert(alerts,
                    "abandon",
                    "Отклонение abandon в срезе " + scope + ": " + dimensionValue + ".",
                    abandonRate,
                    0.10d,
                    "below_or_equal",
                    scope,
                    dimensionValue,
                    events);
            appendGuardrailAlert(alerts,
                    "slow_open",
                    "Отклонение slow_open в срезе " + scope + ": " + dimensionValue + ".",
                    slowOpenRate,
                    0.05d,
                    "below_or_equal",
                    scope,
                    dimensionValue,
                    events);
        }
    }

    private double safeRate(long value, long events) {
        if (events <= 0) {
            return 0d;
        }
        return (double) value / events;
    }

    private void appendGuardrailAlert(List<Map<String, Object>> alerts,
                                      String metric,
                                      String message,
                                      double value,
                                      double threshold,
                                      String expected) {
        if (value <= threshold) {
            return;
        }
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("metric", metric);
        alert.put("message", message);
        alert.put("value", value);
        alert.put("threshold", threshold);
        alert.put("expected", expected);
        alerts.add(alert);
    }

    private void appendGuardrailAlert(List<Map<String, Object>> alerts,
                                      String metric,
                                      String message,
                                      double value,
                                      double threshold,
                                      String expected,
                                      String scope,
                                      String segment,
                                      long events) {
        if (value <= threshold) {
            return;
        }
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("metric", metric);
        alert.put("message", message);
        alert.put("value", value);
        alert.put("threshold", threshold);
        alert.put("expected", expected);
        alert.put("scope", scope);
        alert.put("segment", segment);
        alert.put("events", events);
        alerts.add(alert);
    }

    private List<Map<String, Object>> aggregateWorkspaceTelemetryRows(List<Map<String, Object>> rows, String dimension) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        Map<String, Map<String, Object>> aggregated = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String operatorSegment = row != null ? trimOrNull((String) row.get("operator_segment")) : null;
            String key = resolveOperatorDimension(operatorSegment, dimension);
            Map<String, Object> bucket = aggregated.computeIfAbsent(key, ignored -> createTelemetryBucket(dimension, key));
            mergeTelemetryMetrics(bucket, row);
        }

        return aggregated.values().stream()
                .peek(bucket -> {
                    bucket.remove("_open_weight");
                    bucket.remove("_open_sum");
                })
                .sorted((left, right) -> Long.compare(toLong(right.get("events")), toLong(left.get("events"))))
                .toList();
    }

    private Map<String, Object> createTelemetryBucket(String dimension, String key) {
        Map<String, Object> bucket = new LinkedHashMap<>();
        bucket.put(dimension, key);
        bucket.put("events", 0L);
        bucket.put("render_errors", 0L);
        bucket.put("fallbacks", 0L);
        bucket.put("abandons", 0L);
        bucket.put("slow_open_events", 0L);
        bucket.put("avg_open_ms", null);
        bucket.put("_open_weight", 0L);
        bucket.put("_open_sum", 0L);
        return bucket;
    }

    private void mergeTelemetryMetrics(Map<String, Object> bucket, Map<String, Object> row) {
        if (bucket == null || row == null) {
            return;
        }

        long events = toLong(row.get("events"));
        long renderErrors = toLong(row.get("render_errors"));
        long fallbacks = toLong(row.get("fallbacks"));
        long abandons = toLong(row.get("abandons"));
        long slowOpenEvents = toLong(row.get("slow_open_events"));

        bucket.put("events", toLong(bucket.get("events")) + events);
        bucket.put("render_errors", toLong(bucket.get("render_errors")) + renderErrors);
        bucket.put("fallbacks", toLong(bucket.get("fallbacks")) + fallbacks);
        bucket.put("abandons", toLong(bucket.get("abandons")) + abandons);
        bucket.put("slow_open_events", toLong(bucket.get("slow_open_events")) + slowOpenEvents);

        Long avgOpen = extractNullableLong(row.get("avg_open_ms"));
        if (avgOpen == null) {
            return;
        }

        long weight = Math.max(events - renderErrors - fallbacks - abandons, 1L);
        long nextWeight = toLong(bucket.get("_open_weight")) + weight;
        long nextSum = toLong(bucket.get("_open_sum")) + avgOpen * weight;
        bucket.put("_open_weight", nextWeight);
        bucket.put("_open_sum", nextSum);
        bucket.put("avg_open_ms", Math.round((double) nextSum / nextWeight));
    }

    private String resolveOperatorDimension(String operatorSegment, String dimension) {
        if (!StringUtils.hasText(operatorSegment)) {
            return "unknown";
        }
        String normalized = operatorSegment.trim().toLowerCase();
        if ("team".equals(dimension)) {
            String explicitTeam = extractSegmentValue(normalized, "team");
            if (StringUtils.hasText(explicitTeam)) {
                return explicitTeam;
            }
            int separator = normalized.indexOf('/');
            if (separator > 0) {
                return normalized.substring(0, separator).trim();
            }
            return normalized.contains("_shift") ? "support" : normalized;
        }

        String explicitShift = extractSegmentValue(normalized, "shift");
        if (StringUtils.hasText(explicitShift)) {
            return explicitShift;
        }
        if (normalized.contains("night")) {
            return "night";
        }
        if (normalized.contains("morning")) {
            return "morning";
        }
        if (normalized.contains("evening")) {
            return "evening";
        }
        if (normalized.contains("day")) {
            return "day";
        }
        return "unknown";
    }

    private String extractSegmentValue(String segment, String key) {
        if (!StringUtils.hasText(segment) || !StringUtils.hasText(key)) {
            return null;
        }
        String needle = key + "=";
        int start = segment.indexOf(needle);
        if (start < 0) {
            return null;
        }
        String tail = segment.substring(start + needle.length());
        int delimiter = tail.indexOf(';');
        String value = delimiter >= 0 ? tail.substring(0, delimiter) : tail;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Long extractNullableLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private List<Map<String, Object>> loadWorkspaceTelemetryRows(int windowDays, String experimentName, int offsetDays) {
        String filterExperiment = trimOrNull(experimentName);
        String sql = """
                SELECT COALESCE(experiment_cohort, 'unknown') AS experiment_cohort,
                       COALESCE(operator_segment, 'unknown') AS operator_segment,
                       COUNT(*) AS events,
                       SUM(CASE WHEN event_type = 'workspace_render_error' THEN 1 ELSE 0 END) AS render_errors,
                       SUM(CASE WHEN event_type = 'workspace_fallback_to_legacy' THEN 1 ELSE 0 END) AS fallbacks,
                       SUM(CASE WHEN event_type = 'workspace_abandon' THEN 1 ELSE 0 END) AS abandons,
                       SUM(CASE WHEN event_type = 'workspace_open_ms' AND COALESCE(duration_ms, 0) > 2000 THEN 1 ELSE 0 END) AS slow_open_events,
                       AVG(CASE WHEN event_type = 'workspace_open_ms' THEN duration_ms END) AS avg_open_ms
                  FROM workspace_telemetry_audit
                 WHERE created_at >= ?
                   AND created_at < ?
                   AND (? IS NULL OR experiment_name = ?)
                 GROUP BY COALESCE(experiment_cohort, 'unknown'), COALESCE(operator_segment, 'unknown')
                 ORDER BY events DESC, experiment_cohort ASC, operator_segment ASC
                """;
        try {
            Instant windowEnd = Instant.now().minusSeconds(Math.max(0, offsetDays) * 24L * 60L * 60L);
            Instant windowStart = windowEnd.minusSeconds(windowDays * 24L * 60L * 60L);
            Timestamp cutoffStart = Timestamp.from(windowStart);
            Timestamp cutoffEnd = Timestamp.from(windowEnd);
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("experiment_cohort", rs.getString("experiment_cohort"));
                item.put("operator_segment", rs.getString("operator_segment"));
                item.put("events", rs.getLong("events"));
                item.put("render_errors", rs.getLong("render_errors"));
                item.put("fallbacks", rs.getLong("fallbacks"));
                item.put("abandons", rs.getLong("abandons"));
                item.put("slow_open_events", rs.getLong("slow_open_events"));
                item.put("avg_open_ms", rs.getObject("avg_open_ms") != null ? Math.round(rs.getDouble("avg_open_ms")) : null);
                return item;
            }, cutoffStart, cutoffEnd, filterExperiment, filterExperiment);
        } catch (DataAccessException ex) {
            log.warn("Unable to load workspace telemetry summary: {}", ex.getMessage());
            return List.of();
        }
    }

    private String joinCsv(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isBlank() ? null : joined;
    }

    private String trimOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private List<Map<String, Object>> mapRelatedEvents(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String eventType = rs.getString("event_type");
            String detail = rs.getString("detail");
            if ("audit".equalsIgnoreCase(eventType)) {
                String action = rs.getString("action");
                String result = rs.getString("result");
                detail = formatAuditDetail(action, result, detail);
            }
            Map<String, Object> eventItem = new LinkedHashMap<>();
            eventItem.put("actor", rs.getString("actor"));
            eventItem.put("timestamp", rs.getString("event_at"));
            eventItem.put("type", eventType);
            eventItem.put("detail", detail);
            return eventItem;
        }, args);
    }

    private String formatAuditDetail(String action, String result, String detail) {
        String safeAction = StringUtils.hasText(action) ? action.trim() : "action";
        String safeResult = StringUtils.hasText(result) ? result.trim() : "unknown";
        if (!StringUtils.hasText(detail)) {
            return safeAction + ": " + safeResult;
        }
        return safeAction + ": " + safeResult + " (" + detail.trim() + ")";
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
