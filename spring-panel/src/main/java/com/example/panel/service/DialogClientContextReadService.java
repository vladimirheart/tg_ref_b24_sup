package com.example.panel.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DialogClientContextReadService {

    private static final Logger log = LoggerFactory.getLogger(DialogClientContextReadService.class);

    private final JdbcTemplate jdbcTemplate;

    public DialogClientContextReadService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
            log.warn("Unable to load client dialog history for user {}: {}", userId, DialogService.summarizeDataAccessException(ex));
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
            log.warn("Unable to load client profile enrichment for user {}: {}", userId, DialogService.summarizeDataAccessException(ex));
            return Map.of();
        }
    }

    public Map<String, Object> loadDialogProfileMatchCandidates(Map<String, String> incomingValues, int perFieldLimit) {
        if (incomingValues == null || incomingValues.isEmpty()) {
            return Map.of(
                    "enabled", true,
                    "fields", List.of(),
                    "summary", "no_incoming_values"
            );
        }
        int safeLimit = Math.max(1, Math.min(perFieldLimit, 8));
        List<Map<String, Object>> fields = new ArrayList<>();
        int fieldsWithCandidates = 0;
        for (Map.Entry<String, String> entry : incomingValues.entrySet()) {
            String field = normalizeFieldKey(entry.getKey());
            String incoming = trimOrNull(entry.getValue());
            if (field == null || incoming == null) {
                continue;
            }
            String paramType = mapFieldToParameterType(field);
            if (paramType == null) {
                continue;
            }
            List<Map<String, Object>> candidates = querySettingsParameterMatches(paramType, incoming, safeLimit);
            if (!candidates.isEmpty()) {
                fieldsWithCandidates++;
            }
            Map<String, Object> fieldPayload = new LinkedHashMap<>();
            fieldPayload.put("field", field);
            fieldPayload.put("param_type", paramType);
            fieldPayload.put("incoming_value", incoming);
            fieldPayload.put("label", resolveFieldLabel(field));
            fieldPayload.put("candidates", candidates);
            fieldPayload.put("has_candidates", !candidates.isEmpty());
            fieldPayload.put("needs_review", !candidates.isEmpty() && !Boolean.TRUE.equals(candidates.get(0).get("exact_match")));
            fields.add(fieldPayload);
        }
        return Map.of(
                "enabled", true,
                "source", "settings_parameters",
                "summary", fieldsWithCandidates > 0 ? "review_required" : "no_matches",
                "fields", fields,
                "has_any_candidates", fieldsWithCandidates > 0
        );
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
                               AND (lower(COALESCE(sender, '')) IN ('operator', 'support', 'admin', 'system', 'ai_agent')
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
            log.warn("Unable to load related events with audit trail for ticket {}: {}. Fallback to legacy events.", ticketId, DialogService.summarizeDataAccessException(ex));
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
                                   AND (lower(COALESCE(sender, '')) IN ('operator', 'support', 'admin', 'system', 'ai_agent')
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
                log.warn("Unable to load related events for ticket {}: {}", ticketId, DialogService.summarizeDataAccessException(fallbackEx));
                return List.of();
            }
        }
    }

    private List<Map<String, Object>> querySettingsParameterMatches(String paramType, String incomingValue, int limit) {
        String normalizedIncoming = incomingValue.trim().toLowerCase(Locale.ROOT);
        try {
            return jdbcTemplate.query(
                    """
                    SELECT id, value, state
                      FROM settings_parameters
                     WHERE is_deleted = 0
                       AND param_type = ?
                       AND value IS NOT NULL
                       AND trim(value) <> ''
                     ORDER BY
                       CASE
                         WHEN lower(trim(value)) = ? THEN 0
                         WHEN lower(trim(value)) LIKE ? THEN 1
                         WHEN lower(trim(value)) LIKE ? THEN 2
                         ELSE 3
                       END,
                       value
                     LIMIT ?
                    """,
                    (rs, rowNum) -> {
                        String value = rs.getString("value");
                        String normalizedValue = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
                        boolean exactMatch = normalizedIncoming.equals(normalizedValue);
                        boolean prefixMatch = !exactMatch && normalizedValue.startsWith(normalizedIncoming);
                        String matchType = exactMatch ? "exact" : (prefixMatch ? "prefix" : "contains");
                        double confidence = exactMatch ? 1.0d : (prefixMatch ? 0.82d : 0.64d);
                        Map<String, Object> candidate = new LinkedHashMap<>();
                        candidate.put("id", rs.getLong("id"));
                        candidate.put("value", value);
                        candidate.put("state", rs.getString("state"));
                        candidate.put("match_type", matchType);
                        candidate.put("exact_match", exactMatch);
                        candidate.put("confidence", confidence);
                        return candidate;
                    },
                    paramType,
                    normalizedIncoming,
                    normalizedIncoming + "%",
                    "%" + normalizedIncoming + "%",
                    limit
            );
        } catch (DataAccessException ex) {
            log.debug("Unable to resolve mapping candidates for paramType={} value={}: {}", paramType, incomingValue, DialogService.summarizeDataAccessException(ex));
            return List.of();
        }
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

    private String normalizeFieldKey(String value) {
        String normalized = trimOrNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String mapFieldToParameterType(String field) {
        return switch (field) {
            case "business" -> "business";
            case "location", "location_name", "department" -> "department";
            case "city" -> "city";
            case "country" -> "country";
            default -> null;
        };
    }

    private String resolveFieldLabel(String field) {
        return switch (field) {
            case "business" -> "Бизнес";
            case "location", "location_name", "department" -> "Локация";
            case "city" -> "Город";
            case "country" -> "Страна";
            default -> field;
        };
    }

    private String trimOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
