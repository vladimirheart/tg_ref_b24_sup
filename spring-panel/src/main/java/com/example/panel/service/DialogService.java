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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
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

@Service
public class DialogService {

    private static final Logger log = LoggerFactory.getLogger(DialogService.class);
    private static final List<String> DEFAULT_REQUIRED_PRIMARY_KPIS = List.of("frt", "ttr", "sla_breach");
    private static final long DEFAULT_MIN_KPI_EVENTS_FOR_DECISION = 10L;
    private static final double DEFAULT_MIN_KPI_COVERAGE_RATE_FOR_DECISION = 0.05d;
    private static final long DEFAULT_KPI_OUTCOME_MIN_SAMPLES_PER_COHORT = 5L;
    private static final double DEFAULT_KPI_OUTCOME_FRT_MAX_RELATIVE_REGRESSION = 0.10d;
    private static final double DEFAULT_KPI_OUTCOME_TTR_MAX_RELATIVE_REGRESSION = 0.10d;
    private static final double DEFAULT_KPI_OUTCOME_SLA_BREACH_MAX_ABSOLUTE_DELTA = 0.02d;
    private static final double DEFAULT_KPI_OUTCOME_SLA_BREACH_MAX_RELATIVE_MULTIPLIER = 1.20d;
    private static final double DEFAULT_WORKSPACE_WINNER_MIN_OPEN_IMPROVEMENT = 0d;
    private static final boolean DEFAULT_EXTERNAL_KPI_GATE_ENABLED = false;
    private static final long DEFAULT_EXTERNAL_KPI_REVIEW_TTL_HOURS = 168L;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATA_FRESHNESS_REQUIRED = false;
    private static final long DEFAULT_EXTERNAL_KPI_DATA_FRESHNESS_TTL_HOURS = 48L;
    private static final boolean DEFAULT_EXTERNAL_KPI_DASHBOARD_LINKS_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_DASHBOARD_STATUS_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_OWNER_RUNBOOK_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_HEALTH_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_HEALTH_FRESHNESS_REQUIRED = false;
    private static final long DEFAULT_EXTERNAL_KPI_DATAMART_HEALTH_TTL_HOURS = 48L;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_PROGRAM_BLOCKER_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_PROGRAM_FRESHNESS_REQUIRED = false;
    private static final long DEFAULT_EXTERNAL_KPI_DATAMART_PROGRAM_TTL_HOURS = 24L * 14L;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_TIMELINE_REQUIRED = false;
    private static final long DEFAULT_EXTERNAL_KPI_DATAMART_TIMELINE_GRACE_HOURS = 24L;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_FRESHNESS_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_OWNER_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_OWNER_CONTACT_REQUIRED = false;
    private static final boolean DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_OWNER_CONTACT_ACTIONABLE_REQUIRED = false;
    private static final long DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_TTL_HOURS = 24L * 14L;
    private static final String DEFAULT_EXTERNAL_KPI_CONTRACT_VERSION = "v1";
    private static final Set<String> DEFAULT_EXTERNAL_KPI_CONTRACT_MANDATORY_FIELDS = Set.of("frt", "ttr", "sla_breach", "cost_per_contact");
    private static final int DEFAULT_EXTERNAL_KPI_CONTRACT_OPTIONAL_MIN_COVERAGE_PCT = 80;
    private static final Set<String> DEFAULT_REQUIRED_KPI_OUTCOME_KEYS = Set.of("frt", "ttr", "sla_breach");
    private static final double DEFAULT_GUARDRAIL_RENDER_ERROR_RATE = 0.01d;
    private static final double DEFAULT_GUARDRAIL_FALLBACK_RATE = 0.03d;
    private static final double DEFAULT_GUARDRAIL_ABANDON_RATE = 0.10d;
    private static final double DEFAULT_GUARDRAIL_SLOW_OPEN_RATE = 0.05d;
    private static final int DEFAULT_DIMENSION_MIN_EVENTS = 20;
    private static final int DEFAULT_COHORT_MIN_EVENTS = 30;

    private final JdbcTemplate jdbcTemplate;
    private final SharedConfigService sharedConfigService;

