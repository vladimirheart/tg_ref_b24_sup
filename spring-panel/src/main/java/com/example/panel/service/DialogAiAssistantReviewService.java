package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DialogAiAssistantReviewService {

    private final JdbcTemplate jdbcTemplate;
    private final DialogAiAssistantPersistenceService persistenceService;

    public DialogAiAssistantReviewService(JdbcTemplate jdbcTemplate,
                                          DialogAiAssistantPersistenceService persistenceService) {
        this.jdbcTemplate = jdbcTemplate;
        this.persistenceService = persistenceService;
    }

    public Map<String, Object> loadPendingReview(String ticketId) {
        String ticket = persistenceService.trim(ticketId);
        if (ticket == null) {
            return Map.of("pending", false);
        }
        try {
            Map<String, Object> row = loadPendingReviewRowByTicket(ticket);
            if (row == null) {
                return Map.of("pending", false);
            }
            String key = persistenceService.trim(persistenceService.safe(row.get("query_key")));
            if (key == null) {
                return Map.of("pending", false);
            }
            List<Map<String, Object>> problemCandidates = loadReviewMessageCandidates(ticket, false, 20);
            List<Map<String, Object>> solutionCandidates = loadReviewMessageCandidates(ticket, true, 20);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("pending", true);
            payload.put("ticket_id", ticket);
            payload.put("query_key", key);
            payload.put("query_text", persistenceService.safe(row.get("query_text")));
            payload.put("current_solution", persistenceService.safe(row.get("solution_text")));
            payload.put("pending_solution", persistenceService.safe(row.get("pending_solution_text")));
            payload.put("updated_at", persistenceService.safe(row.get("updated_at")));
            payload.put("problem_message_candidates", problemCandidates);
            payload.put("solution_message_candidates", solutionCandidates);
            payload.put("selected_problem_message_id", resolveReviewMessageSelection(problemCandidates, persistenceService.safe(row.get("query_text"))));
            payload.put("selected_solution_message_id", resolveReviewMessageSelection(solutionCandidates, persistenceService.safe(row.get("pending_solution_text"))));
            return payload;
        } catch (Exception ex) {
            return Map.of("pending", false);
        }
    }

    public boolean approvePendingReview(String ticketId, String operator) {
        return approvePendingReview(ticketId, operator, null, null);
    }

    public boolean approvePendingReview(String ticketId, String operator, Long clientMessageId, Long operatorMessageId) {
        String ticket = persistenceService.trim(ticketId);
        if (ticket == null) {
            return false;
        }
        try {
            Map<String, Object> reviewRow = loadPendingReviewRowByTicket(ticket);
            if (reviewRow == null) {
                return false;
            }
            String sourceKey = persistenceService.trim(persistenceService.safe(reviewRow.get("query_key")));
            if (sourceKey == null) {
                return false;
            }

            String selectedClientMessage = loadReviewMessageText(ticket, clientMessageId, false);
            String selectedOperatorMessage = loadReviewMessageText(ticket, operatorMessageId, true);

            String resolvedQueryText = persistenceService.trim(selectedClientMessage);
            if (resolvedQueryText == null) {
                resolvedQueryText = persistenceService.trim(persistenceService.safe(reviewRow.get("query_text")));
            }
            if (resolvedQueryText == null) {
                resolvedQueryText = persistenceService.loadLastClientMessage(ticket);
            }

            String resolvedSolutionText = persistenceService.trim(selectedOperatorMessage);
            if (resolvedSolutionText == null) {
                resolvedSolutionText = persistenceService.trim(persistenceService.safe(reviewRow.get("pending_solution_text")));
            }
            if (resolvedQueryText == null || resolvedSolutionText == null) {
                return false;
            }

            String targetKey = persistenceService.buildKey(resolvedQueryText);
            String safeOperator = persistenceService.trim(operator);
            int sourceUpdated;
            if (targetKey.equals(sourceKey)) {
                sourceUpdated = jdbcTemplate.update(
                        """
                        UPDATE ai_agent_solution_memory
                           SET query_text = ?,
                               solution_text = ?,
                               pending_solution_text = NULL,
                               review_required = 0,
                               times_confirmed = COALESCE(times_confirmed,0) + 1,
                               last_operator = ?,
                               last_ticket_id = ?,
                               last_client_message = ?,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE query_key = ?
                           AND COALESCE(review_required,0) = 1
                           AND trim(COALESCE(pending_solution_text,'')) <> ''
                        """,
                        persistenceService.cut(resolvedQueryText, 600),
                        persistenceService.cut(resolvedSolutionText, 2000),
                        safeOperator,
                        ticket,
                        persistenceService.cut(resolvedQueryText, 600),
                        sourceKey
                );
                if (sourceUpdated > 0) {
                    persistenceService.applyMemoryGovernance(
                            sourceKey,
                            "approved",
                            "medium",
                            null,
                            null,
                            null,
                            "normal",
                            "operator",
                            true,
                            safeOperator
                    );
                }
            } else {
                int targetUpdated = jdbcTemplate.update(
                        """
                        UPDATE ai_agent_solution_memory
                           SET query_text = ?,
                               solution_text = ?,
                               review_required = 0,
                               pending_solution_text = NULL,
                               times_confirmed = COALESCE(times_confirmed,0) + 1,
                               last_operator = ?,
                               last_ticket_id = ?,
                               last_client_message = ?,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE query_key = ?
                        """,
                        persistenceService.cut(resolvedQueryText, 600),
                        persistenceService.cut(resolvedSolutionText, 2000),
                        safeOperator,
                        ticket,
                        persistenceService.cut(resolvedQueryText, 600),
                        targetKey
                );
                if (targetUpdated == 0) {
                    jdbcTemplate.update(
                            """
                            INSERT INTO ai_agent_solution_memory(
                                query_key, query_text, solution_text, source,
                                times_used, times_confirmed, times_corrected,
                                review_required, pending_solution_text,
                                last_operator, last_ticket_id, last_client_message,
                                created_at, updated_at
                            ) VALUES (?, ?, ?, 'operator', 0, 1, 0, 0, NULL, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """,
                            targetKey,
                            persistenceService.cut(resolvedQueryText, 600),
                            persistenceService.cut(resolvedSolutionText, 2000),
                            safeOperator,
                            ticket,
                            persistenceService.cut(resolvedQueryText, 600)
                    );
                }
                persistenceService.applyMemoryGovernance(
                        targetKey,
                        "approved",
                        "medium",
                        null,
                        null,
                        null,
                        "normal",
                        "operator",
                        true,
                        safeOperator
                );
                sourceUpdated = jdbcTemplate.update(
                        """
                        UPDATE ai_agent_solution_memory
                           SET pending_solution_text = NULL,
                               review_required = 0,
                               last_operator = ?,
                               updated_at = CURRENT_TIMESTAMP
                         WHERE query_key = ?
                           AND COALESCE(review_required,0) = 1
                        """,
                        safeOperator,
                        sourceKey
                );
                if (sourceUpdated > 0) {
                    persistenceService.applyMemoryGovernance(
                            sourceKey,
                            "deprecated",
                            "low",
                            null,
                            null,
                            null,
                            "normal",
                            "operator",
                            false,
                            safeOperator
                    );
                }
            }
            if (sourceUpdated > 0) {
                persistenceService.syncFromMemory(targetKey);
                if (!targetKey.equals(sourceKey)) {
                    persistenceService.syncFromMemory(sourceKey);
                }
                persistenceService.clearProcessing(ticket, "operator_correction_approved", null);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("query_key", targetKey);
                payload.put("source_query_key", sourceKey);
                payload.put("message_mapping_updated", !targetKey.equals(sourceKey));
                if (clientMessageId != null && clientMessageId > 0) {
                    payload.put("client_message_id", clientMessageId);
                }
                if (operatorMessageId != null && operatorMessageId > 0) {
                    payload.put("operator_message_id", operatorMessageId);
                }
                persistenceService.recordAiEvent(ticket, "ai_agent_correction_approved", safeOperator, "review", "approved", null, null, null, payload);
                return true;
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean rejectPendingReview(String ticketId, String operator) {
        String ticket = persistenceService.trim(ticketId);
        if (ticket == null) {
            return false;
        }
        try {
            Map<String, Object> reviewRow = loadPendingReviewRowByTicket(ticket);
            if (reviewRow == null) {
                return false;
            }
            String key = persistenceService.trim(persistenceService.safe(reviewRow.get("query_key")));
            if (key == null) {
                return false;
            }
            int updated = jdbcTemplate.update(
                    "UPDATE ai_agent_solution_memory SET pending_solution_text = NULL, review_required = 0, last_operator = ?, updated_at = CURRENT_TIMESTAMP WHERE query_key = ? AND COALESCE(review_required,0) = 1",
                    persistenceService.trim(operator),
                    key
            );
            if (updated > 0) {
                persistenceService.applyMemoryGovernance(
                        key,
                        "rejected",
                        "low",
                        null,
                        null,
                        null,
                        "normal",
                        "operator",
                        false,
                        persistenceService.trim(operator)
                );
                persistenceService.syncFromMemory(key);
                persistenceService.clearProcessing(ticket, "operator_correction_rejected", null);
                persistenceService.recordAiEvent(ticket, "ai_agent_correction_rejected", persistenceService.trim(operator), "review", "rejected", null, null, null, null);
                return true;
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    public List<Map<String, Object>> loadPendingReviewsQueue(Integer limit) {
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : 25, 200));
        try {
            return jdbcTemplate.queryForList(
                    "SELECT query_key, query_text, solution_text, pending_solution_text, last_ticket_id, updated_at, times_confirmed, times_corrected, status, trust_level, safety_level FROM ai_agent_solution_memory WHERE COALESCE(review_required,0)=1 AND trim(COALESCE(pending_solution_text,''))<>'' ORDER BY COALESCE(updated_at, created_at) DESC LIMIT ?",
                    safeLimit
            );
        } catch (Exception ex) {
            return List.of();
        }
    }

    public boolean approvePendingReviewByKey(String queryKey, String operator) {
        String key = persistenceService.trim(queryKey);
        if (key == null) {
            return false;
        }
        try {
            String ticketId = jdbcTemplate.query(
                    "SELECT last_ticket_id FROM ai_agent_solution_memory WHERE query_key = ? LIMIT 1",
                    rs -> rs.next() ? persistenceService.trim(rs.getString("last_ticket_id")) : null,
                    key
            );
            int updated = jdbcTemplate.update(
                    "UPDATE ai_agent_solution_memory SET solution_text = pending_solution_text, pending_solution_text = NULL, review_required = 0, times_confirmed = COALESCE(times_confirmed,0) + 1, last_operator = ?, updated_at = CURRENT_TIMESTAMP WHERE query_key = ? AND COALESCE(review_required,0)=1 AND trim(COALESCE(pending_solution_text,''))<>''",
                    persistenceService.trim(operator),
                    key
            );
            if (updated > 0) {
                persistenceService.applyMemoryGovernance(
                        key,
                        "approved",
                        "medium",
                        null,
                        null,
                        null,
                        "normal",
                        "operator",
                        true,
                        persistenceService.trim(operator)
                );
                persistenceService.syncFromMemory(key);
            }
            if (updated > 0 && ticketId != null) {
                persistenceService.clearProcessing(ticketId, "operator_correction_approved", null);
                persistenceService.recordAiEvent(ticketId, "ai_agent_correction_approved", persistenceService.trim(operator), "review", "approved", null, null, null, Map.of(
                        "query_key", key
                ));
            }
            return updated > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean rejectPendingReviewByKey(String queryKey, String operator) {
        String key = persistenceService.trim(queryKey);
        if (key == null) {
            return false;
        }
        try {
            String ticketId = jdbcTemplate.query(
                    "SELECT last_ticket_id FROM ai_agent_solution_memory WHERE query_key = ? LIMIT 1",
                    rs -> rs.next() ? persistenceService.trim(rs.getString("last_ticket_id")) : null,
                    key
            );
            int updated = jdbcTemplate.update(
                    "UPDATE ai_agent_solution_memory SET pending_solution_text = NULL, review_required = 0, last_operator = ?, updated_at = CURRENT_TIMESTAMP WHERE query_key = ? AND COALESCE(review_required,0)=1",
                    persistenceService.trim(operator),
                    key
            );
            if (updated > 0) {
                persistenceService.applyMemoryGovernance(
                        key,
                        "rejected",
                        "low",
                        null,
                        null,
                        null,
                        "normal",
                        "operator",
                        false,
                        persistenceService.trim(operator)
                );
                persistenceService.syncFromMemory(key);
            }
            if (updated > 0 && ticketId != null) {
                persistenceService.clearProcessing(ticketId, "operator_correction_rejected", null);
                persistenceService.recordAiEvent(ticketId, "ai_agent_correction_rejected", persistenceService.trim(operator), "review", "rejected", null, null, null, Map.of(
                        "query_key", key
                ));
            }
            return updated > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private Map<String, Object> loadPendingReviewRowByTicket(String ticketId) {
        String ticket = persistenceService.trim(ticketId);
        if (ticket == null) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT query_key, query_text, solution_text, pending_solution_text, updated_at
                  FROM ai_agent_solution_memory
                 WHERE last_ticket_id = ?
                   AND COALESCE(review_required,0) = 1
                   AND trim(COALESCE(pending_solution_text,'')) <> ''
                 ORDER BY COALESCE(updated_at, created_at) DESC
                 LIMIT 1
                """,
                ticket
        );
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        String lastClient = persistenceService.loadLastClientMessage(ticket);
        if (!StringUtils.hasText(lastClient)) {
            return null;
        }
        String key = persistenceService.buildKey(lastClient);
        List<Map<String, Object>> fallbackRows = jdbcTemplate.queryForList(
                """
                SELECT query_key, query_text, solution_text, pending_solution_text, updated_at
                  FROM ai_agent_solution_memory
                 WHERE query_key = ?
                   AND COALESCE(review_required,0) = 1
                   AND trim(COALESCE(pending_solution_text,'')) <> ''
                 LIMIT 1
                """,
                key
        );
        return fallbackRows.isEmpty() ? null : fallbackRows.get(0);
    }

    private List<Map<String, Object>> loadReviewMessageCandidates(String ticketId, boolean operatorMessages, int limit) {
        String ticket = persistenceService.trim(ticketId);
        if (ticket == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String senderFilter = operatorMessages
                ? "IN ('operator','support','admin','system')"
                : "NOT IN ('operator','support','admin','system','ai_agent')";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, sender, message, timestamp FROM chat_history WHERE ticket_id = ? AND lower(COALESCE(sender,'')) " + senderFilter + " AND message IS NOT NULL AND trim(message) <> '' ORDER BY id DESC LIMIT ?",
                    ticket,
                    safeLimit
            );
            List<Map<String, Object>> out = new ArrayList<>();
            for (int i = rows.size() - 1; i >= 0; i--) {
                Map<String, Object> row = rows.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", persistenceService.toLong(row.get("id")));
                item.put("sender", persistenceService.safe(row.get("sender")));
                item.put("text", persistenceService.safe(row.get("message")));
                item.put("timestamp", persistenceService.safe(row.get("timestamp")));
                out.add(item);
            }
            return out;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Long resolveReviewMessageSelection(List<Map<String, Object>> candidates, String targetText) {
        String target = persistenceService.trim(targetText);
        if (target == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (Map<String, Object> candidate : candidates) {
            if (target.equals(persistenceService.trim(persistenceService.safe(candidate.get("text"))))) {
                return persistenceService.toLong(candidate.get("id"));
            }
        }
        return persistenceService.toLong(candidates.get(candidates.size() - 1).get("id"));
    }

    private String loadReviewMessageText(String ticketId, Long messageId, boolean operatorMessage) {
        String ticket = persistenceService.trim(ticketId);
        if (ticket == null || messageId == null || messageId <= 0) {
            return null;
        }
        String senderFilter = operatorMessage
                ? "IN ('operator','support','admin','system')"
                : "NOT IN ('operator','support','admin','system','ai_agent')";
        try {
            return jdbcTemplate.query(
                    "SELECT message FROM chat_history WHERE ticket_id = ? AND id = ? AND lower(COALESCE(sender,'')) " + senderFilter + " AND message IS NOT NULL AND trim(message) <> '' LIMIT 1",
                    rs -> rs.next() ? persistenceService.trim(rs.getString("message")) : null,
                    ticket,
                    messageId
            );
        } catch (Exception ex) {
            return null;
        }
    }
}
