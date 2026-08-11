package com.example.panel.service;

import com.example.panel.model.dialog.DialogChannelStat;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogMyDialogs;
import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.support.JdbcSchemaInspector;
import com.example.panel.support.PanelTimestampSqlSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    private static final DateTimeFormatter REQUEST_NUMBER_ISO_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter REQUEST_NUMBER_DISPLAY_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter REQUEST_NUMBER_LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final JdbcTemplate jdbcTemplate;
    private final JdbcTemplate usersJdbcTemplate;
    private final PanelUserPhotoService panelUserPhotoService;
    private final PanelTimestampSqlSupport timestampSqlSupport;

    public DialogLookupReadService(JdbcTemplate jdbcTemplate,
                                   @Qualifier("usersJdbcTemplate") JdbcTemplate usersJdbcTemplate,
                                   PanelUserPhotoService panelUserPhotoService,
                                   PanelTimestampSqlSupport timestampSqlSupport) {
        this.jdbcTemplate = jdbcTemplate;
        this.usersJdbcTemplate = usersJdbcTemplate;
        this.panelUserPhotoService = panelUserPhotoService;
        this.timestampSqlSupport = timestampSqlSupport;
    }

    private String latestMessageOrderSql(String alias) {
        return timestampSqlSupport.orderByTimestampDesc(alias + ".created_at") + ", " + alias + ".group_msg_id DESC";
    }

    private String latestChatHistoryOrderSql(String alias) {
        return timestampSqlSupport.orderByTimestampDesc(alias + ".timestamp")
                + ", COALESCE(" + alias + ".tg_message_id, 0) DESC, " + alias + ".id DESC";
    }

    private String categoryAggregationSql(String alias) {
        return timestampSqlSupport.stringAggregationExpression(alias + ".category", "', '", alias + ".category");
    }

    private String unreadBoundarySql(String ticketAlias) {
        String lastOperatorReply = """
                (
                    SELECT MAX(op.timestamp)
                      FROM chat_history op
                     WHERE op.ticket_id = %s.ticket_id
                       AND lower(op.sender) IN ('operator', 'support', 'admin', 'system', 'ai_agent')
                )
                """.formatted(ticketAlias);
        return "COALESCE(tr.last_read_at, " + lastOperatorReply + ")";
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
            log.warn("Unable to load dialog summary, returning empty view: {}", DialogDataAccessSupport.summarizeDataAccessException(ex));
            return new DialogSummary(0, 0, 0, List.of());
        }
    }

    public List<DialogListItem> loadDialogs(String currentOperator) {
        try {
            Set<String> feedbackColumns = loadTableColumns("feedbacks");
            boolean feedbackHasTicketId = feedbackColumns.contains("ticket_id");
            boolean feedbackHasId = feedbackColumns.contains("id");
            String feedbackOrderBy = feedbackHasTicketId ? "f.timestamp DESC, f.id DESC" : "f.timestamp DESC";
            String latestMessageOrder = latestMessageOrderSql("m3");
            String latestMessageSnapshotOrder = latestMessageOrderSql("m2");
            String latestHistoryOrder = latestChatHistoryOrderSql("ch");
            String unreadBoundary = unreadBoundarySql("t");
            String categoriesAggregation = categoryAggregationSql("tc");
            String lastActivityExpression = """
                    COALESCE(
                        (
                            SELECT timestamp
                              FROM chat_history ch
                             WHERE ch.ticket_id = t.ticket_id
                             ORDER BY %s
                             LIMIT 1
                        ),
                        COALESCE(m.created_at, t.created_at)
                    )
                    """.formatted(latestHistoryOrder);
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
                                    ORDER BY %s
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
                           m.created_date AS created_date,
                           m.created_time AS created_time,
                           cs.status AS client_status,
                           %s
                           (
                               SELECT sender
                                 FROM chat_history ch
                                WHERE ch.ticket_id = t.ticket_id
                                ORDER BY %s
                                LIMIT 1
                           ) AS last_sender,
                           (
                               SELECT timestamp
                                 FROM chat_history ch
                                WHERE ch.ticket_id = t.ticket_id
                                ORDER BY %s
                                LIMIT 1
                           ) AS last_sender_time,
                           (
                               SELECT %s
                                 FROM ticket_categories tc
                                WHERE tc.ticket_id = t.ticket_id
                           ) AS categories,
                           CASE
                               WHEN tr.responsible IS NULL OR trim(tr.responsible) = '' OR lower(COALESCE(tr.responsible, '')) = lower(?) THEN (
                                   SELECT COUNT(*)
                                     FROM chat_history ch
                                    WHERE ch.ticket_id = t.ticket_id
                                      AND lower(ch.sender) NOT IN ('operator', 'support', 'admin', 'system', 'ai_agent')
                                      AND (%s IS NULL OR ch.timestamp > %s)
                               )
                               ELSE 0
                           END AS unread_count
                      FROM tickets t
                      LEFT JOIN messages m ON m.group_msg_id = (
                          SELECT m2.group_msg_id
                            FROM messages m2
                           WHERE m2.ticket_id = t.ticket_id
                           ORDER BY %s
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
                                    ORDER BY %s
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
                                        ORDER BY %s
                                        LIMIT 1
                                   )
                               )
                           )
                      ORDER BY %s,
                               t.ticket_id DESC
                    """.formatted(
                    latestMessageOrder,
                    ratingSelect,
                    latestHistoryOrder,
                    latestHistoryOrder,
                    categoriesAggregation,
                    unreadBoundary,
                    unreadBoundary,
                    latestMessageSnapshotOrder,
                    latestMessageOrder,
                    latestMessageOrder,
                    timestampSqlSupport.orderByTimestampDesc(lastActivityExpression)
            );
            List<DialogListItem> items = jdbcTemplate.query(sql, (rs, rowNum) -> new DialogListItem(
                    rs.getString("ticket_id"),
                    rs.getString("request_number"),
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
                    resolveCreatedDate(rs.getString("created_date"), rs.getString("created_at")),
                    resolveCreatedTime(rs.getString("created_time"), rs.getString("created_at")),
                    rs.getString("client_status"),
                    rs.getString("last_sender"),
                    rs.getString("last_sender_time"),
                    rs.getObject("unread_count") != null ? rs.getInt("unread_count") : 0,
                    rs.getObject("rating") != null ? rs.getInt("rating") : null,
                    rs.getString("categories"),
                    null,
                    null
            ), currentOperator);
            return assignDailyRequestNumbers(enrichResponsibleProfiles(items));
        } catch (DataAccessException ex) {
            log.warn("Unable to load dialogs, returning empty list: {}", DialogDataAccessSupport.summarizeDataAccessException(ex));
            return List.of();
        }
    }

    public DialogMyDialogs loadMyActiveDialogs(String currentOperator) {
        return groupMyActiveDialogs(loadDialogs(currentOperator), currentOperator);
    }

    public DialogMyDialogs groupMyActiveDialogs(List<DialogListItem> dialogs, String currentOperator) {
        String normalizedOperator = normalizeIdentity(currentOperator);
        if (normalizedOperator == null || dialogs == null || dialogs.isEmpty()) {
            return DialogMyDialogs.empty();
        }
        List<DialogListItem> newUnassigned = new ArrayList<>();
        List<DialogListItem> unanswered = new ArrayList<>();
        List<DialogListItem> inWork = new ArrayList<>();
        for (DialogListItem item : dialogs) {
            if (isClosedDialog(item)) {
                continue;
            }
            if (isNewUnassignedDialog(item)) {
                newUnassigned.add(item);
                continue;
            }
            if (!belongsToCurrentOperator(item, normalizedOperator)) {
                continue;
            }
            int unreadCount = item.unreadCount() != null ? item.unreadCount() : 0;
            if (unreadCount > 0) {
                unanswered.add(item);
                continue;
            }
            inWork.add(item);
        }
        return new DialogMyDialogs(List.copyOf(newUnassigned), List.copyOf(unanswered), List.copyOf(inWork));
    }

    public Optional<DialogListItem> findDialog(String ticketId, String operator) {
        try {
            Set<String> feedbackColumns = loadTableColumns("feedbacks");
            boolean feedbackHasTicketId = feedbackColumns.contains("ticket_id");
            boolean feedbackHasId = feedbackColumns.contains("id");
            String feedbackOrderBy = feedbackHasTicketId ? "f.timestamp DESC, f.id DESC" : "f.timestamp DESC";
            String latestMessageOrder = latestMessageOrderSql("m3");
            String latestMessageSnapshotOrder = latestMessageOrderSql("m2");
            String latestHistoryOrder = latestChatHistoryOrderSql("ch");
            String unreadBoundary = unreadBoundarySql("t");
            String categoriesAggregation = categoryAggregationSql("tc");
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
                                    ORDER BY %s
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
                           m.created_date AS created_date,
                           m.created_time AS created_time,
                           cs.status AS client_status,
                           %s
                           (
                               SELECT sender
                                 FROM chat_history ch
                                WHERE ch.ticket_id = t.ticket_id
                                ORDER BY %s
                                LIMIT 1
                           ) AS last_sender,
                           (
                               SELECT timestamp
                                 FROM chat_history ch
                                WHERE ch.ticket_id = t.ticket_id
                                ORDER BY %s
                                LIMIT 1
                           ) AS last_sender_time,
                           (
                               SELECT %s
                                 FROM ticket_categories tc
                                WHERE tc.ticket_id = t.ticket_id
                           ) AS categories,
                           CASE
                               WHEN tr.responsible IS NULL OR trim(tr.responsible) = '' OR lower(COALESCE(tr.responsible, '')) = lower(?) THEN (
                                   SELECT COUNT(*)
                                     FROM chat_history ch
                                    WHERE ch.ticket_id = t.ticket_id
                                      AND lower(ch.sender) NOT IN ('operator', 'support', 'admin', 'system', 'ai_agent')
                                      AND (%s IS NULL OR ch.timestamp > %s)
                               )
                               ELSE 0
                           END AS unread_count
                      FROM tickets t
                      LEFT JOIN messages m ON m.group_msg_id = (
                          SELECT m2.group_msg_id
                            FROM messages m2
                           WHERE m2.ticket_id = t.ticket_id
                           ORDER BY %s
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
                                    ORDER BY %s
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
                                        ORDER BY %s
                                        LIMIT 1
                                   )
                               )
                           )
                     WHERE t.ticket_id = ?
                    """.formatted(
                    latestMessageOrder,
                    ratingSelect,
                    latestHistoryOrder,
                    latestHistoryOrder,
                    categoriesAggregation,
                    unreadBoundary,
                    unreadBoundary,
                    latestMessageSnapshotOrder,
                    latestMessageOrder,
                    latestMessageOrder
            );
            List<DialogListItem> items = jdbcTemplate.query(sql, (rs, rowNum) -> new DialogListItem(
                    rs.getString("ticket_id"),
                    rs.getString("request_number"),
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
                    resolveCreatedDate(rs.getString("created_date"), rs.getString("created_at")),
                    resolveCreatedTime(rs.getString("created_time"), rs.getString("created_at")),
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
            return enriched.isEmpty() ? Optional.empty() : Optional.of(assignDailyRequestNumber(enriched.get(0)));
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

    private List<DialogListItem> assignDailyRequestNumbers(List<DialogListItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<DialogListItem> ordered = new ArrayList<>(items);
        ordered.sort(Comparator
                .comparing(this::requestNumberSortKey, Comparator.nullsLast(String::compareTo))
                .thenComparing(item -> trimToNull(item.ticketId()), Comparator.nullsLast(String::compareTo)));
        Map<String, Integer> sequenceByDay = new HashMap<>();
        Map<String, String> requestNumberByTicketId = new HashMap<>();
        for (DialogListItem item : ordered) {
            RequestNumberSeed seed = buildRequestNumberSeed(item);
            if (seed == null || !StringUtils.hasText(item.ticketId())) {
                continue;
            }
            int sequence = sequenceByDay.merge(seed.isoDate(), 1, Integer::sum);
            requestNumberByTicketId.put(item.ticketId(), formatRequestNumber(seed.displayDate(), sequence));
        }
        List<DialogListItem> numbered = new ArrayList<>(items.size());
        for (DialogListItem item : items) {
            String requestNumber = requestNumberByTicketId.get(item.ticketId());
            numbered.add(requestNumber != null ? item.withRequestNumber(requestNumber) : item);
        }
        return numbered;
    }

    private DialogListItem assignDailyRequestNumber(DialogListItem item) {
        if (item == null) {
            return null;
        }
        RequestNumberSeed seed = buildRequestNumberSeed(item);
        String ticketId = trimToNull(item.ticketId());
        if (seed == null || ticketId == null) {
            return item;
        }
        String comparableCreatedAt = timestampSqlSupport.normalizeComparableTimestamp(seed.sortKey());
        if (comparableCreatedAt == null) {
            return item;
        }
        String createdAtExpr = comparableTicketCreatedAtSql("t");
        String sql = """
                SELECT COUNT(*)
                  FROM tickets t
                 WHERE %2$s = ?
                   AND (
                        %1$s < ?
                        OR (%1$s = ? AND t.ticket_id <= ?)
                   )
                """.formatted(createdAtExpr, timestampSqlSupport.dateBucketExpression(createdAtExpr));
        Object comparableCreatedAtParam = timestampSqlSupport.isSqliteMode()
                ? comparableCreatedAt
                : timestampSqlSupport.comparableTimestampParam(comparableCreatedAt);
        Long count = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                seed.isoDate(),
                comparableCreatedAtParam,
                comparableCreatedAtParam,
                ticketId
        );
        int sequence = count != null && count > 0 ? count.intValue() : 1;
        return item.withRequestNumber(formatRequestNumber(seed.displayDate(), sequence));
    }

    private RequestNumberSeed buildRequestNumberSeed(DialogListItem item) {
        if (item == null) {
            return null;
        }
        LocalDate date = parseRequestDate(item.createdDate(), item.createdAt());
        String sortKey = requestNumberSortKey(item);
        if (date == null || !StringUtils.hasText(sortKey)) {
            return null;
        }
        return new RequestNumberSeed(
                date.format(REQUEST_NUMBER_ISO_DATE_FORMAT),
                date.format(REQUEST_NUMBER_DISPLAY_DATE_FORMAT),
                sortKey
        );
    }

    private LocalDate parseRequestDate(String createdDate, String createdAt) {
        String normalizedCreatedDate = trimToNull(createdDate);
        if (normalizedCreatedDate != null) {
            LocalDate parsed = parseLocalDate(normalizedCreatedDate);
            if (parsed != null) {
                return parsed;
            }
        }
        String normalizedCreatedAt = trimToNull(createdAt);
        if (normalizedCreatedAt != null && normalizedCreatedAt.length() >= 10) {
            return parseLocalDate(normalizedCreatedAt.substring(0, 10));
        }
        return null;
    }

    private LocalDate parseLocalDate(String value) {
        try {
            return LocalDate.parse(value, REQUEST_NUMBER_ISO_DATE_FORMAT);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(value, REQUEST_NUMBER_LEGACY_DATE_FORMAT);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDate.parse(value, REQUEST_NUMBER_DISPLAY_DATE_FORMAT);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String requestNumberSortKey(DialogListItem item) {
        if (item == null) {
            return null;
        }
        String createdAt = timestampSqlSupport.normalizeComparableTimestamp(item.createdAt());
        if (createdAt != null) {
            return createdAt.length() > 19 ? createdAt.substring(0, 19) : createdAt;
        }
        LocalDate date = parseRequestDate(item.createdDate(), item.createdAt());
        if (date == null) {
            return null;
        }
        String time = trimToNull(item.createdTime());
        String timePart = time != null
                ? (time.length() > 8 ? time.substring(0, 8) : time)
                : "00:00:00";
        return date.format(REQUEST_NUMBER_ISO_DATE_FORMAT) + "T" + timePart;
    }

    private String comparableTicketCreatedAtSql(String ticketAlias) {
        if (timestampSqlSupport.isSqliteMode()) {
            return "replace(" + ticketCreatedAtSql(ticketAlias) + ", ' ', 'T')";
        }
        return ticketCreatedAtSql(ticketAlias);
    }

    private String ticketCreatedAtSql(String ticketAlias) {
        if (!timestampSqlSupport.isSqliteMode()) {
            return """
                    COALESCE(
                        (
                            SELECT MIN(m0.created_at)
                              FROM messages m0
                             WHERE m0.ticket_id = %1$s.ticket_id
                               AND m0.created_at IS NOT NULL
                        ),
                        %1$s.created_at
                    )
                    """.formatted(ticketAlias);
        }
        return """
                COALESCE(
                    (
                        SELECT MIN(substr(m0.created_at, 1, 19))
                          FROM messages m0
                         WHERE m0.ticket_id = %1$s.ticket_id
                           AND m0.created_at IS NOT NULL
                    ),
                    substr(%1$s.created_at, 1, 19)
                )
                """.formatted(ticketAlias);
    }

    private String formatRequestNumber(String displayDate, int sequence) {
        return displayDate + "-" + String.format("%03d", sequence);
    }

    private String resolveCreatedDate(String createdDate, String createdAt) {
        String explicit = trimToNull(createdDate);
        if (explicit != null) {
            return explicit;
        }
        String normalized = timestampSqlSupport.normalizeComparableTimestamp(createdAt);
        return normalized != null && normalized.length() >= 10 ? normalized.substring(0, 10) : null;
    }

    private String resolveCreatedTime(String createdTime, String createdAt) {
        String explicit = trimToNull(createdTime);
        if (explicit != null) {
            return explicit.length() > 8 ? explicit.substring(0, 8) : explicit;
        }
        String normalized = timestampSqlSupport.normalizeComparableTimestamp(createdAt);
        return normalized != null && normalized.length() >= 19 ? normalized.substring(11, 19) : null;
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
            usersJdbcTemplate.query(sql, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                String username = normalizeIdentity(rs.getString("username"));
                if (username == null) {
                    return;
                }
                String displayName = trimToNull(rs.getString("full_name"));
                if (displayName == null) {
                    displayName = trimToNull(rs.getString("username"));
                }
                profiles.put(username, new ResponsibleProfile(displayName, panelUserPhotoService.resolveUrl(rs.getString("photo"))));
            }, identities.toArray());
            return profiles;
        } catch (DataAccessException ex) {
            log.warn("Unable to load responsible profiles for dialog list: {}", DialogDataAccessSupport.summarizeDataAccessException(ex));
            return Map.of();
        }
    }

    private Set<String> loadUsersTableColumns() {
        try {
            return JdbcSchemaInspector.loadColumnNames(usersJdbcTemplate, "users");
        } catch (DataAccessException ex) {
            log.warn("Unable to inspect users table columns: {}", DialogDataAccessSupport.summarizeDataAccessException(ex));
            return Set.of();
        }
    }

    private Set<String> loadTableColumns(String tableName) {
        try {
            return JdbcSchemaInspector.loadColumnNames(jdbcTemplate, tableName);
        } catch (DataAccessException ex) {
            log.warn("Unable to inspect {} columns: {}", tableName, DialogDataAccessSupport.summarizeDataAccessException(ex));
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

    private boolean belongsToCurrentOperator(DialogListItem item, String normalizedOperator) {
        return normalizedOperator != null
                && normalizedOperator.equals(normalizeIdentity(item != null ? item.rawResponsible() : null));
    }

    private boolean isNewUnassignedDialog(DialogListItem item) {
        if (item == null) {
            return false;
        }
        String statusKey = normalizeIdentity(item.statusKey());
        if (normalizeIdentity(item.rawResponsible()) != null) {
            return false;
        }
        return "new".equals(statusKey) || "auto_processing".equals(statusKey);
    }

    private boolean isClosedDialog(DialogListItem item) {
        String statusKey = normalizeIdentity(item != null ? item.statusKey() : null);
        return "closed".equals(statusKey) || "auto_closed".equals(statusKey);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record RequestNumberSeed(String isoDate, String displayDate, String sortKey) {
    }

    private record ResponsibleProfile(String displayName, String avatarUrl) {
    }
}