    public DialogService(JdbcTemplate jdbcTemplate,
                         SharedConfigService sharedConfigService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sharedConfigService = sharedConfigService;
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
                           COALESCE(m.user_id, t.user_id) AS user_id,
                           m.username, m.client_name, m.business,
                           COALESCE(m.channel_id, t.channel_id) AS channel_id,
                           c.channel_name AS channel_name,
                           m.city, m.location_name,
                       m.problem,
                       COALESCE(m.created_at, t.created_at) AS created_at,
                       t.status, t.resolved_by, t.resolved_at,
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
                           WHEN tr.responsible = ? THEN (
                               SELECT COUNT(*)
                                 FROM chat_history ch
                                WHERE ch.ticket_id = t.ticket_id
                                  AND lower(ch.sender) NOT IN ('operator', 'support', 'admin', 'system')
                                  AND ch.timestamp > COALESCE(
                                      tr.last_read_at,
                                      (
                                          SELECT MAX(op.timestamp)
                                            FROM chat_history op
                                           WHERE op.ticket_id = t.ticket_id
                                             AND lower(op.sender) IN ('operator', 'support', 'admin', 'system')
                                      ),
                                      ''
                                  )
                           )
                           ELSE 0
                       END AS unread_count
                  FROM tickets t
                  LEFT JOIN messages m ON m.id = (
                      SELECT m2.id
                        FROM messages m2
                       WHERE m2.ticket_id = t.ticket_id
                       ORDER BY substr(m2.created_at, 1, 19) DESC,
                                m2.id DESC
                       LIMIT 1
                  )
                  LEFT JOIN channels c ON c.id = COALESCE(m.channel_id, t.channel_id)
                  LEFT JOIN ticket_responsibles tr ON tr.ticket_id = t.ticket_id
                  LEFT JOIN client_statuses cs ON cs.user_id = COALESCE(m.user_id, t.user_id)
                       AND cs.updated_at = (
                           SELECT MAX(updated_at) FROM client_statuses WHERE user_id = COALESCE(m.user_id, t.user_id)
                       )
                  ORDER BY substr(COALESCE(m.created_at, t.created_at), 1, 19) DESC,
                           t.ticket_id DESC
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
                           COALESCE(m.user_id, t.user_id) AS user_id,
                           m.username, m.client_name, m.business,
                           COALESCE(m.channel_id, t.channel_id) AS channel_id,
                           c.channel_name AS channel_name,
                           m.city, m.location_name,
                           m.problem,
                           COALESCE(m.created_at, t.created_at) AS created_at,
                           t.status, t.resolved_by, t.resolved_at,
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
                               WHEN tr.responsible = ? THEN (
                                   SELECT COUNT(*)
                                     FROM chat_history ch
                                    WHERE ch.ticket_id = t.ticket_id
                                      AND lower(ch.sender) NOT IN ('operator', 'support', 'admin', 'system')
                                      AND ch.timestamp > COALESCE(
                                          tr.last_read_at,
                                          (
                                              SELECT MAX(op.timestamp)
                                                FROM chat_history op
                                               WHERE op.ticket_id = t.ticket_id
                                                 AND lower(op.sender) IN ('operator', 'support', 'admin', 'system')
                                          ),
                                          ''
                                      )
                               )
                               ELSE 0
                           END AS unread_count
                      FROM tickets t
                      LEFT JOIN messages m ON m.id = (
                          SELECT m2.id
                            FROM messages m2
                           WHERE m2.ticket_id = t.ticket_id
                           ORDER BY substr(m2.created_at, 1, 19) DESC,
                                    m2.id DESC
                           LIMIT 1
                      )
                      LEFT JOIN channels c ON c.id = COALESCE(m.channel_id, t.channel_id)
                      LEFT JOIN ticket_responsibles tr ON tr.ticket_id = t.ticket_id
                      LEFT JOIN client_statuses cs ON cs.user_id = COALESCE(m.user_id, t.user_id)
                           AND cs.updated_at = (
                               SELECT MAX(updated_at) FROM client_statuses WHERE user_id = COALESCE(m.user_id, t.user_id)
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
        Map<String, Object> workspaceTelemetryConfig = resolveWorkspaceTelemetryConfig();
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
        Map<String, Object> cohortComparison = buildWorkspaceCohortComparison(rows, workspaceTelemetryConfig);
        payload.put("cohort_comparison", cohortComparison);
        payload.put("rows", rows);
        payload.put("by_shift", shiftRows);
        payload.put("by_team", teamRows);
        payload.put("gap_breakdown", buildWorkspaceGapBreakdown(windowDays, experimentName));
        Map<String, Object> guardrails = buildWorkspaceGuardrails(totals, previousTotals, rows, shiftRows, teamRows, workspaceTelemetryConfig);
        payload.put("guardrails", guardrails);
        Map<String, Object> rolloutDecision = buildWorkspaceRolloutDecision(cohortComparison, guardrails);
        payload.put("rollout_decision", rolloutDecision);
        payload.put("rollout_scorecard", buildWorkspaceRolloutScorecard(totals, cohortComparison, guardrails, rolloutDecision));
        return payload;
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
        Map<String, Object> externalKpiSignal = buildExternalKpiSignal();
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

    private Map<String, Object> buildExternalKpiSignal() {
        Map<String, Object> signal = new LinkedHashMap<>();
        boolean gateEnabled = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_gate_enabled",
                DEFAULT_EXTERNAL_KPI_GATE_ENABLED);
        boolean omnichannelReady = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_omnichannel_ready",
                false);
        boolean financeReady = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_finance_ready",
                false);
        String note = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_note"));
        String datamartOwner = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_owner"));
        String datamartRunbookUrl = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_runbook_url"));
        String datamartDependencyTicketUrl = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_dependency_ticket_url")));
        String datamartDependencyTicketOwner = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_dependency_ticket_owner")));
        String datamartDependencyTicketOwnerContact = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact")));
        String datamartDependencyTicketUpdatedAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_dependency_ticket_updated_at"));
        String reviewedBy = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_reviewed_by"));
        String reviewedAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_reviewed_at"));
        long reviewTtlHours = resolveLongDialogConfigValue(
                "workspace_rollout_external_kpi_review_ttl_hours",
                DEFAULT_EXTERNAL_KPI_REVIEW_TTL_HOURS,
                1,
                24 * 90L);
        boolean dataFreshnessRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_data_freshness_required",
                DEFAULT_EXTERNAL_KPI_DATA_FRESHNESS_REQUIRED);
        boolean dashboardLinksRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_dashboard_links_required",
                DEFAULT_EXTERNAL_KPI_DASHBOARD_LINKS_REQUIRED);
        boolean dashboardStatusRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_dashboard_status_required",
                DEFAULT_EXTERNAL_KPI_DASHBOARD_STATUS_REQUIRED);
        boolean ownerRunbookRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_owner_runbook_required",
                DEFAULT_EXTERNAL_KPI_OWNER_RUNBOOK_REQUIRED);
        boolean datamartHealthRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_health_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_HEALTH_REQUIRED);
        boolean datamartHealthFreshnessRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_health_freshness_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_HEALTH_FRESHNESS_REQUIRED);
        boolean datamartProgramBlockerRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_program_blocker_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_PROGRAM_BLOCKER_REQUIRED);
        boolean datamartProgramFreshnessRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_program_freshness_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_PROGRAM_FRESHNESS_REQUIRED);
        boolean datamartTimelineRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_timeline_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_TIMELINE_REQUIRED);
        boolean datamartDependencyTicketRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_dependency_ticket_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_REQUIRED);
        boolean datamartDependencyTicketFreshnessRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_dependency_ticket_freshness_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_FRESHNESS_REQUIRED);
        boolean datamartDependencyTicketOwnerRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_OWNER_REQUIRED);
        boolean datamartDependencyTicketOwnerContactRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_OWNER_CONTACT_REQUIRED);
        boolean datamartDependencyTicketOwnerContactActionableRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_dependency_ticket_owner_contact_actionable_required",
                DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_OWNER_CONTACT_ACTIONABLE_REQUIRED);
        boolean datamartContractRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_contract_required",
                false);
        String datamartContractVersion = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_contract_version")));
        Set<String> datamartContractMandatoryFields = parseExternalKpiContractFields(
                resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_contract_mandatory_fields"),
                DEFAULT_EXTERNAL_KPI_CONTRACT_MANDATORY_FIELDS);
        Set<String> datamartContractOptionalFields = parseExternalKpiContractFields(
                resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_contract_optional_fields"),
                Set.of());
        Set<String> datamartContractAvailableFields = parseExternalKpiContractFields(
                resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_contract_available_fields"),
                Set.of());
        boolean datamartContractOptionalCoverageRequired = resolveBooleanDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_contract_optional_coverage_required",
                false);
        int datamartContractOptionalMinCoveragePct = (int) resolveLongDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_contract_optional_min_coverage_pct",
                DEFAULT_EXTERNAL_KPI_CONTRACT_OPTIONAL_MIN_COVERAGE_PCT,
                0,
                100);
        String datamartHealthStatus = normalizeDatamartHealthStatus(
                normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_health_status"))));
        String dashboardStatus = normalizeDatamartHealthStatus(
                normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_dashboard_status"))));
        String dashboardStatusNote = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_dashboard_status_note")));
        String datamartHealthNote = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_health_note")));
        String datamartHealthUpdatedAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_health_updated_at"));
        long datamartHealthTtlHours = resolveLongDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_health_ttl_hours",
                DEFAULT_EXTERNAL_KPI_DATAMART_HEALTH_TTL_HOURS,
                1,
                24 * 90L);
        String datamartProgramStatus = normalizeDatamartProgramStatus(
                normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_program_status"))));
        String datamartProgramNote = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_program_note")));
        String datamartProgramBlockerUrl = normalizeNullString(String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_program_blocker_url")));
        String datamartProgramUpdatedAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_program_updated_at"));
        long datamartProgramTtlHours = resolveLongDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_program_ttl_hours",
                DEFAULT_EXTERNAL_KPI_DATAMART_PROGRAM_TTL_HOURS,
                1,
                24 * 90L);
        String datamartTargetReadyAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_datamart_target_ready_at"));
        long datamartTimelineGraceHours = resolveLongDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_timeline_grace_hours",
                DEFAULT_EXTERNAL_KPI_DATAMART_TIMELINE_GRACE_HOURS,
                0,
                24 * 30L);
        String dataUpdatedAtRaw = String.valueOf(resolveDialogConfigValue("workspace_rollout_external_kpi_data_updated_at"));
        long dataFreshnessTtlHours = resolveLongDialogConfigValue(
                "workspace_rollout_external_kpi_data_freshness_ttl_hours",
                DEFAULT_EXTERNAL_KPI_DATA_FRESHNESS_TTL_HOURS,
                1,
                24 * 30L);
        long datamartDependencyTicketTtlHours = resolveLongDialogConfigValue(
                "workspace_rollout_external_kpi_datamart_dependency_ticket_ttl_hours",
                DEFAULT_EXTERNAL_KPI_DATAMART_DEPENDENCY_TICKET_TTL_HOURS,
                1,
                24 * 90L);
        if ("null".equalsIgnoreCase(note)) {
            note = "";
        }
        OffsetDateTime reviewedAt = parseReviewTimestamp(reviewedAtRaw);
        boolean reviewTimestampInvalid = StringUtils.hasText(normalizeNullString(reviewedAtRaw)) && reviewedAt == null;
        boolean reviewPresent = reviewedAt != null && StringUtils.hasText(reviewedBy);
        boolean reviewFresh = false;
        long reviewAgeHours = -1;
        if (reviewedAt != null) {
            reviewAgeHours = Math.max(0, java.time.Duration.between(reviewedAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
            reviewFresh = reviewAgeHours <= reviewTtlHours;
        }
        boolean reviewReady = !gateEnabled || (reviewPresent && reviewFresh);
        OffsetDateTime dataUpdatedAt = parseReviewTimestamp(dataUpdatedAtRaw);
        boolean dataUpdatedInvalid = StringUtils.hasText(normalizeNullString(dataUpdatedAtRaw)) && dataUpdatedAt == null;
        boolean dataUpdatedPresent = dataUpdatedAt != null;
        boolean dataFresh = false;
        long dataAgeHours = -1;
        if (dataUpdatedAt != null) {
            dataAgeHours = Math.max(0, java.time.Duration.between(dataUpdatedAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
            dataFresh = dataAgeHours <= dataFreshnessTtlHours;
        }
        boolean dataFreshnessReady = !dataFreshnessRequired || (dataUpdatedPresent && dataFresh);
        String omnichannelDashboardUrl = normalizeNullString(String.valueOf(resolveDialogConfigValue("cross_product_omnichannel_dashboard_url")));
        String financeDashboardUrl = normalizeNullString(String.valueOf(resolveDialogConfigValue("cross_product_finance_dashboard_url")));
        boolean dashboardLinksPresent = StringUtils.hasText(omnichannelDashboardUrl) && StringUtils.hasText(financeDashboardUrl);
        boolean dashboardLinksReady = !dashboardLinksRequired || dashboardLinksPresent;
        boolean dashboardStatusReady = !dashboardStatusRequired || "healthy".equals(dashboardStatus);
        boolean datamartRunbookUrlPresent = StringUtils.hasText(datamartRunbookUrl);
        boolean datamartRunbookUrlValid = !datamartRunbookUrlPresent || isValidExternalReferenceUrl(datamartRunbookUrl);
        boolean ownerRunbookPresent = StringUtils.hasText(datamartOwner) && datamartRunbookUrlPresent;
        boolean ownerRunbookReady = !ownerRunbookRequired || (ownerRunbookPresent && datamartRunbookUrlValid);
        boolean datamartHealthy = "healthy".equals(datamartHealthStatus);
        boolean datamartHealthReady = !datamartHealthRequired || datamartHealthy;
        OffsetDateTime datamartHealthUpdatedAt = parseReviewTimestamp(datamartHealthUpdatedAtRaw);
        boolean datamartHealthUpdatedInvalid = StringUtils.hasText(normalizeNullString(datamartHealthUpdatedAtRaw))
                && datamartHealthUpdatedAt == null;
        boolean datamartHealthUpdatedPresent = datamartHealthUpdatedAt != null;
        boolean datamartHealthFresh = false;
        long datamartHealthAgeHours = -1;
        if (datamartHealthUpdatedAt != null) {
            datamartHealthAgeHours = Math.max(0, java.time.Duration.between(datamartHealthUpdatedAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
            datamartHealthFresh = datamartHealthAgeHours <= datamartHealthTtlHours;
        }
        boolean datamartHealthFreshnessReady = !datamartHealthFreshnessRequired || (datamartHealthUpdatedPresent && datamartHealthFresh);
        boolean datamartProgramBlockerUrlPresent = StringUtils.hasText(datamartProgramBlockerUrl);
        boolean datamartProgramBlockerUrlValid = !datamartProgramBlockerUrlPresent || isValidExternalReferenceUrl(datamartProgramBlockerUrl);
        boolean datamartProgramBlocked = "blocked".equals(datamartProgramStatus);
        boolean datamartProgramBlockerReady = !datamartProgramBlocked
                || (datamartProgramBlockerUrlPresent && datamartProgramBlockerUrlValid);
        boolean datamartProgramReady = !datamartProgramBlockerRequired
                || (!datamartProgramBlocked && datamartProgramBlockerReady);
        OffsetDateTime datamartProgramUpdatedAt = parseReviewTimestamp(datamartProgramUpdatedAtRaw);
        boolean datamartProgramUpdatedInvalid = StringUtils.hasText(normalizeNullString(datamartProgramUpdatedAtRaw))
                && datamartProgramUpdatedAt == null;
        boolean datamartProgramUpdatedPresent = datamartProgramUpdatedAt != null;
        boolean datamartProgramFresh = false;
        long datamartProgramAgeHours = -1;
        if (datamartProgramUpdatedAt != null) {
            datamartProgramAgeHours = Math.max(0, java.time.Duration.between(datamartProgramUpdatedAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
            datamartProgramFresh = datamartProgramAgeHours <= datamartProgramTtlHours;
        }
        boolean datamartProgramFreshnessReady = !datamartProgramFreshnessRequired || (datamartProgramUpdatedPresent && datamartProgramFresh);
        OffsetDateTime datamartTargetReadyAt = parseReviewTimestamp(datamartTargetReadyAtRaw);
        boolean datamartTargetReadyInvalid = StringUtils.hasText(normalizeNullString(datamartTargetReadyAtRaw))
                && datamartTargetReadyAt == null;
        boolean datamartTargetPresent = datamartTargetReadyAt != null;
        boolean datamartTargetOverdue = false;
        long datamartTimelineHoursToTarget = Long.MIN_VALUE;
        if (datamartTargetReadyAt != null) {
            OffsetDateTime overdueThreshold = datamartTargetReadyAt.plusHours(datamartTimelineGraceHours);
            datamartTargetOverdue = OffsetDateTime.now(ZoneOffset.UTC).isAfter(overdueThreshold)
                    && !"ready".equals(datamartProgramStatus);
            datamartTimelineHoursToTarget = java.time.Duration.between(OffsetDateTime.now(ZoneOffset.UTC), overdueThreshold).toHours();
        }
        boolean datamartTimelineReady = !datamartTimelineRequired
                || "ready".equals(datamartProgramStatus)
                || (datamartTargetPresent && !datamartTargetOverdue);
        boolean datamartDependencyTicketPresent = StringUtils.hasText(datamartDependencyTicketUrl);
        boolean datamartDependencyTicketValid = !datamartDependencyTicketPresent || isValidExternalReferenceUrl(datamartDependencyTicketUrl);
        OffsetDateTime datamartDependencyTicketUpdatedAt = parseReviewTimestamp(datamartDependencyTicketUpdatedAtRaw);
        boolean datamartDependencyTicketUpdatedInvalid = StringUtils.hasText(normalizeNullString(datamartDependencyTicketUpdatedAtRaw))
                && datamartDependencyTicketUpdatedAt == null;
        boolean datamartDependencyTicketUpdatedPresent = datamartDependencyTicketUpdatedAt != null;
        boolean datamartDependencyTicketFresh = false;
        long datamartDependencyTicketAgeHours = -1;
        if (datamartDependencyTicketUpdatedAt != null) {
            datamartDependencyTicketAgeHours = Math.max(0, java.time.Duration.between(datamartDependencyTicketUpdatedAt, OffsetDateTime.now(ZoneOffset.UTC)).toHours());
            datamartDependencyTicketFresh = datamartDependencyTicketAgeHours <= datamartDependencyTicketTtlHours;
        }
        boolean datamartDependencyTicketFreshnessReady = !datamartDependencyTicketFreshnessRequired
                || (datamartDependencyTicketUpdatedPresent && datamartDependencyTicketFresh);
        boolean datamartDependencyTicketReady = !datamartDependencyTicketRequired
                || (datamartDependencyTicketPresent && datamartDependencyTicketValid);
        boolean datamartDependencyTicketOwnerPresent = StringUtils.hasText(datamartDependencyTicketOwner);
        boolean datamartDependencyTicketOwnerReady = !datamartDependencyTicketOwnerRequired || datamartDependencyTicketOwnerPresent;
        boolean datamartDependencyTicketOwnerContactPresent = StringUtils.hasText(datamartDependencyTicketOwnerContact);
        boolean datamartDependencyTicketOwnerContactReady = !datamartDependencyTicketOwnerContactRequired || datamartDependencyTicketOwnerContactPresent;
        boolean datamartDependencyTicketOwnerContactActionable = isValidOwnerContact(datamartDependencyTicketOwnerContact);
        boolean datamartDependencyTicketOwnerContactActionableReady = !datamartDependencyTicketOwnerContactActionableRequired
                || datamartDependencyTicketOwnerContactActionable;
        Set<String> datamartContractMissingMandatoryFields = datamartContractMandatoryFields.stream()
                .filter(field -> !datamartContractAvailableFields.contains(field))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<String> datamartContractMissingOptionalFields = datamartContractOptionalFields.stream()
                .filter(field -> !datamartContractAvailableFields.contains(field))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<String> datamartContractOverlappingFields = datamartContractMandatoryFields.stream()
                .filter(datamartContractOptionalFields::contains)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Set<String> datamartContractFields = new java.util.LinkedHashSet<>(datamartContractMandatoryFields);
        datamartContractFields.addAll(datamartContractOptionalFields);
        Set<String> datamartContractAvailableOutsideFields = datamartContractAvailableFields.stream()
                .filter(field -> !datamartContractFields.contains(field))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        boolean datamartContractConfigurationConflict = !datamartContractOverlappingFields.isEmpty();
        int datamartContractMandatoryCoveragePct = calculateContractCoveragePercent(
                datamartContractMandatoryFields,
                datamartContractMissingMandatoryFields);
        int datamartContractOptionalCoveragePct = calculateContractCoveragePercent(
                datamartContractOptionalFields,
                datamartContractMissingOptionalFields);
        int datamartContractBlockingGapCount = datamartContractMissingMandatoryFields.size();
        int datamartContractNonBlockingGapCount = datamartContractMissingOptionalFields.size();
        String datamartContractGapSeverity = resolveDatamartContractGapSeverity(
                datamartContractBlockingGapCount,
                datamartContractNonBlockingGapCount);
        boolean datamartContractOptionalCoverageGateActive = datamartContractRequired
                && datamartContractOptionalCoverageRequired;
        boolean datamartContractOptionalCoverageReady = !datamartContractOptionalCoverageGateActive
                || datamartContractOptionalCoveragePct >= datamartContractOptionalMinCoveragePct;
        boolean datamartContractReady = (!datamartContractRequired || datamartContractMissingMandatoryFields.isEmpty())
                && datamartContractOptionalCoverageReady
                && !datamartContractConfigurationConflict;
        boolean readyForDecision = !gateEnabled || (omnichannelReady
                && financeReady
                && reviewReady
                && dataFreshnessReady
                && dashboardLinksReady
                && dashboardStatusReady
                && ownerRunbookReady
                && datamartHealthReady
                && datamartHealthFreshnessReady
                && datamartProgramReady
                && datamartProgramFreshnessReady
                && datamartTimelineReady
                && datamartDependencyTicketReady
                && datamartDependencyTicketOwnerReady
                && datamartDependencyTicketOwnerContactReady
                && datamartDependencyTicketOwnerContactActionableReady
                && datamartDependencyTicketFreshnessReady
                && datamartContractReady);

        java.util.List<String> datamartRiskReasons = new java.util.ArrayList<>();
        if (!ownerRunbookReady) {
            datamartRiskReasons.add("owner_runbook_missing_or_invalid");
        }
        if (!datamartHealthReady) {
            datamartRiskReasons.add("datamart_health_unhealthy");
        }
        if (!datamartHealthFreshnessReady) {
            datamartRiskReasons.add("datamart_health_stale");
        }
        if (!datamartProgramReady) {
            datamartRiskReasons.add("datamart_program_blocked");
        }
        if (!datamartProgramFreshnessReady) {
            datamartRiskReasons.add("datamart_program_status_stale");
        }
        if (!datamartTimelineReady) {
            datamartRiskReasons.add("datamart_timeline_overdue");
        }
        if (!datamartDependencyTicketReady) {
            datamartRiskReasons.add("dependency_ticket_missing_or_invalid");
        }
        if (!datamartDependencyTicketFreshnessReady) {
            datamartRiskReasons.add("dependency_ticket_stale");
        }
        if (reviewTimestampInvalid) {
            datamartRiskReasons.add("review_timestamp_invalid");
        }
        if (dataUpdatedInvalid) {
            datamartRiskReasons.add("data_updated_timestamp_invalid");
        }
        if (datamartHealthUpdatedInvalid) {
            datamartRiskReasons.add("datamart_health_timestamp_invalid");
        }
        if (datamartProgramUpdatedInvalid) {
            datamartRiskReasons.add("datamart_program_timestamp_invalid");
        }
        if (datamartTargetReadyInvalid) {
            datamartRiskReasons.add("datamart_target_timestamp_invalid");
        }
        if (datamartDependencyTicketUpdatedInvalid) {
            datamartRiskReasons.add("dependency_ticket_timestamp_invalid");
        }
        if (!datamartDependencyTicketOwnerReady) {
            datamartRiskReasons.add("dependency_ticket_owner_missing");
        }
        if (!datamartDependencyTicketOwnerContactReady) {
            datamartRiskReasons.add("dependency_ticket_owner_contact_missing");
        }
        if (!datamartDependencyTicketOwnerContactActionableReady) {
            datamartRiskReasons.add("dependency_ticket_owner_contact_not_actionable");
        }
        if (!datamartContractMissingMandatoryFields.isEmpty()) {
            datamartRiskReasons.add("datamart_contract_missing_mandatory_fields");
        }
        if (datamartContractConfigurationConflict) {
            datamartRiskReasons.add("datamart_contract_configuration_conflict");
        }
        if (datamartContractOptionalCoverageGateActive && !datamartContractOptionalCoverageReady) {
            datamartRiskReasons.add("datamart_contract_optional_coverage_below_threshold");
        }

        String datamartRiskLevel = "low";
        if (!datamartRiskReasons.isEmpty()) {
            datamartRiskLevel = datamartRiskReasons.size() >= 3 ? "high" : "medium";
        }
        if (datamartProgramBlocked || datamartTargetOverdue) {
            datamartRiskLevel = "high";
        }
        signal.put("enabled", gateEnabled);
        signal.put("omnichannel_ready", omnichannelReady);
        signal.put("finance_ready", financeReady);
        signal.put("datamart_owner", normalizeNullString(datamartOwner));
        signal.put("datamart_runbook_url", normalizeNullString(datamartRunbookUrl));
        signal.put("datamart_dependency_ticket_required", datamartDependencyTicketRequired);
        signal.put("datamart_dependency_ticket_url", datamartDependencyTicketUrl);
        signal.put("datamart_dependency_ticket_owner_required", datamartDependencyTicketOwnerRequired);
        signal.put("datamart_dependency_ticket_owner", datamartDependencyTicketOwner);
        signal.put("datamart_dependency_ticket_owner_present", datamartDependencyTicketOwnerPresent);
        signal.put("datamart_dependency_ticket_owner_ready", datamartDependencyTicketOwnerReady);
        signal.put("datamart_dependency_ticket_owner_contact_required", datamartDependencyTicketOwnerContactRequired);
        signal.put("datamart_dependency_ticket_owner_contact", datamartDependencyTicketOwnerContact);
        signal.put("datamart_dependency_ticket_owner_contact_present", datamartDependencyTicketOwnerContactPresent);
        signal.put("datamart_dependency_ticket_owner_contact_ready", datamartDependencyTicketOwnerContactReady);
        signal.put("datamart_dependency_ticket_owner_contact_actionable_required", datamartDependencyTicketOwnerContactActionableRequired);
        signal.put("datamart_dependency_ticket_owner_contact_actionable", datamartDependencyTicketOwnerContactActionable);
        signal.put("datamart_dependency_ticket_owner_contact_actionable_ready", datamartDependencyTicketOwnerContactActionableReady);
        signal.put("datamart_contract_required", datamartContractRequired);
        signal.put("datamart_contract_version", StringUtils.hasText(datamartContractVersion) ? datamartContractVersion : DEFAULT_EXTERNAL_KPI_CONTRACT_VERSION);
        signal.put("datamart_contract_mandatory_fields", new ArrayList<>(datamartContractMandatoryFields));
        signal.put("datamart_contract_optional_fields", new ArrayList<>(datamartContractOptionalFields));
        signal.put("datamart_contract_available_fields", new ArrayList<>(datamartContractAvailableFields));
        signal.put("datamart_contract_missing_mandatory_fields", new ArrayList<>(datamartContractMissingMandatoryFields));
        signal.put("datamart_contract_missing_optional_fields", new ArrayList<>(datamartContractMissingOptionalFields));
        signal.put("datamart_contract_overlapping_fields", new ArrayList<>(datamartContractOverlappingFields));
        signal.put("datamart_contract_available_outside_fields", new ArrayList<>(datamartContractAvailableOutsideFields));
        signal.put("datamart_contract_configuration_conflict", datamartContractConfigurationConflict);
        signal.put("datamart_contract_mandatory_coverage_pct", datamartContractMandatoryCoveragePct);
        signal.put("datamart_contract_optional_coverage_pct", datamartContractOptionalCoveragePct);
        signal.put("datamart_contract_blocking_gap_count", datamartContractBlockingGapCount);
        signal.put("datamart_contract_non_blocking_gap_count", datamartContractNonBlockingGapCount);
        signal.put("datamart_contract_gap_severity", datamartContractGapSeverity);
        signal.put("datamart_contract_optional_coverage_required", datamartContractOptionalCoverageRequired);
        signal.put("datamart_contract_optional_coverage_gate_active", datamartContractOptionalCoverageGateActive);
        signal.put("datamart_contract_optional_min_coverage_pct", datamartContractOptionalMinCoveragePct);
        signal.put("datamart_contract_optional_coverage_ready", datamartContractOptionalCoverageReady);
        signal.put("datamart_contract_ready", datamartContractReady);
        signal.put("datamart_dependency_ticket_present", datamartDependencyTicketPresent);
        signal.put("datamart_dependency_ticket_valid", datamartDependencyTicketValid);
        signal.put("datamart_dependency_ticket_ready", datamartDependencyTicketReady);
        signal.put("datamart_dependency_ticket_freshness_required", datamartDependencyTicketFreshnessRequired);
        signal.put("datamart_dependency_ticket_updated_at", datamartDependencyTicketUpdatedAt != null ? datamartDependencyTicketUpdatedAt.toString() : "");
        signal.put("datamart_dependency_ticket_ttl_hours", datamartDependencyTicketTtlHours);
        signal.put("datamart_dependency_ticket_updated_present", datamartDependencyTicketUpdatedPresent);
        signal.put("datamart_dependency_ticket_updated_timestamp_invalid", datamartDependencyTicketUpdatedInvalid);
        signal.put("dependency_ticket_timestamp_invalid", datamartDependencyTicketUpdatedInvalid);
        signal.put("datamart_dependency_ticket_fresh", datamartDependencyTicketFresh);
        signal.put("datamart_dependency_ticket_age_hours", datamartDependencyTicketAgeHours);
        signal.put("datamart_dependency_ticket_freshness_ready", datamartDependencyTicketFreshnessReady);
        signal.put("reviewed_by", normalizeNullString(reviewedBy));
        signal.put("reviewed_at", reviewedAt != null ? reviewedAt.toString() : "");
        signal.put("review_ttl_hours", reviewTtlHours);
        signal.put("review_present", reviewPresent);
        signal.put("review_timestamp_invalid", reviewTimestampInvalid);
        signal.put("review_fresh", reviewFresh);
        signal.put("review_age_hours", reviewAgeHours);
        signal.put("data_freshness_required", dataFreshnessRequired);
        signal.put("data_updated_at", dataUpdatedAt != null ? dataUpdatedAt.toString() : "");
        signal.put("data_freshness_ttl_hours", dataFreshnessTtlHours);
        signal.put("data_updated_present", dataUpdatedPresent);
        signal.put("data_updated_timestamp_invalid", dataUpdatedInvalid);
        signal.put("data_fresh", dataFresh);
        signal.put("data_age_hours", dataAgeHours);
        signal.put("dashboard_links_required", dashboardLinksRequired);
        signal.put("dashboard_links_present", dashboardLinksPresent);
        signal.put("dashboard_links_ready", dashboardLinksReady);
        signal.put("dashboard_status_required", dashboardStatusRequired);
        signal.put("dashboard_status", dashboardStatus);
        signal.put("dashboard_status_note", dashboardStatusNote);
        signal.put("dashboard_status_ready", dashboardStatusReady);
        signal.put("owner_runbook_required", ownerRunbookRequired);
        signal.put("owner_runbook_present", ownerRunbookPresent);
        signal.put("datamart_runbook_url_present", datamartRunbookUrlPresent);
        signal.put("datamart_runbook_url_valid", datamartRunbookUrlValid);
        signal.put("owner_runbook_ready", ownerRunbookReady);
        signal.put("datamart_health_required", datamartHealthRequired);
        signal.put("datamart_health_status", datamartHealthStatus);
        signal.put("datamart_health_note", datamartHealthNote);
        signal.put("datamart_health_ready", datamartHealthReady);
        signal.put("datamart_health_freshness_required", datamartHealthFreshnessRequired);
        signal.put("datamart_health_updated_at", datamartHealthUpdatedAt != null ? datamartHealthUpdatedAt.toString() : "");
        signal.put("datamart_health_ttl_hours", datamartHealthTtlHours);
        signal.put("datamart_health_updated_present", datamartHealthUpdatedPresent);
        signal.put("datamart_health_updated_timestamp_invalid", datamartHealthUpdatedInvalid);
        signal.put("datamart_health_timestamp_invalid", datamartHealthUpdatedInvalid);
        signal.put("datamart_health_fresh", datamartHealthFresh);
        signal.put("datamart_health_age_hours", datamartHealthAgeHours);
        signal.put("datamart_health_freshness_ready", datamartHealthFreshnessReady);
        signal.put("datamart_program_blocker_required", datamartProgramBlockerRequired);
        signal.put("datamart_program_status", datamartProgramStatus);
        signal.put("datamart_program_note", datamartProgramNote);
        signal.put("datamart_program_blocker_url", datamartProgramBlockerUrl);
        signal.put("datamart_program_blocked", datamartProgramBlocked);
        signal.put("datamart_program_blocker_url_present", datamartProgramBlockerUrlPresent);
        signal.put("datamart_program_blocker_url_valid", datamartProgramBlockerUrlValid);
        signal.put("datamart_program_blocker_ready", datamartProgramBlockerReady);
        signal.put("datamart_program_ready", datamartProgramReady);
        signal.put("datamart_program_freshness_required", datamartProgramFreshnessRequired);
        signal.put("datamart_program_updated_at", datamartProgramUpdatedAt != null ? datamartProgramUpdatedAt.toString() : "");
        signal.put("datamart_program_ttl_hours", datamartProgramTtlHours);
        signal.put("datamart_program_updated_present", datamartProgramUpdatedPresent);
        signal.put("datamart_program_updated_timestamp_invalid", datamartProgramUpdatedInvalid);
        signal.put("datamart_program_timestamp_invalid", datamartProgramUpdatedInvalid);
        signal.put("datamart_program_fresh", datamartProgramFresh);
        signal.put("datamart_program_age_hours", datamartProgramAgeHours);
        signal.put("datamart_program_freshness_ready", datamartProgramFreshnessReady);
        signal.put("datamart_timeline_required", datamartTimelineRequired);
        signal.put("datamart_target_ready_at", datamartTargetReadyAt != null ? datamartTargetReadyAt.toString() : "");
        signal.put("datamart_timeline_grace_hours", datamartTimelineGraceHours);
        signal.put("datamart_target_present", datamartTargetPresent);
        signal.put("datamart_target_timestamp_invalid", datamartTargetReadyInvalid);
        signal.put("datamart_target_overdue", datamartTargetOverdue);
        signal.put("datamart_timeline_hours_to_target", datamartTimelineHoursToTarget);
        signal.put("datamart_timeline_ready", datamartTimelineReady);
        signal.put("datamart_risk_level", datamartRiskLevel);
        signal.put("datamart_risk_reasons", datamartRiskReasons);
        signal.put("ready_for_decision", readyForDecision);
        signal.put("note", note != null ? note.trim() : "");
        return signal;
    }


    private int calculateContractCoveragePercent(Set<String> contractFields, Set<String> missingFields) {
        if (contractFields == null || contractFields.isEmpty()) {
            return 100;
        }
        int covered = Math.max(0, contractFields.size() - (missingFields == null ? 0 : missingFields.size()));
        return (int) Math.round((covered * 100.0d) / contractFields.size());
    }

    private String resolveDatamartContractGapSeverity(int blockingGapCount, int nonBlockingGapCount) {
        if (blockingGapCount > 0 && nonBlockingGapCount > 0) {
            return "mixed";
        }
        if (blockingGapCount > 0) {
            return "blocking";
        }
        if (nonBlockingGapCount > 0) {
            return "non_blocking";
        }
        return "none";
    }

    private boolean isValidExternalReferenceUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return false;
        }
        try {
            URI parsed = URI.create(rawUrl.trim());
            String scheme = parsed.getScheme();
            if (!StringUtils.hasText(scheme)) {
                return false;
            }
            String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                return false;
            }
            return StringUtils.hasText(parsed.getHost());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isValidOwnerContact(String rawContact) {
        String contact = normalizeNullString(rawContact);
        if (!StringUtils.hasText(contact)) {
            return false;
        }
        if (contact.startsWith("@") && contact.length() > 1) {
            return true;
        }
        if (contact.startsWith("mailto:")) {
            return contact.length() > "mailto:".length() && contact.substring("mailto:".length()).contains("@");
        }
        if (contact.startsWith("slack://")) {
            return contact.length() > "slack://".length();
        }
        if (isValidExternalReferenceUrl(contact)) {
            return true;
        }
        int atIndex = contact.indexOf('@');
        return atIndex > 0 && atIndex < contact.length() - 1;
    }

    private String normalizeDatamartProgramStatus(String rawValue) {
        String normalized = normalizeNullString(rawValue).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "ready", "in_progress", "blocked" -> normalized;
            default -> "unknown";
        };
    }

    private String normalizeDatamartHealthStatus(String rawValue) {
        String normalized = normalizeNullString(rawValue).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "healthy", "degraded", "down" -> normalized;
            default -> "unknown";
        };
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
        String normalized = String.valueOf(value).trim().toLowerCase();
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

    private Map<String, Object> buildWorkspaceCohortComparison(List<Map<String, Object>> rows,
                                                               Map<String, Object> telemetryConfig) {
        List<Map<String, Object>> safeRows = rows == null ? List.of() : rows;
        List<Map<String, Object>> controlRows = safeRows.stream()
                .filter(row -> "control".equalsIgnoreCase(String.valueOf(row.get("experiment_cohort"))))
                .toList();
        List<Map<String, Object>> testRows = safeRows.stream()
                .filter(row -> "test".equalsIgnoreCase(String.valueOf(row.get("experiment_cohort"))))
                .toList();

        Map<String, Object> controlTotals = computeWorkspaceTelemetryTotals(controlRows);
        Map<String, Object> testTotals = computeWorkspaceTelemetryTotals(testRows);

        long controlEvents = toLong(controlTotals.get("events"));
        long testEvents = toLong(testTotals.get("events"));

        double controlRenderRate = safeRate(toLong(controlTotals.get("render_errors")), controlEvents);
        double testRenderRate = safeRate(toLong(testTotals.get("render_errors")), testEvents);
        double controlFallbackRate = safeRate(toLong(controlTotals.get("fallbacks")), controlEvents);
        double testFallbackRate = safeRate(toLong(testTotals.get("fallbacks")), testEvents);
        double controlAbandonRate = safeRate(toLong(controlTotals.get("abandons")), controlEvents);
        double testAbandonRate = safeRate(toLong(testTotals.get("abandons")), testEvents);
        double controlSlowOpenRate = safeRate(toLong(controlTotals.get("slow_open_events")), controlEvents);
        double testSlowOpenRate = safeRate(toLong(testTotals.get("slow_open_events")), testEvents);

        Long controlAvgOpen = extractNullableLong(controlTotals.get("avg_open_ms"));
        Long testAvgOpen = extractNullableLong(testTotals.get("avg_open_ms"));
        double minOpenImprovementPercent = resolveWinnerMinOpenImprovementPercent();

        long minCohortEvents = resolveLongConfig(telemetryConfig, "cohort_min_events", DEFAULT_COHORT_MIN_EVENTS, 5, 1000);
        boolean enoughData = controlEvents >= minCohortEvents && testEvents >= minCohortEvents;
        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("control", controlTotals);
        comparison.put("test", testTotals);
        comparison.put("sample_size_ok", enoughData);
        comparison.put("sample_size_min_events", minCohortEvents);
        comparison.put("control_events", controlEvents);
        comparison.put("test_events", testEvents);
        comparison.put("render_error_rate_delta", testRenderRate - controlRenderRate);
        comparison.put("fallback_rate_delta", testFallbackRate - controlFallbackRate);
        comparison.put("abandon_rate_delta", testAbandonRate - controlAbandonRate);
        comparison.put("slow_open_rate_delta", testSlowOpenRate - controlSlowOpenRate);
        comparison.put("avg_open_ms_delta", (testAvgOpen != null && controlAvgOpen != null)
                ? testAvgOpen - controlAvgOpen
                : null);
        comparison.put("winner_min_open_improvement_percent", minOpenImprovementPercent * 100d);
        comparison.put("kpi_signal", buildPrimaryKpiSignal(controlTotals, testTotals, controlEvents, testEvents));
        comparison.put("kpi_outcome_signal", buildPrimaryKpiOutcomeSignal(controlTotals, testTotals));
        comparison.put("winner", resolveWorkspaceCohortWinner(
                enoughData,
                testAvgOpen,
                controlAvgOpen,
                testRenderRate,
                controlRenderRate,
                testFallbackRate,
                controlFallbackRate,
                testAbandonRate,
                controlAbandonRate,
                testSlowOpenRate,
                controlSlowOpenRate,
                minOpenImprovementPercent));
        return comparison;
    }

    private String resolveWorkspaceCohortWinner(boolean enoughData,
                                                Long testAvgOpen,
                                                Long controlAvgOpen,
                                                double testRenderRate,
                                                double controlRenderRate,
                                                double testFallbackRate,
                                                double controlFallbackRate,
                                                double testAbandonRate,
                                                double controlAbandonRate,
                                                double testSlowOpenRate,
                                                double controlSlowOpenRate,
                                                double minOpenImprovementPercent) {
        if (!enoughData || testAvgOpen == null || controlAvgOpen == null) {
            return "insufficient_data";
        }
        boolean technicalRegressions = testRenderRate > controlRenderRate
                || testFallbackRate > controlFallbackRate
                || testAbandonRate > controlAbandonRate
                || testSlowOpenRate > controlSlowOpenRate;
        if (technicalRegressions) {
            return "control";
        }
        double relativeImprovement = controlAvgOpen > 0
                ? (double) (controlAvgOpen - testAvgOpen) / controlAvgOpen
                : 0d;
        return relativeImprovement >= minOpenImprovementPercent ? "test" : "control";
    }

    private Map<String, Object> computeWorkspaceTelemetryTotals(List<Map<String, Object>> rows) {
        Map<String, Object> totals = new LinkedHashMap<>();
        long events = rows.stream().mapToLong(row -> toLong(row.get("events"))).sum();
        long renderErrors = rows.stream().mapToLong(row -> toLong(row.get("render_errors"))).sum();
        long fallbacks = rows.stream().mapToLong(row -> toLong(row.get("fallbacks"))).sum();
        long abandons = rows.stream().mapToLong(row -> toLong(row.get("abandons"))).sum();
        long slowOpenEvents = rows.stream().mapToLong(row -> toLong(row.get("slow_open_events"))).sum();
        long frtKpiEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_frt_events"))).sum();
        long ttrKpiEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_ttr_events"))).sum();
        long slaBreachKpiEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_sla_breach_events"))).sum();
        long dialogsPerShiftKpiEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_dialogs_per_shift_events"))).sum();
        long csatKpiEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_csat_events"))).sum();
        long workspaceOpenEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_open_events"))).sum();
        long contextProfileGapEvents = rows.stream().mapToLong(row -> toLong(row.get("context_profile_gap_events"))).sum();
        long contextSourceGapEvents = rows.stream().mapToLong(row -> toLong(row.get("context_source_gap_events"))).sum();
        long contextBlockGapEvents = rows.stream().mapToLong(row -> toLong(row.get("context_block_gap_events"))).sum();
        long workspaceParityGapEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_parity_gap_events"))).sum();
        long workspaceInlineNavigationEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_inline_navigation_events"))).sum();
        long manualLegacyOpenEvents = rows.stream().mapToLong(row -> toLong(row.get("manual_legacy_open_events"))).sum();
        long frtRecordedEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_frt_recorded_events"))).sum();
        long ttrRecordedEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_ttr_recorded_events"))).sum();
        long slaBreachRecordedEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_sla_breach_recorded_events"))).sum();
        long dialogsPerShiftRecordedEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_dialogs_per_shift_recorded_events"))).sum();
        long csatRecordedEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_csat_recorded_events"))).sum();

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
        Long avgFrtMs = weightedAverage(rows, "kpi_frt_recorded_events", "avg_frt_ms");
        Long avgTtrMs = weightedAverage(rows, "kpi_ttr_recorded_events", "avg_ttr_ms");

        totals.put("events", events);
        totals.put("render_errors", renderErrors);
        totals.put("fallbacks", fallbacks);
        totals.put("abandons", abandons);
        totals.put("slow_open_events", slowOpenEvents);
        totals.put("kpi_frt_events", frtKpiEvents);
        totals.put("kpi_ttr_events", ttrKpiEvents);
        totals.put("kpi_sla_breach_events", slaBreachKpiEvents);
        totals.put("kpi_dialogs_per_shift_events", dialogsPerShiftKpiEvents);
        totals.put("kpi_csat_events", csatKpiEvents);
        totals.put("workspace_open_events", workspaceOpenEvents);
        totals.put("context_profile_gap_events", contextProfileGapEvents);
        totals.put("context_source_gap_events", contextSourceGapEvents);
        totals.put("context_block_gap_events", contextBlockGapEvents);
        totals.put("workspace_parity_gap_events", workspaceParityGapEvents);
        totals.put("workspace_inline_navigation_events", workspaceInlineNavigationEvents);
        totals.put("manual_legacy_open_events", manualLegacyOpenEvents);
        totals.put("context_profile_gap_rate", workspaceOpenEvents > 0 ? (double) contextProfileGapEvents / workspaceOpenEvents : 0d);
        totals.put("context_profile_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) contextProfileGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("context_source_gap_rate", workspaceOpenEvents > 0 ? (double) contextSourceGapEvents / workspaceOpenEvents : 0d);
        totals.put("context_source_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) contextSourceGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("context_block_gap_rate", workspaceOpenEvents > 0 ? (double) contextBlockGapEvents / workspaceOpenEvents : 0d);
        totals.put("context_block_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) contextBlockGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("workspace_parity_gap_rate", workspaceOpenEvents > 0 ? (double) workspaceParityGapEvents / workspaceOpenEvents : 0d);
        totals.put("workspace_parity_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) workspaceParityGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("kpi_frt_recorded_events", frtRecordedEvents);
        totals.put("kpi_ttr_recorded_events", ttrRecordedEvents);
        totals.put("kpi_sla_breach_recorded_events", slaBreachRecordedEvents);
        totals.put("kpi_dialogs_per_shift_recorded_events", dialogsPerShiftRecordedEvents);
        totals.put("kpi_csat_recorded_events", csatRecordedEvents);
        totals.put("avg_frt_ms", avgFrtMs);
        totals.put("avg_ttr_ms", avgTtrMs);
        totals.put("avg_open_ms", avgOpenMs);
        return totals;
    }

    private Long weightedAverage(List<Map<String, Object>> rows, String weightKey, String avgKey) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        long weightSum = 0L;
        long weightedValueSum = 0L;
        for (Map<String, Object> row : rows) {
            long weight = toLong(row.get(weightKey));
            Long avg = extractNullableLong(row.get(avgKey));
            if (weight <= 0 || avg == null) {
                continue;
            }
            weightSum += weight;
            weightedValueSum += avg * weight;
        }
        return weightSum > 0 ? Math.round((double) weightedValueSum / weightSum) : null;
    }

    private Map<String, Object> buildPrimaryKpiOutcomeSignal(Map<String, Object> controlTotals,
                                                             Map<String, Object> testTotals) {
        Map<String, Object> signal = new LinkedHashMap<>();
        Map<String, Object> metrics = new LinkedHashMap<>();
        long minSamplesPerCohort = resolveOutcomeMinSamplesPerCohort();
        double frtRelativeRegression = resolveOutcomeRelativeRegressionThreshold(
                "workspace_rollout_kpi_outcome_frt_max_relative_regression",
                DEFAULT_KPI_OUTCOME_FRT_MAX_RELATIVE_REGRESSION);
        double ttrRelativeRegression = resolveOutcomeRelativeRegressionThreshold(
                "workspace_rollout_kpi_outcome_ttr_max_relative_regression",
                DEFAULT_KPI_OUTCOME_TTR_MAX_RELATIVE_REGRESSION);
        double slaBreachAbsoluteDelta = resolveOutcomeRateAbsoluteDeltaThreshold();
        double slaBreachRelativeMultiplier = resolveOutcomeRateRelativeMultiplierThreshold();

        Map<String, Object> frtMetric = buildLatencyMetricSignal(
                "frt",
                extractNullableLong(controlTotals.get("avg_frt_ms")),
                extractNullableLong(testTotals.get("avg_frt_ms")),
                toLong(controlTotals.get("kpi_frt_recorded_events")),
                toLong(testTotals.get("kpi_frt_recorded_events")),
                minSamplesPerCohort,
                frtRelativeRegression);
        Map<String, Object> ttrMetric = buildLatencyMetricSignal(
                "ttr",
                extractNullableLong(controlTotals.get("avg_ttr_ms")),
                extractNullableLong(testTotals.get("avg_ttr_ms")),
                toLong(controlTotals.get("kpi_ttr_recorded_events")),
                toLong(testTotals.get("kpi_ttr_recorded_events")),
                minSamplesPerCohort,
                ttrRelativeRegression);
        Map<String, Object> slaBreachMetric = buildRateMetricSignal(
                "sla_breach",
                toLong(controlTotals.get("kpi_sla_breach_recorded_events")),
                toLong(testTotals.get("kpi_sla_breach_recorded_events")),
                minSamplesPerCohort,
                slaBreachAbsoluteDelta,
                slaBreachRelativeMultiplier);

        metrics.put("frt", frtMetric);
        metrics.put("ttr", ttrMetric);
        metrics.put("sla_breach", slaBreachMetric);

        Set<String> requiredOutcomeKpis = resolveRequiredOutcomeKpis();
        List<Map<String, Object>> evaluatedMetrics = metrics.entrySet().stream()
                .filter(entry -> requiredOutcomeKpis.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(this::castObjectMap)
                .toList();
        boolean ready = !evaluatedMetrics.isEmpty()
                && evaluatedMetrics.stream().allMatch(metric -> toBoolean(metric.get("ready")));
        boolean hasRegression = evaluatedMetrics.stream().anyMatch(metric -> toBoolean(metric.get("regression")));

        signal.put("required_kpis", requiredOutcomeKpis);
        signal.put("evaluated_kpis", evaluatedMetrics.stream()
                .map(metric -> String.valueOf(metric.get("key")))
                .toList());
        signal.put("ready_for_decision", ready);
        signal.put("has_regression", hasRegression);
        signal.put("min_samples_per_cohort", minSamplesPerCohort);
        signal.put("thresholds", Map.of(
                "frt_max_relative_regression", frtRelativeRegression,
                "ttr_max_relative_regression", ttrRelativeRegression,
                "sla_breach_max_absolute_delta", slaBreachAbsoluteDelta,
                "sla_breach_max_relative_multiplier", slaBreachRelativeMultiplier
        ));
        signal.put("metrics", metrics);
        return signal;
    }

    private Map<String, Object> buildLatencyMetricSignal(String key,
                                                         Long controlAvgMs,
                                                         Long testAvgMs,
                                                         long controlSamples,
                                                         long testSamples,
                                                         long minSamplesPerCohort,
                                                         double maxRelativeRegression) {
        Map<String, Object> metric = new LinkedHashMap<>();
        boolean ready = controlSamples >= minSamplesPerCohort
                && testSamples >= minSamplesPerCohort
                && controlAvgMs != null
                && testAvgMs != null;
        Long deltaMs = ready ? testAvgMs - controlAvgMs : null;
        double relativeDelta = ready && controlAvgMs > 0 ? (double) deltaMs / controlAvgMs : 0d;
        boolean regression = ready && relativeDelta > maxRelativeRegression;
        metric.put("type", "latency_ms");
        metric.put("key", key);
        metric.put("control_value", controlAvgMs);
        metric.put("test_value", testAvgMs);
        metric.put("control_samples", controlSamples);
        metric.put("test_samples", testSamples);
        metric.put("min_samples_per_cohort", minSamplesPerCohort);
        metric.put("delta", deltaMs);
        metric.put("relative_delta", relativeDelta);
        metric.put("max_relative_regression", maxRelativeRegression);
        metric.put("ready", ready);
        metric.put("regression", regression);
        return metric;
    }

    private Map<String, Object> buildRateMetricSignal(String key,
                                                      long controlCount,
                                                      long testCount,
                                                      long minSamplesPerCohort,
                                                      double maxAbsoluteDelta,
                                                      double maxRelativeMultiplier) {
        Map<String, Object> metric = new LinkedHashMap<>();
        boolean ready = controlCount >= minSamplesPerCohort && testCount >= minSamplesPerCohort;
        double delta = testCount - controlCount;
        double multiplier = controlCount > 0 ? (double) testCount / controlCount : (testCount > 0 ? Double.POSITIVE_INFINITY : 1d);
        boolean regression = ready && (delta > maxAbsoluteDelta * Math.max(controlCount, 1) || multiplier > maxRelativeMultiplier);
        metric.put("type", "events_count");
        metric.put("key", key);
        metric.put("control_value", controlCount);
        metric.put("test_value", testCount);
        metric.put("min_samples_per_cohort", minSamplesPerCohort);
        metric.put("delta", delta);
        metric.put("multiplier", multiplier);
        metric.put("max_absolute_delta", maxAbsoluteDelta);
        metric.put("max_relative_multiplier", maxRelativeMultiplier);
        metric.put("ready", ready);
        metric.put("regression", regression);
        return metric;
    }

    private long resolveOutcomeMinSamplesPerCohort() {
        Object value = resolveDialogConfigValue("workspace_rollout_kpi_outcome_min_samples_per_cohort");
        if (value instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        try {
            long parsed = Long.parseLong(String.valueOf(value));
            return parsed > 0 ? parsed : DEFAULT_KPI_OUTCOME_MIN_SAMPLES_PER_COHORT;
        } catch (Exception ignored) {
            return DEFAULT_KPI_OUTCOME_MIN_SAMPLES_PER_COHORT;
        }
    }

    private double resolveOutcomeRelativeRegressionThreshold(String key, double fallback) {
        Object value = resolveDialogConfigValue(key);
        if (value instanceof Number number) {
            double normalized = number.doubleValue();
            if (normalized > 0d && normalized <= 5d) {
                return normalized;
            }
        }
        if (value instanceof String text) {
            try {
                double parsed = Double.parseDouble(text.trim());
                if (parsed > 0d && parsed <= 5d) {
                    return parsed;
                }
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private double resolveOutcomeRateAbsoluteDeltaThreshold() {
        Object value = resolveDialogConfigValue("workspace_rollout_kpi_outcome_sla_breach_max_absolute_delta");
        if (value instanceof Number number) {
            double normalized = number.doubleValue();
            if (normalized > 0d && normalized <= 1d) {
                return normalized;
            }
        }
        if (value instanceof String text) {
            try {
                double parsed = Double.parseDouble(text.trim());
                if (parsed > 0d && parsed <= 1d) {
                    return parsed;
                }
            } catch (Exception ignored) {
                return DEFAULT_KPI_OUTCOME_SLA_BREACH_MAX_ABSOLUTE_DELTA;
            }
        }
        return DEFAULT_KPI_OUTCOME_SLA_BREACH_MAX_ABSOLUTE_DELTA;
    }

    private Set<String> resolveRequiredOutcomeKpis() {
        Object value = resolveDialogConfigValue("workspace_rollout_required_outcome_kpis");
        Set<String> resolved = parseKpiKeySet(value);
        return resolved.isEmpty() ? DEFAULT_REQUIRED_KPI_OUTCOME_KEYS : resolved;
    }

    private Set<String> parseKpiKeySet(Object rawValue) {
        if (rawValue == null) {
            return Set.of();
        }
        List<String> values = new ArrayList<>();
        if (rawValue instanceof List<?> list) {
            for (Object item : list) {
                values.add(String.valueOf(item));
            }
        } else {
            values.addAll(List.of(String.valueOf(rawValue).split(",")));
        }
        return values.stream()
                .map(value -> String.valueOf(value).trim().toLowerCase())
                .filter(StringUtils::hasText)
                .filter(DEFAULT_REQUIRED_KPI_OUTCOME_KEYS::contains)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private Set<String> parseExternalKpiContractFields(Object rawValue, Set<String> fallback) {
        if (rawValue == null) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        if (rawValue instanceof List<?> list) {
            for (Object item : list) {
                values.add(String.valueOf(item));
            }
        } else {
            values.addAll(List.of(String.valueOf(rawValue).split(",")));
        }
        Set<String> normalized = values.stream()
                .map(value -> normalizeNullString(String.valueOf(value)).toLowerCase(Locale.ROOT))
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return normalized.isEmpty() ? fallback : normalized;
    }

    private double resolveWinnerMinOpenImprovementPercent() {
        Object value = resolveDialogConfigValue("workspace_rollout_winner_min_open_improvement");
        if (value instanceof Number number) {
            double normalized = number.doubleValue();
            if (normalized >= 0d && normalized <= 0.5d) {
                return normalized;
            }
        }
        if (value instanceof String text) {
            try {
                double parsed = Double.parseDouble(text.trim());
                if (parsed >= 0d && parsed <= 0.5d) {
                    return parsed;
                }
            } catch (Exception ignored) {
                return DEFAULT_WORKSPACE_WINNER_MIN_OPEN_IMPROVEMENT;
            }
        }
        return DEFAULT_WORKSPACE_WINNER_MIN_OPEN_IMPROVEMENT;
    }

    private double resolveOutcomeRateRelativeMultiplierThreshold() {
        Object value = resolveDialogConfigValue("workspace_rollout_kpi_outcome_sla_breach_max_relative_multiplier");
        if (value instanceof Number number) {
            double normalized = number.doubleValue();
            if (normalized >= 1d && normalized <= 10d) {
                return normalized;
            }
        }
        if (value instanceof String text) {
            try {
                double parsed = Double.parseDouble(text.trim());
                if (parsed >= 1d && parsed <= 10d) {
                    return parsed;
                }
            } catch (Exception ignored) {
                return DEFAULT_KPI_OUTCOME_SLA_BREACH_MAX_RELATIVE_MULTIPLIER;
            }
        }
        return DEFAULT_KPI_OUTCOME_SLA_BREACH_MAX_RELATIVE_MULTIPLIER;
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

    private Map<String, Object> buildPrimaryKpiSignal(Map<String, Object> controlTotals,
                                                      Map<String, Object> testTotals,
                                                      long controlEvents,
                                                      long testEvents) {
        List<String> requiredKpis = resolveRequiredPrimaryKpis();
        long minKpiEvents = resolveMinKpiEventsForDecision();
        double minCoverageRate = resolveMinKpiCoverageRateForDecision();
        Map<String, Object> metrics = new LinkedHashMap<>();
        boolean ready = true;
        for (String kpi : requiredKpis) {
            String key = "kpi_" + kpi + "_events";
            long control = toLong(controlTotals.get(key));
            long test = toLong(testTotals.get(key));
            long minObserved = Math.min(control, test);
            double controlCoverage = safeRate(control, controlEvents);
            double testCoverage = safeRate(test, testEvents);
            double minCoverage = Math.min(controlCoverage, testCoverage);
            boolean eventsReady = minObserved >= minKpiEvents;
            boolean coverageReady = minCoverage >= minCoverageRate;
            metrics.put(kpi, Map.ofEntries(
                    Map.entry("control", control),
                    Map.entry("test", test),
                    Map.entry("control_coverage", controlCoverage),
                    Map.entry("test_coverage", testCoverage),
                    Map.entry("min_coverage", minCoverage),
                    Map.entry("min_coverage_threshold", minCoverageRate),
                    Map.entry("min_observed", minObserved),
                    Map.entry("threshold", minKpiEvents),
                    Map.entry("events_ready", eventsReady),
                    Map.entry("coverage_ready", coverageReady),
                    Map.entry("ready", eventsReady && coverageReady)
            ));
            if (!eventsReady || !coverageReady) {
                ready = false;
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("required_kpis", requiredKpis);
        payload.put("min_events_per_cohort", minKpiEvents);
        payload.put("min_coverage_rate_per_cohort", minCoverageRate);
        payload.put("ready_for_decision", ready);
        payload.put("metrics", metrics);
        return payload;
    }

    private List<String> resolveRequiredPrimaryKpis() {
        Object value = resolveDialogConfigValue("workspace_rollout_required_primary_kpis");
        List<String> fromConfig = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                String normalized = normalizeKpiKey(item);
                if (normalized != null) fromConfig.add(normalized);
            }
        } else if (value instanceof String csv) {
            for (String chunk : csv.split(",")) {
                String normalized = normalizeKpiKey(chunk);
                if (normalized != null) fromConfig.add(normalized);
            }
        }
        List<String> unique = fromConfig.stream().distinct().toList();
        return unique.isEmpty() ? DEFAULT_REQUIRED_PRIMARY_KPIS : unique;
    }

    private long resolveMinKpiEventsForDecision() {
        Object value = resolveDialogConfigValue("workspace_rollout_min_kpi_events");
        if (value instanceof Number number && number.longValue() > 0) {
            return number.longValue();
        }
        try {
            long parsed = Long.parseLong(String.valueOf(value));
            return parsed > 0 ? parsed : DEFAULT_MIN_KPI_EVENTS_FOR_DECISION;
        } catch (Exception ignored) {
            return DEFAULT_MIN_KPI_EVENTS_FOR_DECISION;
        }
    }


    private double resolveMinKpiCoverageRateForDecision() {
        Object value = resolveDialogConfigValue("workspace_rollout_min_kpi_coverage_rate");
        if (value instanceof Number number) {
            double normalized = number.doubleValue();
            if (normalized > 0d && normalized <= 1d) {
                return normalized;
            }
        }
        if (value instanceof String text) {
            try {
                double parsed = Double.parseDouble(text.trim());
                if (parsed > 0d && parsed <= 1d) {
                    return parsed;
                }
            } catch (Exception ignored) {
                return DEFAULT_MIN_KPI_COVERAGE_RATE_FOR_DECISION;
            }
        }
        return DEFAULT_MIN_KPI_COVERAGE_RATE_FOR_DECISION;
    }

    private Object resolveDialogConfigValue(String key) {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Object dialogConfigRaw = settings.get("dialog_config");
        if (!(dialogConfigRaw instanceof Map<?, ?> map)) {
            return null;
        }
        return map.get(key);
    }

    private String normalizeKpiKey(Object raw) {
        String value = raw == null ? "" : String.valueOf(raw).trim().toLowerCase();
        if (value.isBlank()) return null;
        return value.replace('-', '_');
    }

    private Map<String, Object> buildWorkspaceGuardrails(Map<String, Object> totals,
                                                          Map<String, Object> previousTotals,
                                                          List<Map<String, Object>> cohortRows,
                                                          List<Map<String, Object>> shiftRows,
                                                          List<Map<String, Object>> teamRows,
                                                          Map<String, Object> telemetryConfig) {
        long events = Math.max(1L, toLong(totals.get("events")));
        long renderErrors = toLong(totals.get("render_errors"));
        long fallbacks = toLong(totals.get("fallbacks"));
        long abandons = toLong(totals.get("abandons"));
        long slowOpenEvents = toLong(totals.get("slow_open_events"));

        double renderErrorRate = (double) renderErrors / events;
        double fallbackRate = (double) fallbacks / events;
        double abandonRate = (double) abandons / events;
        double slowOpenRate = (double) slowOpenEvents / events;

        double renderErrorThreshold = resolveDoubleConfig(telemetryConfig, "guardrail_render_error_rate", DEFAULT_GUARDRAIL_RENDER_ERROR_RATE, 0.0001d, 1d);
        double fallbackThreshold = resolveDoubleConfig(telemetryConfig, "guardrail_fallback_rate", DEFAULT_GUARDRAIL_FALLBACK_RATE, 0.0001d, 1d);
        double abandonThreshold = resolveDoubleConfig(telemetryConfig, "guardrail_abandon_rate", DEFAULT_GUARDRAIL_ABANDON_RATE, 0.0001d, 1d);
        double slowOpenThreshold = resolveDoubleConfig(telemetryConfig, "guardrail_slow_open_rate", DEFAULT_GUARDRAIL_SLOW_OPEN_RATE, 0.0001d, 1d);
        int minDimensionEvents = (int) resolveLongConfig(telemetryConfig, "dimension_min_events", DEFAULT_DIMENSION_MIN_EVENTS, 5, 1000);

        Map<String, Object> rates = new LinkedHashMap<>();
        rates.put("render_error", renderErrorRate);
        rates.put("fallback", fallbackRate);
        rates.put("abandon", abandonRate);
        rates.put("slow_open", slowOpenRate);
        rates.put("threshold_render_error", renderErrorThreshold);
        rates.put("threshold_fallback", fallbackThreshold);
        rates.put("threshold_abandon", abandonThreshold);
        rates.put("threshold_slow_open", slowOpenThreshold);

        List<Map<String, Object>> alerts = new ArrayList<>();
        appendGuardrailAlert(alerts,
                "render_error",
                "Доля workspace_render_error превышает SLO 1%.",
                renderErrorRate,
                renderErrorThreshold,
                "below_or_equal");
        appendGuardrailAlert(alerts,
                "fallback",
                "Доля fallback в legacy превышает SLO 3%.",
                fallbackRate,
                fallbackThreshold,
                "below_or_equal");
        appendGuardrailAlert(alerts,
                "abandon",
                "Доля abandon в workspace превышает рабочий порог 10%.",
                abandonRate,
                abandonThreshold,
                "below_or_equal");
        appendGuardrailAlert(alerts,
                "slow_open",
                "Доля медленных workspace_open_ms (>2000ms) превышает рабочий порог 5%.",
                slowOpenRate,
                slowOpenThreshold,
                "below_or_equal");
        appendRegressionGuardrailAlerts(alerts, totals, previousTotals);
        appendDimensionGuardrailAlerts(alerts, cohortRows, "cohort", "experiment_cohort", minDimensionEvents);
        appendDimensionGuardrailAlerts(alerts, shiftRows, "shift", "shift", minDimensionEvents);
        appendDimensionGuardrailAlerts(alerts, teamRows, "team", "team", minDimensionEvents);

        Map<String, Object> guardrails = new LinkedHashMap<>();
        guardrails.put("status", alerts.isEmpty() ? "ok" : "attention");
        guardrails.put("rates", rates);
        guardrails.put("alerts", alerts);
        guardrails.put("dimension_min_events", minDimensionEvents);
        return guardrails;
    }

    private Map<String, Object> resolveWorkspaceTelemetryConfig() {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        if (settings == null || settings.isEmpty()) {
            return Map.of();
        }
        Object dialogConfigRaw = settings.get("dialog_config");
        if (dialogConfigRaw instanceof Map<?, ?> dialogConfig) {
            return castObjectMap(dialogConfig);
        }
        return Map.of();
    }

    private double resolveDoubleConfig(Map<String, Object> source,
                                       String key,
                                       double fallback,
                                       double min,
                                       double max) {
        if (source == null || source.isEmpty()) {
            return fallback;
        }
        Object raw = source.get(key);
        if (raw instanceof Number number) {
            double value = number.doubleValue();
            if (Double.isFinite(value) && value >= min && value <= max) {
                return value;
            }
            return fallback;
        }
        if (raw instanceof String stringValue) {
            try {
                double value = Double.parseDouble(stringValue.trim());
                if (Double.isFinite(value) && value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private long resolveLongConfig(Map<String, Object> source,
                                   String key,
                                   long fallback,
                                   long min,
                                   long max) {
        if (source == null || source.isEmpty()) {
            return fallback;
        }
        Object raw = source.get(key);
        if (raw instanceof Number number) {
            long value = number.longValue();
            if (value >= min && value <= max) {
                return value;
            }
            return fallback;
        }
        if (raw instanceof String stringValue) {
            try {
                long value = Long.parseLong(stringValue.trim());
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
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
                       SUM(CASE WHEN event_type = 'workspace_open_ms' THEN 1 ELSE 0 END) AS workspace_open_events,
                       SUM(CASE WHEN event_type = 'workspace_context_profile_gap' THEN 1 ELSE 0 END) AS context_profile_gap_events,
                       SUM(CASE WHEN event_type = 'workspace_context_source_gap' THEN 1 ELSE 0 END) AS context_source_gap_events,
                       SUM(CASE WHEN event_type = 'workspace_context_block_gap' THEN 1 ELSE 0 END) AS context_block_gap_events,
                       SUM(CASE WHEN event_type = 'workspace_parity_gap' THEN 1 ELSE 0 END) AS workspace_parity_gap_events,
                       SUM(CASE WHEN event_type = 'workspace_inline_navigation' THEN 1 ELSE 0 END) AS workspace_inline_navigation_events,
                       SUM(CASE WHEN event_type = 'workspace_open_legacy_manual' THEN 1 ELSE 0 END) AS manual_legacy_open_events,
                       SUM(CASE WHEN event_type = 'workspace_open_ms' AND COALESCE(duration_ms, 0) > 2000 THEN 1 ELSE 0 END) AS slow_open_events,
                       SUM(CASE WHEN event_type = 'kpi_frt_recorded' OR LOWER(COALESCE(primary_kpis, '')) LIKE '%frt%' THEN 1 ELSE 0 END) AS kpi_frt_events,
                       SUM(CASE WHEN event_type = 'kpi_ttr_recorded' OR LOWER(COALESCE(primary_kpis, '')) LIKE '%ttr%' THEN 1 ELSE 0 END) AS kpi_ttr_events,
                       SUM(CASE WHEN event_type = 'kpi_sla_breach_recorded' OR LOWER(COALESCE(primary_kpis, '')) LIKE '%sla_breach%' THEN 1 ELSE 0 END) AS kpi_sla_breach_events,
                       SUM(CASE WHEN event_type = 'kpi_dialogs_per_shift_recorded' OR LOWER(COALESCE(secondary_kpis, '')) LIKE '%dialogs_per_shift%' THEN 1 ELSE 0 END) AS kpi_dialogs_per_shift_events,
                       SUM(CASE WHEN event_type = 'kpi_csat_recorded' OR LOWER(COALESCE(secondary_kpis, '')) LIKE '%csat%' THEN 1 ELSE 0 END) AS kpi_csat_events,
                       SUM(CASE WHEN event_type = 'kpi_frt_recorded' THEN 1 ELSE 0 END) AS kpi_frt_recorded_events,
                       SUM(CASE WHEN event_type = 'kpi_ttr_recorded' THEN 1 ELSE 0 END) AS kpi_ttr_recorded_events,
                       SUM(CASE WHEN event_type = 'kpi_sla_breach_recorded' THEN 1 ELSE 0 END) AS kpi_sla_breach_recorded_events,
                       SUM(CASE WHEN event_type = 'kpi_dialogs_per_shift_recorded' THEN 1 ELSE 0 END) AS kpi_dialogs_per_shift_recorded_events,
                       SUM(CASE WHEN event_type = 'kpi_csat_recorded' THEN 1 ELSE 0 END) AS kpi_csat_recorded_events,
                       AVG(CASE WHEN event_type = 'kpi_frt_recorded' THEN duration_ms END) AS avg_frt_ms,
                       AVG(CASE WHEN event_type = 'kpi_ttr_recorded' THEN duration_ms END) AS avg_ttr_ms,
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
                item.put("workspace_open_events", rs.getLong("workspace_open_events"));
                item.put("context_profile_gap_events", rs.getLong("context_profile_gap_events"));
                item.put("context_source_gap_events", rs.getLong("context_source_gap_events"));
                item.put("context_block_gap_events", rs.getLong("context_block_gap_events"));
                item.put("workspace_parity_gap_events", rs.getLong("workspace_parity_gap_events"));
                item.put("workspace_inline_navigation_events", rs.getLong("workspace_inline_navigation_events"));
                item.put("manual_legacy_open_events", rs.getLong("manual_legacy_open_events"));
                item.put("slow_open_events", rs.getLong("slow_open_events"));
                item.put("kpi_frt_events", rs.getLong("kpi_frt_events"));
                item.put("kpi_ttr_events", rs.getLong("kpi_ttr_events"));
                item.put("kpi_sla_breach_events", rs.getLong("kpi_sla_breach_events"));
                item.put("kpi_dialogs_per_shift_events", rs.getLong("kpi_dialogs_per_shift_events"));
                item.put("kpi_csat_events", rs.getLong("kpi_csat_events"));
                item.put("kpi_frt_recorded_events", rs.getLong("kpi_frt_recorded_events"));
                item.put("kpi_ttr_recorded_events", rs.getLong("kpi_ttr_recorded_events"));
                item.put("kpi_sla_breach_recorded_events", rs.getLong("kpi_sla_breach_recorded_events"));
                item.put("kpi_dialogs_per_shift_recorded_events", rs.getLong("kpi_dialogs_per_shift_recorded_events"));
                item.put("kpi_csat_recorded_events", rs.getLong("kpi_csat_recorded_events"));
                item.put("avg_frt_ms", rs.getObject("avg_frt_ms") != null ? Math.round(rs.getDouble("avg_frt_ms")) : null);
                item.put("avg_ttr_ms", rs.getObject("avg_ttr_ms") != null ? Math.round(rs.getDouble("avg_ttr_ms")) : null);
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

    private Map<String, Object> buildWorkspaceGapBreakdown(int windowDays, String experimentName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("profile", loadWorkspaceGapBreakdownRows(windowDays, experimentName, "workspace_context_profile_gap"));
        payload.put("source", loadWorkspaceGapBreakdownRows(windowDays, experimentName, "workspace_context_source_gap"));
        payload.put("block", loadWorkspaceGapBreakdownRows(windowDays, experimentName, "workspace_context_block_gap"));
        payload.put("parity", loadWorkspaceGapBreakdownRows(windowDays, experimentName, "workspace_parity_gap"));
        return payload;
    }

    private List<Map<String, Object>> loadWorkspaceGapBreakdownRows(int windowDays,
                                                                    String experimentName,
                                                                    String eventType) {
        String filterExperiment = StringUtils.hasText(experimentName) ? experimentName.trim() : null;
        String sql = """
                SELECT reason, ticket_id, created_at
                  FROM workspace_telemetry_audit
                 WHERE created_at >= ?
                   AND created_at < ?
                   AND event_type = ?
                   AND (? IS NULL OR experiment_name = ?)
                 ORDER BY created_at DESC
                """;
        try {
            Instant windowEnd = Instant.now();
            Instant windowStart = windowEnd.minusSeconds(windowDays * 24L * 60L * 60L);
            List<Map<String, Object>> rawRows = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("reason", rs.getString("reason"));
                item.put("ticket_id", rs.getString("ticket_id"));
                Timestamp createdAt = rs.getTimestamp("created_at");
                item.put("created_at", createdAt != null ? createdAt.toInstant() : null);
                return item;
            }, Timestamp.from(windowStart), Timestamp.from(windowEnd), eventType, filterExperiment, filterExperiment);
            return aggregateWorkspaceGapReasons(rawRows);
        } catch (DataAccessException ex) {
            log.warn("Unable to load workspace gap breakdown for {}: {}", eventType, ex.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> aggregateWorkspaceGapReasons(List<Map<String, Object>> rawRows) {
        if (rawRows == null || rawRows.isEmpty()) {
            return List.of();
        }
        Map<String, GapBreakdownAccumulator> aggregates = new LinkedHashMap<>();
        for (Map<String, Object> row : rawRows) {
            List<String> reasons = normalizeWorkspaceGapReasons(row.get("reason"));
            String ticketId = normalizeNullString(String.valueOf(row.getOrDefault("ticket_id", "")));
            Instant createdAt = row.get("created_at") instanceof Instant value ? value : null;
            for (String reason : reasons) {
                aggregates.computeIfAbsent(reason, GapBreakdownAccumulator::new)
                        .record(ticketId, createdAt);
            }
        }
        return aggregates.values().stream()
                .sorted(Comparator.comparingLong(GapBreakdownAccumulator::events).reversed()
                        .thenComparing(GapBreakdownAccumulator::reason))
                .limit(10)
                .map(GapBreakdownAccumulator::toMap)
                .toList();
    }

    private List<String> normalizeWorkspaceGapReasons(Object rawReason) {
        if (rawReason == null) {
            return List.of("unspecified");
        }
        String normalized = String.valueOf(rawReason).trim();
        if (normalized.isBlank()) {
            return List.of("unspecified");
        }
        List<String> reasons = Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(10)
                .toList();
        return reasons.isEmpty() ? List.of("unspecified") : reasons;
    }

    private static final class GapBreakdownAccumulator {

        private final String reason;
        private long events = 0L;
        private final Set<String> ticketIds = new LinkedHashSet<>();
        private Instant lastSeenAt;

        private GapBreakdownAccumulator(String reason) {
            this.reason = reason;
        }

        private void record(String ticketId, Instant createdAt) {
            events++;
            if (StringUtils.hasText(ticketId)) {
                ticketIds.add(ticketId.trim());
            }
            if (createdAt != null && (lastSeenAt == null || createdAt.isAfter(lastSeenAt))) {
                lastSeenAt = createdAt;
            }
        }

        private long events() {
            return events;
        }

        private String reason() {
            return reason;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("reason", reason);
            payload.put("events", events);
            payload.put("tickets", ticketIds.size());
            payload.put("last_seen_at", lastSeenAt != null ? lastSeenAt.toString() : "");
            return payload;
        }
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

    private static Long parseLong(Object value) {
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
