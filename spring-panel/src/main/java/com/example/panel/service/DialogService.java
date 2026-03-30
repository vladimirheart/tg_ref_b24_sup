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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static final int DEFAULT_MACRO_GOVERNANCE_UNUSED_DAYS = 30;
    private static final long DEFAULT_MACRO_GOVERNANCE_REVIEW_TTL_HOURS = 24L * 90L;
    private static final long DEFAULT_MACRO_GOVERNANCE_CHECKPOINT_TTL_HOURS = 24L * 7L;
    private static final Pattern MACRO_VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([a-z0-9_]+)(?:\\s*\\|\\s*([^}]+))?\\s*}}", Pattern.CASE_INSENSITIVE);
    private static final Set<String> BUILTIN_MACRO_VARIABLE_KEYS = Set.of(
            "client_name",
            "ticket_id",
            "operator_name",
            "channel_name",
            "business",
            "location",
            "dialog_status",
            "created_at",
            "client_total_dialogs",
            "client_open_dialogs",
            "client_resolved_30d",
            "client_avg_rating",
            "client_segment_list",
            "current_date",
            "current_time"
    );

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
            log.warn("Unable to load dialog summary, returning empty view: {}", summarizeDataAccessException(ex));
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
            log.warn("Unable to load dialogs, returning empty list: {}", summarizeDataAccessException(ex));
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
            log.warn("Unable to load dialog {} details: {}", ticketId, summarizeDataAccessException(ex));
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
            log.warn("Unable to assign responsible for ticket {}: {}", ticketId, summarizeDataAccessException(ex));
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
            log.warn("Unable to mark dialog {} as read for {}: {}", ticketId, operator, summarizeDataAccessException(ex));
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
            log.warn("Unable to update responsible for ticket {}: {}", ticketId, summarizeDataAccessException(ex));
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
            log.warn("Unable to load chat history for ticket {}: {}", ticketId, summarizeDataAccessException(ex));
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
            log.warn("Unable to load client dialog history for user {}: {}", userId, summarizeDataAccessException(ex));
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
            log.warn("Unable to load client profile enrichment for user {}: {}", userId, summarizeDataAccessException(ex));
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
            log.warn("Unable to load related events with audit trail for ticket {}: {}. Fallback to legacy events.", ticketId, summarizeDataAccessException(ex));
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
                log.warn("Unable to load related events for ticket {}: {}", ticketId, summarizeDataAccessException(fallbackEx));
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
            log.warn("Unable to persist dialog action audit for ticket {}: {}", ticketId, summarizeDataAccessException(ex));
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
            log.warn("Unable to persist workspace telemetry event '{}': {}", eventType, summarizeDataAccessException(ex));
        }
    }

    public Map<String, Object> loadWorkspaceTelemetrySummary(int days, String experimentName) {
        Map<String, Object> workspaceTelemetryConfig = resolveWorkspaceTelemetryConfig();
        int windowDays = Math.max(1, Math.min(days, 30));
        Instant windowEnd = Instant.now();
        Instant windowStart = windowEnd.minusSeconds(windowDays * 24L * 60L * 60L);
        Instant previousWindowEnd = windowStart;
        Instant previousWindowStart = previousWindowEnd.minusSeconds(windowDays * 24L * 60L * 60L);
        List<Map<String, Object>> rows = loadWorkspaceTelemetryRows(windowStart, windowEnd, experimentName);
        List<Map<String, Object>> previousRows = loadWorkspaceTelemetryRows(previousWindowStart, previousWindowEnd, experimentName);
        List<Map<String, Object>> shiftRows = aggregateWorkspaceTelemetryRows(rows, "shift");
        List<Map<String, Object>> teamRows = aggregateWorkspaceTelemetryRows(rows, "team");
        Map<String, Object> totals = computeWorkspaceTelemetryTotals(rows);
        Map<String, Object> previousTotals = computeWorkspaceTelemetryTotals(previousRows);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("window_days", windowDays);
        payload.put("window_from_utc", windowStart.toString());
        payload.put("window_to_utc", windowEnd.toString());
        payload.put("generated_at", Instant.now().toString());
        payload.put("totals", totals);
        payload.put("previous_totals", previousTotals);
        payload.put("period_comparison", buildWorkspaceTelemetryComparison(totals, previousTotals));
        Map<String, Object> cohortComparison = buildWorkspaceCohortComparison(rows, workspaceTelemetryConfig);
        payload.put("cohort_comparison", cohortComparison);
        payload.put("rows", rows);
        payload.put("by_shift", shiftRows);
        payload.put("by_team", teamRows);
        payload.put("gap_breakdown", buildWorkspaceGapBreakdown(windowStart, windowEnd, experimentName));
        Map<String, Object> guardrails = buildWorkspaceGuardrails(totals, previousTotals, rows, shiftRows, teamRows, workspaceTelemetryConfig);
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

        Map<String, Object> workspaceTelemetryConfig = resolveWorkspaceTelemetryConfig();
        List<Map<String, Object>> rows = loadWorkspaceTelemetryRows(resolvedStart, resolvedEnd, experimentName);
        List<Map<String, Object>> previousRows = loadWorkspaceTelemetryRows(previousWindowStart, previousWindowEnd, experimentName);
        List<Map<String, Object>> shiftRows = aggregateWorkspaceTelemetryRows(rows, "shift");
        List<Map<String, Object>> teamRows = aggregateWorkspaceTelemetryRows(rows, "team");
        Map<String, Object> totals = computeWorkspaceTelemetryTotals(rows);
        Map<String, Object> previousTotals = computeWorkspaceTelemetryTotals(previousRows);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("window_days", windowDays);
        payload.put("window_from_utc", resolvedStart.toString());
        payload.put("window_to_utc", resolvedEnd.toString());
        payload.put("generated_at", Instant.now().toString());
        payload.put("totals", totals);
        payload.put("previous_totals", previousTotals);
        payload.put("period_comparison", buildWorkspaceTelemetryComparison(totals, previousTotals));
        Map<String, Object> cohortComparison = buildWorkspaceCohortComparison(rows, workspaceTelemetryConfig);
        payload.put("cohort_comparison", cohortComparison);
        payload.put("rows", rows);
        payload.put("by_shift", shiftRows);
        payload.put("by_team", teamRows);
        payload.put("gap_breakdown", buildWorkspaceGapBreakdown(resolvedStart, resolvedEnd, experimentName));
        Map<String, Object> guardrails = buildWorkspaceGuardrails(totals, previousTotals, rows, shiftRows, teamRows, workspaceTelemetryConfig);
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
        Map<String, Object> safeSettings = settings == null ? Map.of() : settings;
        Object rawDialogConfig = safeSettings.get("dialog_config");
        Map<String, Object> dialogConfig = rawDialogConfig instanceof Map<?, ?> map ? castObjectMap(map) : Map.of();
        OffsetDateTime generatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<Map<String, Object>> templates = safeListOfMaps(dialogConfig.get("macro_templates"));
        boolean requireOwner = resolveBooleanConfig(dialogConfig, "macro_governance_require_owner", false);
        boolean requireNamespace = resolveBooleanConfig(dialogConfig, "macro_governance_require_namespace", false);
        boolean requireReview = resolveBooleanConfig(dialogConfig, "macro_governance_require_review", false);
        boolean deprecationRequiresReason = resolveBooleanConfig(dialogConfig, "macro_governance_deprecation_requires_reason", false);
        boolean redListEnabled = resolveBooleanConfig(dialogConfig, "macro_governance_red_list_enabled", false);
        boolean ownerActionRequired = resolveBooleanConfig(dialogConfig, "macro_governance_owner_action_required", false);
        boolean aliasCleanupRequired = resolveBooleanConfig(dialogConfig, "macro_governance_alias_cleanup_required", false);
        boolean variableCleanupRequired = resolveBooleanConfig(dialogConfig, "macro_governance_variable_cleanup_required", false);
        boolean usageTierSlaRequired = resolveBooleanConfig(dialogConfig, "macro_governance_usage_tier_sla_required", false);
        long reviewTtlHours = resolveLongConfig(dialogConfig,
                "macro_governance_review_ttl_hours",
                DEFAULT_MACRO_GOVERNANCE_REVIEW_TTL_HOURS,
                1,
                24L * 365L);
        int usageWindowDays = (int) resolveLongConfig(dialogConfig,
                "macro_governance_unused_days",
                DEFAULT_MACRO_GOVERNANCE_UNUSED_DAYS,
                1,
                365);
        int redListUsageMax = (int) resolveLongConfig(dialogConfig,
                "macro_governance_red_list_usage_max",
                0,
                0,
                10000);
        int cleanupCadenceDays = (int) resolveLongConfig(dialogConfig,
                "macro_governance_cleanup_cadence_days",
                0,
                0,
                365);
        int usageTierLowMax = (int) resolveLongConfig(dialogConfig,
                "macro_governance_usage_tier_low_max",
                0,
                0,
                10000);
        int usageTierMediumMax = (int) resolveLongConfig(dialogConfig,
                "macro_governance_usage_tier_medium_max",
                5,
                0,
                10000);
        if (usageTierMediumMax < usageTierLowMax) {
            usageTierMediumMax = usageTierLowMax;
        }
        int cleanupSlaLowDays = (int) resolveLongConfig(dialogConfig,
                "macro_governance_cleanup_sla_low_days",
                7,
                1,
                365);
        int cleanupSlaMediumDays = (int) resolveLongConfig(dialogConfig,
                "macro_governance_cleanup_sla_medium_days",
                30,
                1,
                365);
        int cleanupSlaHighDays = (int) resolveLongConfig(dialogConfig,
                "macro_governance_cleanup_sla_high_days",
                90,
                1,
                365);
        int deprecationSlaLowDays = (int) resolveLongConfig(dialogConfig,
                "macro_governance_deprecation_sla_low_days",
                14,
                1,
                365);
        int deprecationSlaMediumDays = (int) resolveLongConfig(dialogConfig,
                "macro_governance_deprecation_sla_medium_days",
                45,
                1,
                365);
        int deprecationSlaHighDays = (int) resolveLongConfig(dialogConfig,
                "macro_governance_deprecation_sla_high_days",
                120,
                1,
                365);
        Set<String> knownMacroVariables = resolveKnownMacroVariableKeys(dialogConfig);

        List<Map<String, Object>> auditedTemplates = new ArrayList<>();
        List<Map<String, Object>> issues = new ArrayList<>();
        int publishedActiveTotal = 0;
        int deprecatedTotal = 0;
        int missingOwnerTotal = 0;
        int missingNamespaceTotal = 0;
        int staleReviewTotal = 0;
        int invalidReviewTotal = 0;
        int unusedPublishedTotal = 0;
        int deprecationGapTotal = 0;
        int redListTotal = 0;
        int ownerActionTotal = 0;
        int aliasCleanupTotal = 0;
        int variableCleanupTotal = 0;
        int cleanupSlaOverdueTotal = 0;
        int deprecationSlaOverdueTotal = 0;

        for (Map<String, Object> template : templates) {
            String templateId = normalizeNullString(String.valueOf(template.get("id")));
            String templateName = normalizeNullString(String.valueOf(template.get("name")));
            String templateText = normalizeNullString(String.valueOf(template.get("message")));
            if (!StringUtils.hasText(templateText)) {
                templateText = normalizeNullString(String.valueOf(template.get("text")));
            }
            boolean published = toBoolean(template.get("published"));
            boolean deprecated = toBoolean(template.get("deprecated"));
            boolean activePublished = published && !deprecated;
            String owner = normalizeNullString(String.valueOf(template.get("owner")));
            String namespace = normalizeNullString(String.valueOf(template.get("namespace")));
            String reviewedAtRaw = normalizeNullString(String.valueOf(template.get("reviewed_at")));
            OffsetDateTime reviewedAt = parseReviewTimestamp(reviewedAtRaw);
            boolean reviewedAtInvalid = StringUtils.hasText(reviewedAtRaw) && reviewedAt == null;
            long reviewAgeHours = reviewedAt != null
                    ? Math.max(0L, java.time.Duration.between(reviewedAt, generatedAt).toHours())
                    : -1L;
            boolean reviewFresh = reviewedAt != null && reviewAgeHours <= reviewTtlHours;
            String deprecationReason = normalizeNullString(String.valueOf(template.get("deprecation_reason")));
            Map<String, Object> usage = loadMacroTemplateUsage(templateId, templateName, usageWindowDays);
            long usageCount = toLong(usage.get("usage_count"));
            long previewCount = toLong(usage.get("preview_count"));
            long errorCount = toLong(usage.get("error_count"));
            String lastUsedAt = normalizeUtcTimestamp(usage.get("last_used_at"));
            OffsetDateTime lastUsedAtUtc = parseReviewTimestamp(lastUsedAt);
            String deprecatedAtRaw = normalizeNullString(String.valueOf(template.get("deprecated_at")));
            OffsetDateTime deprecatedAtUtc = parseReviewTimestamp(deprecatedAtRaw);
            List<String> tagAliases = resolveMacroTagAliases(template.get("tags"));
            int duplicateAliasCount = Math.max(0, tagAliases.size() - new LinkedHashSet<>(tagAliases).size());
            List<String> usedVariables = extractMacroTemplateVariables(templateText);
            List<String> unknownVariables = usedVariables.stream()
                    .filter(variable -> !knownMacroVariables.contains(variable))
                    .distinct()
                    .toList();
            String usageTier = resolveMacroUsageTier(usageCount, usageTierLowMax, usageTierMediumMax);
            int cleanupSlaDays = resolveMacroTierSlaDays(usageTier, cleanupSlaLowDays, cleanupSlaMediumDays, cleanupSlaHighDays);
            int deprecationSlaDays = resolveMacroTierSlaDays(usageTier, deprecationSlaLowDays, deprecationSlaMediumDays, deprecationSlaHighDays);
            OffsetDateTime cleanupReferenceAt = lastUsedAtUtc != null ? lastUsedAtUtc : (reviewedAt != null ? reviewedAt : generatedAt);
            long cleanupDueInDays = java.time.Duration.between(generatedAt, cleanupReferenceAt.plusDays(cleanupSlaDays)).toDays();
            String cleanupSlaStatus = !activePublished ? "off" : (cleanupDueInDays < 0 ? "hold" : "attention");
            OffsetDateTime deprecationReferenceAt = deprecatedAtUtc != null ? deprecatedAtUtc : generatedAt;
            long deprecationDueInDays = java.time.Duration.between(generatedAt, deprecationReferenceAt.plusDays(deprecationSlaDays)).toDays();
            String deprecationSlaStatus = !deprecated ? "off" : (deprecationDueInDays < 0 ? "hold" : "attention");

            if (activePublished) {
                publishedActiveTotal += 1;
            }
            if (deprecated) {
                deprecatedTotal += 1;
            }

            List<String> templateIssues = new ArrayList<>();
            boolean hasBlockingIssue = false;
            if (activePublished && !StringUtils.hasText(owner)) {
                missingOwnerTotal += 1;
                templateIssues.add("owner_missing");
                hasBlockingIssue = hasBlockingIssue || requireOwner;
                issues.add(buildMacroGovernanceIssue(
                        "owner_missing",
                        templateId,
                        templateName,
                        requireOwner ? "hold" : "attention",
                        requireOwner ? "rollout_blocker" : "backlog_candidate",
                        "У опубликованного макроса отсутствует owner.",
                        "owner=missing"));
            }
            if (activePublished && !StringUtils.hasText(namespace)) {
                missingNamespaceTotal += 1;
                templateIssues.add("namespace_missing");
                hasBlockingIssue = hasBlockingIssue || requireNamespace;
                issues.add(buildMacroGovernanceIssue(
                        "namespace_missing",
                        templateId,
                        templateName,
                        requireNamespace ? "hold" : "attention",
                        requireNamespace ? "rollout_blocker" : "backlog_candidate",
                        "У опубликованного макроса отсутствует namespace.",
                        "namespace=missing"));
            }
            if (activePublished && reviewedAtInvalid) {
                invalidReviewTotal += 1;
                templateIssues.add("review_invalid_utc");
                hasBlockingIssue = hasBlockingIssue || requireReview;
                issues.add(buildMacroGovernanceIssue(
                        "review_invalid_utc",
                        templateId,
                        templateName,
                        requireReview ? "hold" : "attention",
                        requireReview ? "rollout_blocker" : "backlog_candidate",
                        "Дата review макроса невалидна и не может быть интерпретирована как UTC.",
                        "reviewed_at=invalid"));
            }
            if (activePublished && requireReview && (!reviewFresh || reviewedAt == null || reviewedAtInvalid)) {
                staleReviewTotal += 1;
                templateIssues.add(reviewedAt == null ? "review_missing" : "review_stale");
                hasBlockingIssue = true;
                issues.add(buildMacroGovernanceIssue(
                        reviewedAt == null ? "review_missing" : "review_stale",
                        templateId,
                        templateName,
                        "hold",
                        "rollout_blocker",
                        reviewedAt == null
                                ? "У опубликованного макроса нет review-signoff."
                                : "Review макроса устарел и требует повторной проверки.",
                        reviewedAt == null
                                ? "reviewed_at=missing"
                                : "review_age_hours=%d > ttl=%d".formatted(reviewAgeHours, reviewTtlHours)));
            }
            if (activePublished && usageCount <= 0) {
                unusedPublishedTotal += 1;
                templateIssues.add("unused_recently");
                issues.add(buildMacroGovernanceIssue(
                        "unused_recently",
                        templateId,
                        templateName,
                        "attention",
                        "backlog_candidate",
                        "Опубликованный макрос не использовался в telemetry окне и требует cleanup-review.",
                        "window_days=%d".formatted(usageWindowDays)));
            }
            List<String> redListReasons = new ArrayList<>();
            if (redListEnabled && activePublished && usageCount <= redListUsageMax) {
                redListReasons.add("low_adoption");
            }
            if (redListEnabled && activePublished && previewCount > 0 && usageCount == 0) {
                redListReasons.add("preview_only");
            }
            if (redListEnabled && activePublished && errorCount > 0) {
                redListReasons.add("runtime_errors");
            }
            boolean redListCandidate = !redListReasons.isEmpty();
            if (redListCandidate) {
                redListTotal += 1;
                templateIssues.add("red_list_candidate");
                issues.add(buildMacroGovernanceIssue(
                        "red_list_candidate",
                        templateId,
                        templateName,
                        ownerActionRequired ? "hold" : "attention",
                        ownerActionRequired ? "rollout_blocker" : "backlog_candidate",
                        "Макрос попал в quality red-list и требует owner review.",
                        "reasons=%s".formatted(String.join(",", redListReasons))));
            }
            if (aliasCleanupRequired && activePublished && duplicateAliasCount > 0) {
                aliasCleanupTotal += 1;
                templateIssues.add("alias_cleanup_required");
                issues.add(buildMacroGovernanceIssue(
                        "alias_cleanup_required",
                        templateId,
                        templateName,
                        "attention",
                        "backlog_candidate",
                        "У macro template есть дублирующиеся aliases/tags и нужен cleanup.",
                        "duplicate_aliases=%d".formatted(duplicateAliasCount)));
            }
            if (variableCleanupRequired && activePublished && !unknownVariables.isEmpty()) {
                variableCleanupTotal += 1;
                templateIssues.add("unknown_variables_detected");
                issues.add(buildMacroGovernanceIssue(
                        "unknown_variables_detected",
                        templateId,
                        templateName,
                        "attention",
                        "backlog_candidate",
                        "В macro template есть переменные вне известного каталога.",
                        "unknown_variables=%s".formatted(String.join(",", unknownVariables))));
            }
            boolean ownerActionNeeded = ownerActionRequired
                    && activePublished
                    && (redListCandidate || (aliasCleanupRequired && duplicateAliasCount > 0)
                    || (variableCleanupRequired && !unknownVariables.isEmpty()));
            long ownerActionDueInDays = Long.MIN_VALUE;
            String ownerActionStatus = "off";
            if (ownerActionNeeded) {
                ownerActionTotal += 1;
                OffsetDateTime dueReference = lastUsedAtUtc != null ? lastUsedAtUtc : reviewedAt;
                if (!StringUtils.hasText(owner)) {
                    ownerActionStatus = "hold";
                } else if (cleanupCadenceDays <= 0) {
                    ownerActionStatus = "attention";
                } else if (dueReference == null) {
                    ownerActionStatus = "hold";
                } else {
                    ownerActionDueInDays = java.time.Duration.between(generatedAt, dueReference.plusDays(cleanupCadenceDays)).toDays();
                    ownerActionStatus = ownerActionDueInDays < 0 ? "hold" : "attention";
                }
                templateIssues.add("owner_action_required");
                issues.add(buildMacroGovernanceIssue(
                        "owner_action_required",
                        templateId,
                        templateName,
                        ownerActionStatus,
                        "hold".equals(ownerActionStatus) ? "rollout_blocker" : "backlog_candidate",
                        "Для проблемного macro template требуется owner action.",
                        cleanupCadenceDays > 0
                                ? (ownerActionDueInDays == Long.MIN_VALUE
                                ? "owner_action_due=unknown"
                                : "owner_action_due_in_days=%d".formatted(ownerActionDueInDays))
                                : "owner_action_required"));
            }
            if (deprecated && deprecationRequiresReason && !StringUtils.hasText(deprecationReason)) {
                deprecationGapTotal += 1;
                templateIssues.add("deprecation_reason_missing");
                issues.add(buildMacroGovernanceIssue(
                        "deprecation_reason_missing",
                        templateId,
                        templateName,
                        "attention",
                        "backlog_candidate",
                        "Для deprecated макроса не указана причина вывода из эксплуатации.",
                        "deprecation_reason=missing"));
            }
            if (usageTierSlaRequired && activePublished && cleanupDueInDays < 0) {
                cleanupSlaOverdueTotal += 1;
                templateIssues.add("cleanup_sla_overdue");
                issues.add(buildMacroGovernanceIssue(
                        "cleanup_sla_overdue",
                        templateId,
                        templateName,
                        "hold",
                        "rollout_blocker",
                        "Cleanup SLA для macro template просрочен.",
                        "usage_tier=%s overdue_by_days=%d".formatted(usageTier, Math.abs(cleanupDueInDays))));
            }
            if (usageTierSlaRequired && deprecated && deprecationDueInDays < 0) {
                deprecationSlaOverdueTotal += 1;
                templateIssues.add("deprecation_sla_overdue");
                issues.add(buildMacroGovernanceIssue(
                        "deprecation_sla_overdue",
                        templateId,
                        templateName,
                        "hold",
                        "rollout_blocker",
                        "Deprecation SLA для macro template просрочен.",
                        "usage_tier=%s overdue_by_days=%d".formatted(usageTier, Math.abs(deprecationDueInDays))));
            }

            Map<String, Object> auditTemplate = new LinkedHashMap<>();
            auditTemplate.put("template_id", templateId);
            auditTemplate.put("template_name", templateName);
            auditTemplate.put("status", deprecated ? "off" : (hasBlockingIssue ? "hold" : (templateIssues.isEmpty() ? "ok" : "attention")));
            auditTemplate.put("published", published);
            auditTemplate.put("deprecated", deprecated);
            auditTemplate.put("owner", owner);
            auditTemplate.put("namespace", namespace);
            auditTemplate.put("reviewed_at_utc", reviewedAt != null ? reviewedAt.toString() : "");
            auditTemplate.put("reviewed_at_invalid_utc", reviewedAtInvalid);
            auditTemplate.put("review_age_hours", reviewAgeHours);
            auditTemplate.put("usage_count", usageCount);
            auditTemplate.put("preview_count", previewCount);
            auditTemplate.put("error_count", errorCount);
            auditTemplate.put("last_used_at_utc", lastUsedAt);
            auditTemplate.put("deprecated_at_utc", deprecatedAtUtc != null ? deprecatedAtUtc.toString() : "");
            auditTemplate.put("usage_tier", usageTier);
            auditTemplate.put("cleanup_sla_days", cleanupSlaDays);
            auditTemplate.put("cleanup_due_in_days", cleanupDueInDays);
            auditTemplate.put("cleanup_sla_status", cleanupSlaStatus);
            auditTemplate.put("deprecation_sla_days", deprecationSlaDays);
            auditTemplate.put("deprecation_due_in_days", deprecated ? deprecationDueInDays : -1L);
            auditTemplate.put("deprecation_sla_status", deprecationSlaStatus);
            auditTemplate.put("red_list_candidate", redListCandidate);
            auditTemplate.put("red_list_reasons", redListReasons);
            auditTemplate.put("owner_action_required", ownerActionNeeded);
            auditTemplate.put("owner_action_status", ownerActionStatus);
            auditTemplate.put("owner_action_due_in_days", ownerActionDueInDays == Long.MIN_VALUE ? -1L : ownerActionDueInDays);
            auditTemplate.put("alias_count", tagAliases.size());
            auditTemplate.put("duplicate_alias_count", duplicateAliasCount);
            auditTemplate.put("used_variables", usedVariables);
            auditTemplate.put("unknown_variables", unknownVariables);
            auditTemplate.put("unknown_variable_count", unknownVariables.size());
            auditTemplate.put("deprecation_reason", deprecationReason);
            auditTemplate.put("issues", templateIssues);
            auditedTemplates.add(auditTemplate);
        }

        boolean governanceReviewRequired = resolveBooleanConfig(dialogConfig, "macro_governance_review_required", false);
        long governanceReviewTtlHours = resolveLongConfig(
                dialogConfig,
                "macro_governance_checkpoint_ttl_hours",
                DEFAULT_MACRO_GOVERNANCE_CHECKPOINT_TTL_HOURS,
                1,
                24L * 90L);
        boolean governanceCleanupTicketRequired = resolveBooleanConfig(
                dialogConfig,
                "macro_governance_cleanup_ticket_required",
                false);
        String governanceReviewedBy = normalizeNullString(String.valueOf(dialogConfig.get("macro_governance_reviewed_by")));
        String governanceReviewedAtRaw = normalizeNullString(String.valueOf(dialogConfig.get("macro_governance_reviewed_at")));
        OffsetDateTime governanceReviewedAt = parseReviewTimestamp(governanceReviewedAtRaw);
        boolean governanceReviewedAtInvalid = StringUtils.hasText(governanceReviewedAtRaw) && governanceReviewedAt == null;
        long governanceReviewAgeHours = governanceReviewedAt != null
                ? Math.max(0L, java.time.Duration.between(governanceReviewedAt, generatedAt).toHours())
                : -1L;
        boolean governanceReviewFresh = governanceReviewedAt != null && governanceReviewAgeHours <= governanceReviewTtlHours;
        String governanceReviewNote = normalizeNullString(String.valueOf(dialogConfig.get("macro_governance_review_note")));
        String governanceDecision = normalizeNullString(String.valueOf(dialogConfig.get("macro_governance_review_decision")));
        if (governanceDecision != null) {
            governanceDecision = governanceDecision.toLowerCase(Locale.ROOT);
            if (!"go".equals(governanceDecision) && !"hold".equals(governanceDecision)) {
                governanceDecision = null;
            }
        }
        String governanceCleanupTicketId = normalizeNullString(String.valueOf(dialogConfig.get("macro_governance_cleanup_ticket_id")));
        boolean governanceReady = !governanceReviewRequired || (StringUtils.hasText(governanceReviewedBy)
                && governanceReviewFresh
                && !governanceReviewedAtInvalid
                && (!governanceCleanupTicketRequired || StringUtils.hasText(governanceCleanupTicketId)));
        List<String> governanceReviewIssues = new ArrayList<>();
        if (governanceReviewRequired) {
            if (!StringUtils.hasText(governanceReviewedBy) || governanceReviewedAt == null) {
                governanceReviewIssues.add("governance_review_missing");
                issues.add(buildMacroGovernanceIssue(
                        "governance_review_missing",
                        null,
                        null,
                        "hold",
                        "rollout_blocker",
                        "Macro governance review checkpoint не заполнен.",
                        "reviewed_by/reviewed_at=missing"));
            } else if (governanceReviewedAtInvalid) {
                governanceReviewIssues.add("governance_review_invalid_utc");
                issues.add(buildMacroGovernanceIssue(
                        "governance_review_invalid_utc",
                        null,
                        null,
                        "hold",
                        "rollout_blocker",
                        "Дата macro governance review невалидна и не может быть интерпретирована как UTC.",
                        "reviewed_at=invalid"));
            } else if (!governanceReviewFresh) {
                governanceReviewIssues.add("governance_review_stale");
                issues.add(buildMacroGovernanceIssue(
                        "governance_review_stale",
                        null,
                        null,
                        "hold",
                        "rollout_blocker",
                        "Macro governance review устарел и требует повторной проверки.",
                        "review_age_hours=%d > ttl=%d".formatted(governanceReviewAgeHours, governanceReviewTtlHours)));
            }
            if (governanceCleanupTicketRequired && !StringUtils.hasText(governanceCleanupTicketId)) {
                governanceReviewIssues.add("governance_cleanup_ticket_missing");
                issues.add(buildMacroGovernanceIssue(
                        "governance_cleanup_ticket_missing",
                        null,
                        null,
                        "attention",
                        "backlog_candidate",
                        "Для macro governance review требуется cleanup ticket.",
                        "cleanup_ticket_id=missing"));
            }
        }

        boolean externalCatalogContractRequired = resolveBooleanConfig(dialogConfig, "macro_external_catalog_contract_required", false);
        long externalCatalogContractTtlHours = resolveLongConfig(
                dialogConfig,
                "macro_external_catalog_contract_ttl_hours",
                168,
                1,
                24L * 90L);
        String externalCatalogExpectedVersion = normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_expected_version")));
        String externalCatalogObservedVersion = normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_observed_version")));
        String externalCatalogVerifiedBy = normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_verified_by")));
        String externalCatalogVerifiedAtRaw = normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_verified_at")));
        OffsetDateTime externalCatalogVerifiedAt = parseReviewTimestamp(externalCatalogVerifiedAtRaw);
        boolean externalCatalogVerifiedAtInvalid = StringUtils.hasText(externalCatalogVerifiedAtRaw) && externalCatalogVerifiedAt == null;
        long externalCatalogReviewAgeHours = externalCatalogVerifiedAt != null
                ? Math.max(0L, java.time.Duration.between(externalCatalogVerifiedAt, generatedAt).toHours())
                : -1L;
        boolean externalCatalogReviewFresh = externalCatalogVerifiedAt != null && externalCatalogReviewAgeHours <= externalCatalogContractTtlHours;
        String externalCatalogReviewNote = normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_review_note")));
        String externalCatalogDecision = normalizeNullString(String.valueOf(dialogConfig.get("macro_external_catalog_decision")));
        if (externalCatalogDecision != null) {
            externalCatalogDecision = externalCatalogDecision.toLowerCase(Locale.ROOT);
            if (!"go".equals(externalCatalogDecision) && !"hold".equals(externalCatalogDecision)) {
                externalCatalogDecision = null;
            }
        }
        List<String> externalCatalogIssues = new ArrayList<>();
        if (externalCatalogContractRequired) {
            if (!StringUtils.hasText(externalCatalogExpectedVersion)) {
                externalCatalogIssues.add("external_catalog_expected_version_missing");
                issues.add(buildMacroGovernanceIssue("external_catalog_expected_version_missing", null, null, "hold", "rollout_blocker", "Не задан expected version для external macro catalog.", "expected_version=missing"));
            }
            if (!StringUtils.hasText(externalCatalogObservedVersion)) {
                externalCatalogIssues.add("external_catalog_observed_version_missing");
                issues.add(buildMacroGovernanceIssue("external_catalog_observed_version_missing", null, null, "hold", "rollout_blocker", "Не зафиксирована observed version для external macro catalog.", "observed_version=missing"));
            }
            if (StringUtils.hasText(externalCatalogExpectedVersion) && StringUtils.hasText(externalCatalogObservedVersion)
                    && !externalCatalogExpectedVersion.equalsIgnoreCase(externalCatalogObservedVersion)) {
                externalCatalogIssues.add("external_catalog_version_mismatch");
                issues.add(buildMacroGovernanceIssue("external_catalog_version_mismatch", null, null, "hold", "rollout_blocker", "Observed version external macro catalog не совпадает с ожидаемой.", "expected=%s observed=%s".formatted(externalCatalogExpectedVersion, externalCatalogObservedVersion)));
            }
            if (!StringUtils.hasText(externalCatalogVerifiedBy) || externalCatalogVerifiedAt == null) {
                externalCatalogIssues.add("external_catalog_review_missing");
                issues.add(buildMacroGovernanceIssue("external_catalog_review_missing", null, null, "hold", "rollout_blocker", "External catalog compatibility review не заполнен.", "verified_by/verified_at=missing"));
            } else if (externalCatalogVerifiedAtInvalid) {
                externalCatalogIssues.add("external_catalog_review_invalid_utc");
                issues.add(buildMacroGovernanceIssue("external_catalog_review_invalid_utc", null, null, "hold", "rollout_blocker", "Дата compatibility review external macro catalog невалидна для UTC.", "verified_at=invalid"));
            } else if (!externalCatalogReviewFresh) {
                externalCatalogIssues.add("external_catalog_review_stale");
                issues.add(buildMacroGovernanceIssue("external_catalog_review_stale", null, null, "hold", "rollout_blocker", "External catalog compatibility review устарел.", "review_age_hours=%d > ttl=%d".formatted(externalCatalogReviewAgeHours, externalCatalogContractTtlHours)));
            }
        }
        boolean externalCatalogReady = !externalCatalogContractRequired || externalCatalogIssues.isEmpty();
        boolean deprecationPolicyRequired = resolveBooleanConfig(dialogConfig, "macro_deprecation_policy_required", false);
        long deprecationPolicyTtlHours = resolveLongConfig(
                dialogConfig,
                "macro_deprecation_policy_ttl_hours",
                168,
                1,
                24L * 90L);
        boolean deprecationPolicyTicketRequired = resolveBooleanConfig(dialogConfig, "macro_deprecation_policy_ticket_required", false);
        String deprecationPolicyReviewedBy = normalizeNullString(String.valueOf(dialogConfig.get("macro_deprecation_policy_reviewed_by")));
        String deprecationPolicyReviewedAtRaw = normalizeNullString(String.valueOf(dialogConfig.get("macro_deprecation_policy_reviewed_at")));
        OffsetDateTime deprecationPolicyReviewedAt = parseReviewTimestamp(deprecationPolicyReviewedAtRaw);
        boolean deprecationPolicyReviewedAtInvalid = StringUtils.hasText(deprecationPolicyReviewedAtRaw) && deprecationPolicyReviewedAt == null;
        long deprecationPolicyReviewAgeHours = deprecationPolicyReviewedAt != null
                ? Math.max(0L, java.time.Duration.between(deprecationPolicyReviewedAt, generatedAt).toHours())
                : -1L;
        boolean deprecationPolicyReviewFresh = deprecationPolicyReviewedAt != null && deprecationPolicyReviewAgeHours <= deprecationPolicyTtlHours;
        String deprecationPolicyDecision = normalizeNullString(String.valueOf(dialogConfig.get("macro_deprecation_policy_decision")));
        if (deprecationPolicyDecision != null) {
            deprecationPolicyDecision = deprecationPolicyDecision.toLowerCase(Locale.ROOT);
            if (!"go".equals(deprecationPolicyDecision) && !"hold".equals(deprecationPolicyDecision)) {
                deprecationPolicyDecision = null;
            }
        }
        String deprecationPolicyReviewNote = normalizeNullString(String.valueOf(dialogConfig.get("macro_deprecation_policy_review_note")));
        String deprecationPolicyTicketId = normalizeNullString(String.valueOf(dialogConfig.get("macro_deprecation_policy_ticket_id")));
        List<String> deprecationPolicyIssues = new ArrayList<>();
        if (deprecationPolicyRequired) {
            if (!StringUtils.hasText(deprecationPolicyReviewedBy) || deprecationPolicyReviewedAt == null) {
                deprecationPolicyIssues.add("deprecation_policy_review_missing");
                issues.add(buildMacroGovernanceIssue("deprecation_policy_review_missing", null, null, "hold", "rollout_blocker", "Macro deprecation policy review checkpoint не заполнен.", "reviewed_by/reviewed_at=missing"));
            } else if (deprecationPolicyReviewedAtInvalid) {
                deprecationPolicyIssues.add("deprecation_policy_review_invalid_utc");
                issues.add(buildMacroGovernanceIssue("deprecation_policy_review_invalid_utc", null, null, "hold", "rollout_blocker", "Дата macro deprecation policy review невалидна для UTC.", "reviewed_at=invalid"));
            } else if (!deprecationPolicyReviewFresh) {
                deprecationPolicyIssues.add("deprecation_policy_review_stale");
                issues.add(buildMacroGovernanceIssue("deprecation_policy_review_stale", null, null, "hold", "rollout_blocker", "Macro deprecation policy review устарел.", "review_age_hours=%d > ttl=%d".formatted(deprecationPolicyReviewAgeHours, deprecationPolicyTtlHours)));
            }
            if (deprecationPolicyTicketRequired && !StringUtils.hasText(deprecationPolicyTicketId)) {
                deprecationPolicyIssues.add("deprecation_policy_ticket_missing");
                issues.add(buildMacroGovernanceIssue("deprecation_policy_ticket_missing", null, null, "attention", "backlog_candidate", "Для deprecation policy checkpoint требуется deprecation ticket.", "deprecation_ticket_id=missing"));
            }
        }
        boolean deprecationPolicyReady = !deprecationPolicyRequired || deprecationPolicyIssues.isEmpty();

        String status;
        if (templates.isEmpty()) {
            status = "off";
        } else if (issues.stream().anyMatch(item -> "hold".equalsIgnoreCase(String.valueOf(item.get("status"))))) {
            status = "hold";
        } else if (!issues.isEmpty()) {
            status = "attention";
        } else {
            status = "ok";
        }
        long mandatoryIssueTotal = issues.stream()
                .filter(item -> "rollout_blocker".equals(String.valueOf(item.get("classification"))))
                .count();
        long advisoryIssueTotal = Math.max(0L, issues.size() - mandatoryIssueTotal);
        long reviewIssueTotal = issues.stream()
                .filter(item -> String.valueOf(item.get("type")).contains("review"))
                .count();
        long ownershipIssueTotal = issues.stream()
                .filter(item -> String.valueOf(item.get("type")).contains("owner")
                        || String.valueOf(item.get("type")).contains("namespace"))
                .count();
        long cleanupIssueTotal = issues.stream()
                .filter(item -> String.valueOf(item.get("type")).contains("cleanup")
                        || String.valueOf(item.get("type")).contains("deprecation")
                        || String.valueOf(item.get("type")).contains("alias")
                        || String.valueOf(item.get("type")).contains("variable"))
                .count();
        List<String> minimumRequiredCheckpoints = new ArrayList<>();
        if (governanceReviewRequired) {
            minimumRequiredCheckpoints.add("governance_review");
        }
        if (externalCatalogContractRequired) {
            minimumRequiredCheckpoints.add("external_catalog");
        }
        if (deprecationPolicyRequired && minimumRequiredCheckpoints.size() < 2) {
            minimumRequiredCheckpoints.add("deprecation_policy");
        }
        if (minimumRequiredCheckpoints.isEmpty() && requireOwner) {
            minimumRequiredCheckpoints.add("template_owner");
        }
        List<String> advisorySignals = new ArrayList<>();
        if (redListEnabled) {
            advisorySignals.add("red_list");
        }
        if (ownerActionRequired) {
            advisorySignals.add("owner_action");
        }
        if (aliasCleanupRequired) {
            advisorySignals.add("alias_cleanup");
        }
        if (variableCleanupRequired) {
            advisorySignals.add("variable_cleanup");
        }
        if (usageTierSlaRequired) {
            advisorySignals.add("usage_tier_sla");
        }
        Map<String, Boolean> requiredCheckpointState = new LinkedHashMap<>();
        requiredCheckpointState.put("governance_review", governanceReady);
        requiredCheckpointState.put("external_catalog", externalCatalogReady);
        requiredCheckpointState.put("deprecation_policy", deprecationPolicyReady);
        requiredCheckpointState.put("template_owner", missingOwnerTotal == 0);
        long requiredCheckpointTotal = minimumRequiredCheckpoints.size();
        long requiredCheckpointReadyTotal = minimumRequiredCheckpoints.stream()
                .filter(key -> Boolean.TRUE.equals(requiredCheckpointState.get(key)))
                .count();
        long requiredCheckpointClosureRatePct = requiredCheckpointTotal > 0
                ? Math.round((requiredCheckpointReadyTotal * 100d) / requiredCheckpointTotal)
                : 100L;
        long freshnessCheckpointTotal = Stream.of(governanceReviewRequired, externalCatalogContractRequired, deprecationPolicyRequired)
                .filter(Boolean::booleanValue)
                .count();
        long freshnessCheckpointReadyTotal = 0L;
        if (governanceReviewRequired && governanceReviewedAt != null && governanceReviewFresh && !governanceReviewedAtInvalid) {
            freshnessCheckpointReadyTotal += 1L;
        }
        if (externalCatalogContractRequired && externalCatalogVerifiedAt != null && externalCatalogReviewFresh && !externalCatalogVerifiedAtInvalid) {
            freshnessCheckpointReadyTotal += 1L;
        }
        if (deprecationPolicyRequired && deprecationPolicyReviewedAt != null && deprecationPolicyReviewFresh && !deprecationPolicyReviewedAtInvalid) {
            freshnessCheckpointReadyTotal += 1L;
        }
        long freshnessClosureRatePct = freshnessCheckpointTotal > 0
                ? Math.round((freshnessCheckpointReadyTotal * 100d) / freshnessCheckpointTotal)
                : 100L;
        long noiseRatioPct = issues.isEmpty()
                ? 0L
                : Math.round((advisoryIssueTotal * 100d) / issues.size());
        String noiseLevel = advisoryIssueTotal <= mandatoryIssueTotal
                ? "controlled"
                : advisoryIssueTotal >= Math.max(3L, mandatoryIssueTotal * 2L)
                ? "high"
                : "moderate";
        String weeklyReviewPriority = requiredCheckpointClosureRatePct < 100L
                ? "close_required_path"
                : freshnessClosureRatePct < 100L
                ? "refresh_stale_checkpoints"
                : "high".equals(noiseLevel)
                ? "reduce_advisory_noise"
                : advisoryIssueTotal > mandatoryIssueTotal
                ? "trim_advisory_noise"
                : "monitor";
        String weeklyReviewSummary = switch (weeklyReviewPriority) {
            case "close_required_path" -> "Сначала закройте обязательные macro checkpoints.";
            case "refresh_stale_checkpoints" -> "Освежите review/catalog/deprecation checkpoints по UTC TTL.";
            case "reduce_advisory_noise" -> "Сократите advisory red-list шум до минимального обязательного контура.";
            case "trim_advisory_noise" -> "Проверьте, что advisory сигналы не доминируют над обязательными.";
            default -> "Closure, freshness и noise находятся в рабочем диапазоне.";
        };

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("generated_at", generatedAt.toInstant().toString());
        audit.put("status", status);
        audit.put("summary", templates.isEmpty()
                ? "Macro governance audit недоступен: макросы не настроены."
                : "Published active=%d, deprecated=%d, issues=%d, red-list=%d.".formatted(publishedActiveTotal, deprecatedTotal, issues.size(), redListTotal));
        audit.put("templates_total", templates.size());
        audit.put("published_active_total", publishedActiveTotal);
        audit.put("deprecated_total", deprecatedTotal);
        audit.put("issues_total", issues.size());
        audit.put("mandatory_issue_total", mandatoryIssueTotal);
        audit.put("advisory_issue_total", advisoryIssueTotal);
        audit.put("missing_owner_total", missingOwnerTotal);
        audit.put("missing_namespace_total", missingNamespaceTotal);
        audit.put("stale_review_total", staleReviewTotal);
        audit.put("invalid_review_total", invalidReviewTotal);
        audit.put("unused_published_total", unusedPublishedTotal);
        audit.put("deprecation_gap_total", deprecationGapTotal);
        audit.put("red_list_total", redListTotal);
        audit.put("owner_action_total", ownerActionTotal);
        audit.put("alias_cleanup_total", aliasCleanupTotal);
        audit.put("variable_cleanup_total", variableCleanupTotal);
        audit.put("cleanup_sla_overdue_total", cleanupSlaOverdueTotal);
        audit.put("deprecation_sla_overdue_total", deprecationSlaOverdueTotal);
        audit.put("minimum_required_checkpoints", minimumRequiredCheckpoints);
        audit.put("required_checkpoint_total", requiredCheckpointTotal);
        audit.put("required_checkpoint_ready_total", requiredCheckpointReadyTotal);
        audit.put("required_checkpoint_closure_rate_pct", requiredCheckpointClosureRatePct);
        audit.put("freshness_checkpoint_total", freshnessCheckpointTotal);
        audit.put("freshness_checkpoint_ready_total", freshnessCheckpointReadyTotal);
        audit.put("freshness_closure_rate_pct", freshnessClosureRatePct);
        audit.put("noise_ratio_pct", noiseRatioPct);
        audit.put("noise_level", noiseLevel);
        audit.put("weekly_review_priority", weeklyReviewPriority);
        audit.put("weekly_review_summary", weeklyReviewSummary);
        audit.put("advisory_signals", advisorySignals.stream().distinct().toList());
        audit.put("issue_breakdown", Map.of(
                "review", reviewIssueTotal,
                "ownership", ownershipIssueTotal,
                "cleanup", cleanupIssueTotal,
                "mandatory", mandatoryIssueTotal,
                "advisory", advisoryIssueTotal));
        audit.put("requirements", Map.ofEntries(
                Map.entry("require_owner", requireOwner),
                Map.entry("require_namespace", requireNamespace),
                Map.entry("require_review", requireReview),
                Map.entry("review_ttl_hours", reviewTtlHours),
                Map.entry("deprecation_requires_reason", deprecationRequiresReason),
                Map.entry("unused_days", usageWindowDays),
                Map.entry("red_list_enabled", redListEnabled),
                Map.entry("red_list_usage_max", redListUsageMax),
                Map.entry("owner_action_required", ownerActionRequired),
                Map.entry("cleanup_cadence_days", cleanupCadenceDays),
                Map.entry("alias_cleanup_required", aliasCleanupRequired),
                Map.entry("variable_cleanup_required", variableCleanupRequired),
                Map.entry("usage_tier_sla_required", usageTierSlaRequired),
                Map.entry("usage_tier_low_max", usageTierLowMax),
                Map.entry("usage_tier_medium_max", usageTierMediumMax),
                Map.entry("cleanup_sla_low_days", cleanupSlaLowDays),
                Map.entry("cleanup_sla_medium_days", cleanupSlaMediumDays),
                Map.entry("cleanup_sla_high_days", cleanupSlaHighDays),
                Map.entry("deprecation_sla_low_days", deprecationSlaLowDays),
                Map.entry("deprecation_sla_medium_days", deprecationSlaMediumDays),
                Map.entry("deprecation_sla_high_days", deprecationSlaHighDays)));
        audit.put("governance_review", Map.ofEntries(
                Map.entry("required", governanceReviewRequired),
                Map.entry("ready", governanceReady),
                Map.entry("reviewed_by", governanceReviewedBy == null ? "" : governanceReviewedBy),
                Map.entry("reviewed_at_utc", governanceReviewedAt == null ? "" : governanceReviewedAt.toString()),
                Map.entry("reviewed_at_invalid_utc", governanceReviewedAtInvalid),
                Map.entry("review_ttl_hours", governanceReviewTtlHours),
                Map.entry("review_age_hours", governanceReviewAgeHours),
                Map.entry("cleanup_ticket_required", governanceCleanupTicketRequired),
                Map.entry("cleanup_ticket_id", governanceCleanupTicketId == null ? "" : governanceCleanupTicketId),
                Map.entry("decision", governanceDecision == null ? "" : governanceDecision),
                Map.entry("review_note", governanceReviewNote == null ? "" : governanceReviewNote),
                Map.entry("issues", governanceReviewIssues)));
        audit.put("external_catalog_contract", Map.ofEntries(
                Map.entry("required", externalCatalogContractRequired),
                Map.entry("ready", externalCatalogReady),
                Map.entry("expected_version", externalCatalogExpectedVersion == null ? "" : externalCatalogExpectedVersion),
                Map.entry("observed_version", externalCatalogObservedVersion == null ? "" : externalCatalogObservedVersion),
                Map.entry("verified_by", externalCatalogVerifiedBy == null ? "" : externalCatalogVerifiedBy),
                Map.entry("verified_at_utc", externalCatalogVerifiedAt == null ? "" : externalCatalogVerifiedAt.toString()),
                Map.entry("verified_at_invalid_utc", externalCatalogVerifiedAtInvalid),
                Map.entry("review_ttl_hours", externalCatalogContractTtlHours),
                Map.entry("review_age_hours", externalCatalogReviewAgeHours),
                Map.entry("decision", externalCatalogDecision == null ? "" : externalCatalogDecision),
                Map.entry("review_note", externalCatalogReviewNote == null ? "" : externalCatalogReviewNote),
                Map.entry("issues", externalCatalogIssues)));
        audit.put("deprecation_policy", Map.ofEntries(
                Map.entry("required", deprecationPolicyRequired),
                Map.entry("ready", deprecationPolicyReady),
                Map.entry("reviewed_by", deprecationPolicyReviewedBy == null ? "" : deprecationPolicyReviewedBy),
                Map.entry("reviewed_at_utc", deprecationPolicyReviewedAt == null ? "" : deprecationPolicyReviewedAt.toString()),
                Map.entry("reviewed_at_invalid_utc", deprecationPolicyReviewedAtInvalid),
                Map.entry("review_ttl_hours", deprecationPolicyTtlHours),
                Map.entry("review_age_hours", deprecationPolicyReviewAgeHours),
                Map.entry("deprecation_ticket_required", deprecationPolicyTicketRequired),
                Map.entry("deprecation_ticket_id", deprecationPolicyTicketId == null ? "" : deprecationPolicyTicketId),
                Map.entry("decision", deprecationPolicyDecision == null ? "" : deprecationPolicyDecision),
                Map.entry("review_note", deprecationPolicyReviewNote == null ? "" : deprecationPolicyReviewNote),
                Map.entry("issues", deprecationPolicyIssues)));
        audit.put("issues", issues);
        audit.put("templates", auditedTemplates);
        return audit;
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
                        try {
                            deadlineInstant = Instant.parse(deadlineAt);
                        } catch (Exception ignored) {
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
            if (legacyReviewQueueFollowupRequired) {
                legacyInventoryActionItems.add("Закройте weekly closure-loop для сценариев, которые повторно остаются в legacy review-queue.");
            }
        }
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
        List<Map<String, Object>> manualLegacyReasonBreakdown = loadWorkspaceEventReasonBreakdown(
                "workspace_open_legacy_manual",
                windowStart,
                windowEnd,
                experimentName,
                5);
        List<Map<String, Object>> blockedLegacyReasonBreakdown = loadWorkspaceEventReasonBreakdown(
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
        List<Map<String, Object>> previousRows = loadWorkspaceTelemetryRows(previousWindowStart, previousWindowEnd, experimentName);
        Map<String, Object> previousTotals = computeWorkspaceTelemetryTotals(previousRows);
        long previousWorkspaceOpenEvents = toLong(previousTotals.get("workspace_open_events"));
        long previousManualLegacyOpenEvents = toLong(previousTotals.get("manual_legacy_open_events"));
        long previousManualLegacyBlockedEvents = toLong(previousTotals.get("workspace_open_legacy_blocked_events"));
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
                        .filter(row -> normalizeWorkspaceGapReasons(row.get("reason")).stream()
                                .map(value -> value.toLowerCase(Locale.ROOT))
                                .anyMatch(criticalReasonSet::contains))
                        .toList();
            }

            List<Map<String, Object>> topReasons = aggregateWorkspaceGapReasons(filteredRows);
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
            log.warn("Unable to load parity exit criteria snapshot: {}", summarizeDataAccessException(ex));
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

    private Map<String, Object> loadMacroTemplateUsage(String templateId, String templateName, int usageWindowDays) {
        if (!StringUtils.hasText(templateId) && !StringUtils.hasText(templateName)) {
            return Map.of("usage_count", 0L, "preview_count", 0L, "error_count", 0L, "last_used_at", "");
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT SUM(CASE WHEN event_type = 'macro_apply' THEN 1 ELSE 0 END) AS usage_count,
                       SUM(CASE WHEN event_type = 'macro_preview' THEN 1 ELSE 0 END) AS preview_count,
                       SUM(CASE WHEN error_code IS NOT NULL AND TRIM(CAST(error_code AS TEXT)) <> '' THEN 1 ELSE 0 END) AS error_count,
                       MAX(CASE WHEN event_type = 'macro_apply' THEN created_at ELSE NULL END) AS last_used_at
                  FROM workspace_telemetry_audit
                 WHERE event_type IN ('macro_apply', 'macro_preview')
                   AND created_at >= ?
                """);
        args.add(Timestamp.from(Instant.now().minusSeconds(Math.max(1, usageWindowDays) * 24L * 3600L)));
        if (StringUtils.hasText(templateId) && StringUtils.hasText(templateName)) {
            sql.append(" AND (template_id = ? OR template_name = ?)");
            args.add(templateId);
            args.add(templateName);
        } else if (StringUtils.hasText(templateId)) {
            sql.append(" AND template_id = ?");
            args.add(templateId);
        } else {
            sql.append(" AND template_name = ?");
            args.add(templateName);
        }
        try {
            return jdbcTemplate.queryForObject(sql.toString(), (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("usage_count", rs.getLong("usage_count"));
                row.put("preview_count", rs.getLong("preview_count"));
                row.put("error_count", rs.getLong("error_count"));
                Object lastUsed = rs.getObject("last_used_at");
                row.put("last_used_at", lastUsed != null ? String.valueOf(lastUsed) : "");
                return row;
            }, args.toArray());
        } catch (DataAccessException ex) {
            log.warn("Unable to load macro usage audit for template {}: {}", templateId, summarizeDataAccessException(ex));
            return Map.of("usage_count", 0L, "preview_count", 0L, "error_count", 0L, "last_used_at", "");
        }
    }

    private Set<String> resolveKnownMacroVariableKeys(Map<String, Object> dialogConfig) {
        Set<String> variables = new LinkedHashSet<>(BUILTIN_MACRO_VARIABLE_KEYS);
        safeListOfMaps(dialogConfig.get("macro_variable_catalog")).stream()
                .map(item -> normalizeNullString(String.valueOf(item.get("key"))))
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(variables::add);
        Map<String, Object> defaults = dialogConfig.get("macro_variable_defaults") instanceof Map<?, ?> map
                ? castObjectMap(map)
                : Map.of();
        defaults.keySet().stream()
                .map(key -> normalizeNullString(String.valueOf(key)))
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(variables::add);
        return variables;
    }

    private List<String> extractMacroTemplateVariables(String templateText) {
        if (!StringUtils.hasText(templateText)) {
            return List.of();
        }
        List<String> variables = new ArrayList<>();
        Matcher matcher = MACRO_VARIABLE_PATTERN.matcher(templateText);
        while (matcher.find()) {
            String key = normalizeNullString(matcher.group(1));
            if (StringUtils.hasText(key)) {
                String normalized = key.toLowerCase(Locale.ROOT);
                if (!variables.contains(normalized)) {
                    variables.add(normalized);
                }
            }
        }
        return variables;
    }

    private List<String> resolveMacroTagAliases(Object rawTags) {
        if (!(rawTags instanceof Collection<?> tags)) {
            return List.of();
        }
        return tags.stream()
                .map(tag -> normalizeNullString(String.valueOf(tag)))
                .filter(StringUtils::hasText)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
    }

    private String resolveMacroUsageTier(long usageCount, int lowMax, int mediumMax) {
        if (usageCount <= lowMax) {
            return "low";
        }
        if (usageCount <= mediumMax) {
            return "medium";
        }
        return "high";
    }

    private int resolveMacroTierSlaDays(String usageTier, int lowDays, int mediumDays, int highDays) {
        return switch (String.valueOf(usageTier).toLowerCase(Locale.ROOT)) {
            case "low" -> lowDays;
            case "medium" -> mediumDays;
            default -> highDays;
        };
    }

    private Map<String, Object> buildMacroGovernanceIssue(String type,
                                                          String templateId,
                                                          String templateName,
                                                          String status,
                                                          String classification,
                                                          String summary,
                                                          String detail) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("type", type);
        issue.put("template_id", templateId);
        issue.put("template_name", templateName);
        issue.put("status", normalizeScorecardStatus(status));
        issue.put("classification", classification);
        issue.put("summary", summary);
        issue.put("detail", detail);
        return issue;
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
        long contextAttributePolicyGapEvents = rows.stream().mapToLong(row -> toLong(row.get("context_attribute_policy_gap_events"))).sum();
        long contextBlockGapEvents = rows.stream().mapToLong(row -> toLong(row.get("context_block_gap_events"))).sum();
        long contextContractGapEvents = rows.stream().mapToLong(row -> toLong(row.get("context_contract_gap_events"))).sum();
        long contextSourcesExpandedEvents = rows.stream().mapToLong(row -> toLong(row.get("context_sources_expanded_events"))).sum();
        long contextAttributePolicyExpandedEvents = rows.stream().mapToLong(row -> toLong(row.get("context_attribute_policy_expanded_events"))).sum();
        long contextExtraAttributesExpandedEvents = rows.stream().mapToLong(row -> toLong(row.get("context_extra_attributes_expanded_events"))).sum();
        long workspaceSlaPolicyGapEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_sla_policy_gap_events"))).sum();
        long workspaceParityGapEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_parity_gap_events"))).sum();
        long workspaceInlineNavigationEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_inline_navigation_events"))).sum();
        long manualLegacyOpenEvents = rows.stream().mapToLong(row -> toLong(row.get("manual_legacy_open_events"))).sum();
        long workspaceOpenLegacyBlockedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_open_legacy_blocked_events"))).sum();
        long workspaceRolloutPacketViewedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_rollout_packet_viewed_events"))).sum();
        long workspaceRolloutReviewConfirmedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_rollout_review_confirmed_events"))).sum();
        long workspaceRolloutReviewDecisionGoEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_rollout_review_decision_go_events"))).sum();
        long workspaceRolloutReviewDecisionHoldEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_rollout_review_decision_hold_events"))).sum();
        long workspaceRolloutReviewDecisionRollbackEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_rollout_review_decision_rollback_events"))).sum();
        long workspaceRolloutReviewIncidentFollowupLinkedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_rollout_review_incident_followup_linked_events"))).sum();
        long workspaceSlaPolicyReviewUpdatedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_sla_policy_review_updated_events"))).sum();
        long workspaceSlaPolicyDecisionEvents = rows.stream()
                .mapToLong(row -> toLong(row.get("workspace_rollout_review_decision_go_events"))
                        + toLong(row.get("workspace_rollout_review_decision_hold_events"))
                        + toLong(row.get("workspace_rollout_review_decision_rollback_events")))
                .sum();
        long workspaceMacroGovernanceReviewUpdatedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_macro_governance_review_updated_events"))).sum();
        long workspaceMacroExternalCatalogPolicyUpdatedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_macro_external_catalog_policy_updated_events"))).sum();
        long workspaceMacroDeprecationPolicyUpdatedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_macro_deprecation_policy_updated_events"))).sum();
        long workspaceMacroPolicyUpdateEvents = workspaceMacroGovernanceReviewUpdatedEvents
                + workspaceMacroExternalCatalogPolicyUpdatedEvents
                + workspaceMacroDeprecationPolicyUpdatedEvents;
        long frtRecordedEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_frt_recorded_events"))).sum();
        long ttrRecordedEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_ttr_recorded_events"))).sum();
        long slaBreachRecordedEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_sla_breach_recorded_events"))).sum();
        long dialogsPerShiftRecordedEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_dialogs_per_shift_recorded_events"))).sum();
        long csatRecordedEvents = rows.stream().mapToLong(row -> toLong(row.get("kpi_csat_recorded_events"))).sum();

        long weightedOpenCount = rows.stream()
                .mapToLong(this::resolveWorkspaceOpenWeight)
                .sum();
        long weightedOpenSum = rows.stream()
                .mapToLong(row -> {
                    Long avgOpenMs = extractNullableLong(row.get("avg_open_ms"));
                    if (avgOpenMs == null) {
                        return 0L;
                    }
                    long rowWeight = resolveWorkspaceOpenWeight(row);
                    return avgOpenMs * rowWeight;
                })
                .sum();
        Long avgOpenMs = weightedOpenCount > 0 ? Math.round((double) weightedOpenSum / weightedOpenCount) : null;
        Long avgFrtMs = weightedAverage(rows, "kpi_frt_recorded_events", "avg_frt_ms");
        long workspaceLegacyUsagePolicyUpdatedEvents = rows.stream().mapToLong(row -> toLong(row.get("workspace_legacy_usage_policy_updated_events"))).sum();
        Long avgTtrMs = weightedAverage(rows, "kpi_ttr_recorded_events", "avg_ttr_ms");
        long contextSecondaryDetailsExpandedEvents = contextSourcesExpandedEvents
                + contextAttributePolicyExpandedEvents
                + contextExtraAttributesExpandedEvents;
        long contextSecondaryDetailsOpenRatePct = workspaceOpenEvents > 0
                ? Math.round((contextSecondaryDetailsExpandedEvents * 100d) / workspaceOpenEvents)
                : 0L;
        long workspaceSlaPolicyChurnRatioPct = workspaceSlaPolicyDecisionEvents > 0
                ? Math.round((workspaceSlaPolicyReviewUpdatedEvents * 100d) / workspaceSlaPolicyDecisionEvents)
                : (workspaceSlaPolicyReviewUpdatedEvents > 0 ? 100L : 0L);
        long workspaceSlaPolicyDecisionCoveragePct = workspaceSlaPolicyReviewUpdatedEvents > 0
                ? Math.round((workspaceSlaPolicyDecisionEvents * 100d) / workspaceSlaPolicyReviewUpdatedEvents)
                : 100L;

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
        totals.put("context_attribute_policy_gap_events", contextAttributePolicyGapEvents);
        totals.put("context_block_gap_events", contextBlockGapEvents);
        totals.put("context_contract_gap_events", contextContractGapEvents);
        totals.put("context_sources_expanded_events", contextSourcesExpandedEvents);
        totals.put("context_attribute_policy_expanded_events", contextAttributePolicyExpandedEvents);
        totals.put("context_extra_attributes_expanded_events", contextExtraAttributesExpandedEvents);
        totals.put("context_secondary_details_expanded_events", contextSecondaryDetailsExpandedEvents);
        totals.put("workspace_sla_policy_gap_events", workspaceSlaPolicyGapEvents);
        totals.put("workspace_parity_gap_events", workspaceParityGapEvents);
        totals.put("workspace_inline_navigation_events", workspaceInlineNavigationEvents);
        totals.put("manual_legacy_open_events", manualLegacyOpenEvents);
        totals.put("workspace_open_legacy_blocked_events", workspaceOpenLegacyBlockedEvents);
        totals.put("workspace_rollout_packet_viewed_events", workspaceRolloutPacketViewedEvents);
        totals.put("workspace_rollout_review_confirmed_events", workspaceRolloutReviewConfirmedEvents);
        totals.put("workspace_rollout_review_decision_go_events", workspaceRolloutReviewDecisionGoEvents);
        totals.put("workspace_rollout_review_decision_hold_events", workspaceRolloutReviewDecisionHoldEvents);
        totals.put("workspace_rollout_review_decision_rollback_events", workspaceRolloutReviewDecisionRollbackEvents);
        totals.put("workspace_rollout_review_incident_followup_linked_events", workspaceRolloutReviewIncidentFollowupLinkedEvents);
        totals.put("workspace_sla_policy_review_updated_events", workspaceSlaPolicyReviewUpdatedEvents);
        totals.put("workspace_sla_policy_decision_events", workspaceSlaPolicyDecisionEvents);
        totals.put("workspace_sla_policy_churn_ratio_pct", workspaceSlaPolicyChurnRatioPct);
        totals.put("workspace_sla_policy_decision_coverage_pct", workspaceSlaPolicyDecisionCoveragePct);
        totals.put("workspace_macro_governance_review_updated_events", workspaceMacroGovernanceReviewUpdatedEvents);
        totals.put("workspace_macro_external_catalog_policy_updated_events", workspaceMacroExternalCatalogPolicyUpdatedEvents);
        totals.put("workspace_macro_deprecation_policy_updated_events", workspaceMacroDeprecationPolicyUpdatedEvents);
        totals.put("workspace_macro_policy_update_events", workspaceMacroPolicyUpdateEvents);
        totals.put("context_profile_gap_rate", workspaceOpenEvents > 0 ? (double) contextProfileGapEvents / workspaceOpenEvents : 0d);
        totals.put("context_profile_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) contextProfileGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("context_source_gap_rate", workspaceOpenEvents > 0 ? (double) contextSourceGapEvents / workspaceOpenEvents : 0d);
        totals.put("context_source_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) contextSourceGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("context_attribute_policy_gap_rate", workspaceOpenEvents > 0 ? (double) contextAttributePolicyGapEvents / workspaceOpenEvents : 0d);
        totals.put("context_attribute_policy_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) contextAttributePolicyGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("context_block_gap_rate", workspaceOpenEvents > 0 ? (double) contextBlockGapEvents / workspaceOpenEvents : 0d);
        totals.put("context_block_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) contextBlockGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("context_contract_gap_rate", workspaceOpenEvents > 0 ? (double) contextContractGapEvents / workspaceOpenEvents : 0d);
        totals.put("context_contract_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) contextContractGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("context_secondary_details_open_rate_pct", contextSecondaryDetailsOpenRatePct);
        totals.put("workspace_sla_policy_gap_rate", workspaceOpenEvents > 0 ? (double) workspaceSlaPolicyGapEvents / workspaceOpenEvents : 0d);
        totals.put("workspace_sla_policy_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) workspaceSlaPolicyGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("workspace_parity_gap_rate", workspaceOpenEvents > 0 ? (double) workspaceParityGapEvents / workspaceOpenEvents : 0d);
        totals.put("workspace_parity_ready_rate", workspaceOpenEvents > 0
                ? Math.max(0d, 1d - ((double) workspaceParityGapEvents / workspaceOpenEvents))
                : 1d);
        totals.put("workspace_legacy_usage_policy_updated_events", workspaceLegacyUsagePolicyUpdatedEvents);
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

    private long resolveWorkspaceOpenWeight(Map<String, Object> row) {
        return Math.max(toLong(row.get("events"))
                - toLong(row.get("render_errors"))
                - toLong(row.get("fallbacks"))
                - toLong(row.get("abandons")), 0L);
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

    private boolean resolveBooleanConfig(Map<String, Object> source,
                                         String key,
                                         boolean fallback) {
        if (source == null || source.isEmpty() || !source.containsKey(key)) {
            return fallback;
        }
        Object raw = source.get(key);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof String stringValue && !StringUtils.hasText(stringValue)) {
            return fallback;
        }
        return toBoolean(raw);
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

    private List<Map<String, Object>> loadWorkspaceTelemetryRows(Instant windowStart, Instant windowEnd, String experimentName) {
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
                       SUM(CASE WHEN event_type = 'workspace_context_attribute_policy_gap' THEN 1 ELSE 0 END) AS context_attribute_policy_gap_events,
                       SUM(CASE WHEN event_type = 'workspace_context_block_gap' THEN 1 ELSE 0 END) AS context_block_gap_events,
                       SUM(CASE WHEN event_type = 'workspace_context_contract_gap' THEN 1 ELSE 0 END) AS context_contract_gap_events,
                       SUM(CASE WHEN event_type = 'workspace_context_sources_expanded' THEN 1 ELSE 0 END) AS context_sources_expanded_events,
                       SUM(CASE WHEN event_type = 'workspace_context_attribute_policy_expanded' THEN 1 ELSE 0 END) AS context_attribute_policy_expanded_events,
                       SUM(CASE WHEN event_type = 'workspace_context_extra_attributes_expanded' THEN 1 ELSE 0 END) AS context_extra_attributes_expanded_events,
                       SUM(CASE WHEN event_type = 'workspace_sla_policy_gap' THEN 1 ELSE 0 END) AS workspace_sla_policy_gap_events,
                       SUM(CASE WHEN event_type = 'workspace_parity_gap' THEN 1 ELSE 0 END) AS workspace_parity_gap_events,
                       SUM(CASE WHEN event_type = 'workspace_inline_navigation' THEN 1 ELSE 0 END) AS workspace_inline_navigation_events,
                       SUM(CASE WHEN event_type = 'workspace_open_legacy_manual' THEN 1 ELSE 0 END) AS manual_legacy_open_events,
                       SUM(CASE WHEN event_type = 'workspace_open_legacy_blocked' THEN 1 ELSE 0 END) AS workspace_open_legacy_blocked_events,
                       SUM(CASE WHEN event_type = 'workspace_rollout_packet_viewed' THEN 1 ELSE 0 END) AS workspace_rollout_packet_viewed_events,
                       SUM(CASE WHEN event_type = 'workspace_rollout_review_confirmed' THEN 1 ELSE 0 END) AS workspace_rollout_review_confirmed_events,
                       SUM(CASE WHEN event_type = 'workspace_rollout_review_decision_go' THEN 1 ELSE 0 END) AS workspace_rollout_review_decision_go_events,
                       SUM(CASE WHEN event_type = 'workspace_rollout_review_decision_hold' THEN 1 ELSE 0 END) AS workspace_rollout_review_decision_hold_events,
                       SUM(CASE WHEN event_type = 'workspace_rollout_review_decision_rollback' THEN 1 ELSE 0 END) AS workspace_rollout_review_decision_rollback_events,
                       SUM(CASE WHEN event_type = 'workspace_rollout_review_incident_followup_linked' THEN 1 ELSE 0 END) AS workspace_rollout_review_incident_followup_linked_events,
                       SUM(CASE WHEN event_type = 'workspace_sla_policy_review_updated' THEN 1 ELSE 0 END) AS workspace_sla_policy_review_updated_events,
                       SUM(CASE WHEN event_type = 'workspace_macro_governance_review_updated' THEN 1 ELSE 0 END) AS workspace_macro_governance_review_updated_events,
                       SUM(CASE WHEN event_type = 'workspace_macro_external_catalog_policy_updated' THEN 1 ELSE 0 END) AS workspace_macro_external_catalog_policy_updated_events,
                       SUM(CASE WHEN event_type = 'workspace_macro_deprecation_policy_updated' THEN 1 ELSE 0 END) AS workspace_macro_deprecation_policy_updated_events,
                       SUM(CASE WHEN event_type = 'workspace_legacy_usage_policy_updated' THEN 1 ELSE 0 END) AS workspace_legacy_usage_policy_updated_events,
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
                item.put("context_attribute_policy_gap_events", rs.getLong("context_attribute_policy_gap_events"));
                item.put("context_block_gap_events", rs.getLong("context_block_gap_events"));
                item.put("context_contract_gap_events", rs.getLong("context_contract_gap_events"));
                item.put("context_sources_expanded_events", rs.getLong("context_sources_expanded_events"));
                item.put("context_attribute_policy_expanded_events", rs.getLong("context_attribute_policy_expanded_events"));
                item.put("context_extra_attributes_expanded_events", rs.getLong("context_extra_attributes_expanded_events"));
                item.put("workspace_sla_policy_gap_events", rs.getLong("workspace_sla_policy_gap_events"));
                item.put("workspace_parity_gap_events", rs.getLong("workspace_parity_gap_events"));
                item.put("workspace_inline_navigation_events", rs.getLong("workspace_inline_navigation_events"));
                item.put("manual_legacy_open_events", rs.getLong("manual_legacy_open_events"));
                item.put("workspace_open_legacy_blocked_events", rs.getLong("workspace_open_legacy_blocked_events"));
                item.put("workspace_rollout_packet_viewed_events", rs.getLong("workspace_rollout_packet_viewed_events"));
                item.put("workspace_rollout_review_confirmed_events", rs.getLong("workspace_rollout_review_confirmed_events"));
                item.put("workspace_rollout_review_decision_go_events", rs.getLong("workspace_rollout_review_decision_go_events"));
                item.put("workspace_rollout_review_decision_hold_events", rs.getLong("workspace_rollout_review_decision_hold_events"));
                item.put("workspace_rollout_review_decision_rollback_events", rs.getLong("workspace_rollout_review_decision_rollback_events"));
                item.put("workspace_rollout_review_incident_followup_linked_events", rs.getLong("workspace_rollout_review_incident_followup_linked_events"));
                item.put("workspace_sla_policy_review_updated_events", rs.getLong("workspace_sla_policy_review_updated_events"));
                item.put("workspace_macro_governance_review_updated_events", rs.getLong("workspace_macro_governance_review_updated_events"));
                item.put("workspace_macro_external_catalog_policy_updated_events", rs.getLong("workspace_macro_external_catalog_policy_updated_events"));
                item.put("workspace_macro_deprecation_policy_updated_events", rs.getLong("workspace_macro_deprecation_policy_updated_events"));
                item.put("workspace_legacy_usage_policy_updated_events", rs.getLong("workspace_legacy_usage_policy_updated_events"));
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
            log.warn("Unable to load workspace telemetry summary: {}", summarizeDataAccessException(ex));
            return List.of();
        }
    }

    private List<Map<String, Object>> loadWorkspaceEventReasonBreakdown(String eventType,
                                                                        Instant windowStart,
                                                                        Instant windowEnd,
                                                                        String experimentName,
                                                                        int limit) {
        if (!StringUtils.hasText(eventType)) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 20));
        String filterExperiment = trimOrNull(experimentName);
        String sql = """
                SELECT LOWER(TRIM(COALESCE(reason, ''))) AS reason,
                       COUNT(*) AS events
                  FROM workspace_telemetry_audit
                 WHERE created_at >= ?
                   AND created_at < ?
                   AND event_type = ?
                   AND (? IS NULL OR experiment_name = ?)
                 GROUP BY LOWER(TRIM(COALESCE(reason, '')))
                 ORDER BY events DESC, reason ASC
                 LIMIT ?
                """;
        try {
            Timestamp cutoffStart = Timestamp.from(windowStart);
            Timestamp cutoffEnd = Timestamp.from(windowEnd);
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                String reason = normalizeNullString(rs.getString("reason"));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("reason", StringUtils.hasText(reason) ? reason : "unspecified");
                row.put("events", rs.getLong("events"));
                return row;
            }, cutoffStart, cutoffEnd, eventType.trim(), filterExperiment, filterExperiment, safeLimit);
        } catch (DataAccessException ex) {
            log.warn("Unable to load workspace reason breakdown for {}: {}", eventType, summarizeDataAccessException(ex));
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

    private Map<String, Object> buildWorkspaceGapBreakdown(Instant windowStart, Instant windowEnd, String experimentName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("profile", loadWorkspaceGapBreakdownRows(windowStart, windowEnd, experimentName, "workspace_context_profile_gap"));
        payload.put("source", loadWorkspaceGapBreakdownRows(windowStart, windowEnd, experimentName, "workspace_context_source_gap"));
        payload.put("attribute_policy", loadWorkspaceGapBreakdownRows(windowStart, windowEnd, experimentName, "workspace_context_attribute_policy_gap"));
        payload.put("block", loadWorkspaceGapBreakdownRows(windowStart, windowEnd, experimentName, "workspace_context_block_gap"));
        payload.put("contract", loadWorkspaceGapBreakdownRows(windowStart, windowEnd, experimentName, "workspace_context_contract_gap"));
        payload.put("sla_policy", loadWorkspaceGapBreakdownRows(windowStart, windowEnd, experimentName, "workspace_sla_policy_gap"));
        payload.put("parity", loadWorkspaceGapBreakdownRows(windowStart, windowEnd, experimentName, "workspace_parity_gap"));
        return payload;
    }

    private List<Map<String, Object>> loadWorkspaceGapBreakdownRows(Instant windowStart,
                                                                    Instant windowEnd,
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
            log.warn("Unable to load workspace gap breakdown for {}: {}", eventType, summarizeDataAccessException(ex));
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

    static String summarizeDataAccessException(DataAccessException ex) {
        Throwable mostSpecificCause = ex.getMostSpecificCause();
        if (mostSpecificCause != null && StringUtils.hasText(mostSpecificCause.getMessage())) {
            return singleLine(mostSpecificCause.getMessage());
        }
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return ex.getClass().getSimpleName();
        }
        int sqlIndex = message.indexOf(" for SQL [");
        if (sqlIndex >= 0) {
            message = message.substring(0, sqlIndex);
        }
        return singleLine(message);
    }

    private static String singleLine(String value) {
        return value.replaceAll("\\s+", " ").trim();
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
            log.warn("Unable to inspect {} columns: {}", tableName, summarizeDataAccessException(ex));
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
            log.warn("Unable to load categories for ticket {}: {}", ticketId, summarizeDataAccessException(ex));
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
            log.warn("Unable to set categories for ticket {}: {}", ticketId, summarizeDataAccessException(ex));
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
            log.warn("Unable to resolve ticket {}: {}", ticketId, summarizeDataAccessException(ex));
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
            log.warn("Unable to ensure pending feedback request for ticket {}: {}", ticketId, summarizeDataAccessException(ex));
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
            log.warn("Unable to reopen ticket {}: {}", ticketId, summarizeDataAccessException(ex));
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
