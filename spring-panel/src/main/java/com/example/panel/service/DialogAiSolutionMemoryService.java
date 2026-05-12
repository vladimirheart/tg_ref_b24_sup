package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DialogAiSolutionMemoryService {

    private final JdbcTemplate jdbcTemplate;
    private final DialogAiAssistantPersistenceService persistenceService;

    public DialogAiSolutionMemoryService(JdbcTemplate jdbcTemplate,
                                         DialogAiAssistantPersistenceService persistenceService) {
        this.jdbcTemplate = jdbcTemplate;
        this.persistenceService = persistenceService;
    }

    public List<Map<String, Object>> loadSolutionMemory(Integer limit, String query) {
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : 100, 500));
        String searchQuery = persistenceService.trim(query);
        try {
            if (searchQuery == null) {
                return jdbcTemplate.queryForList(
                        """
                        SELECT query_key, query_text, solution_text, pending_solution_text, review_required,
                               times_used, times_confirmed, times_corrected, last_operator, last_ticket_id,
                               status, trust_level, intent_key, slot_signature, scope_channel, scope_business, scope_location,
                               safety_level, source_type, last_verified_at, expires_at, verified_by,
                               created_at, updated_at
                          FROM ai_agent_solution_memory
                         ORDER BY COALESCE(updated_at, created_at) DESC
                         LIMIT ?
                        """,
                        safeLimit
                );
            }
            String like = "%" + searchQuery.toLowerCase(Locale.ROOT) + "%";
            return jdbcTemplate.queryForList(
                    """
                    SELECT query_key, query_text, solution_text, pending_solution_text, review_required,
                           times_used, times_confirmed, times_corrected, last_operator, last_ticket_id,
                           status, trust_level, intent_key, slot_signature, scope_channel, scope_business, scope_location,
                           safety_level, source_type, last_verified_at, expires_at, verified_by,
                           created_at, updated_at
                      FROM ai_agent_solution_memory
                     WHERE lower(COALESCE(query_text,'')) LIKE ?
                        OR lower(COALESCE(solution_text,'')) LIKE ?
                     ORDER BY COALESCE(updated_at, created_at) DESC
                     LIMIT ?
                    """,
                    like,
                    like,
                    safeLimit
            );
        } catch (Exception ex) {
            return List.of();
        }
    }

    public boolean updateSolutionMemory(String queryKey,
                                        String queryText,
                                        String solutionText,
                                        Boolean reviewRequired,
                                        String operator) {
        String key = persistenceService.trim(queryKey);
        String query = persistenceService.trim(queryText);
        String solution = persistenceService.trim(solutionText);
        if (key == null || query == null || solution == null) {
            return false;
        }
        boolean requireReview = reviewRequired != null && reviewRequired;
        try {
            Map<String, Object> before = persistenceService.loadSolutionMemoryByKey(key);
            if (before == null) {
                return false;
            }
            int updated = jdbcTemplate.update(
                    """
                    UPDATE ai_agent_solution_memory
                       SET query_text = ?,
                           solution_text = ?,
                           review_required = ?,
                           pending_solution_text = CASE WHEN ? = 1 THEN pending_solution_text ELSE NULL END,
                           last_operator = ?,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE query_key = ?
                    """,
                    persistenceService.cut(query, 600),
                    persistenceService.cut(solution, 2000),
                    requireReview ? 1 : 0,
                    requireReview ? 1 : 0,
                    persistenceService.trim(operator),
                    key
            );
            if (updated > 0) {
                persistenceService.applyMemoryGovernance(
                        key,
                        requireReview ? "draft" : "approved",
                        requireReview ? "low" : "medium",
                        null,
                        null,
                        null,
                        "normal",
                        "operator",
                        !requireReview,
                        persistenceService.trim(operator)
                );
                persistenceService.insertSolutionMemoryHistory(
                        key,
                        persistenceService.trim(operator),
                        "manual",
                        "update",
                        persistenceService.safe(before.get("query_text")),
                        persistenceService.safe(before.get("solution_text")),
                        persistenceService.isTrue(before.get("review_required")),
                        persistenceService.cut(query, 600),
                        persistenceService.cut(solution, 2000),
                        requireReview,
                        "manual_edit"
                );
                persistenceService.syncFromMemory(key);
                return true;
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean deleteSolutionMemory(String queryKey, String operator) {
        String key = persistenceService.trim(queryKey);
        if (key == null) {
            return false;
        }
        try {
            Map<String, Object> before = persistenceService.loadSolutionMemoryByKey(key);
            if (before == null) {
                return false;
            }
            int deleted = jdbcTemplate.update("DELETE FROM ai_agent_solution_memory WHERE query_key = ?", key);
            if (deleted <= 0) {
                return false;
            }
            persistenceService.forgetMemory(key);
            persistenceService.insertSolutionMemoryHistory(
                    key,
                    persistenceService.trim(operator),
                    "manual",
                    "delete",
                    persistenceService.safe(before.get("query_text")),
                    persistenceService.safe(before.get("solution_text")),
                    persistenceService.isTrue(before.get("review_required")),
                    null,
                    null,
                    false,
                    "manual_delete"
            );
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public List<Map<String, Object>> loadSolutionMemoryHistory(String queryKey, Integer limit) {
        String key = persistenceService.trim(queryKey);
        if (key == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : 30, 200));
        try {
            return jdbcTemplate.queryForList(
                    """
                    SELECT id, query_key, changed_by, change_source, change_action,
                           old_query_text, old_solution_text, old_review_required,
                           new_query_text, new_solution_text, new_review_required,
                           note, created_at
                      FROM ai_agent_solution_memory_history
                     WHERE query_key = ?
                     ORDER BY id DESC
                     LIMIT ?
                    """,
                    key,
                    safeLimit
            );
        } catch (Exception ex) {
            return List.of();
        }
    }

    public boolean rollbackSolutionMemory(String queryKey, Long historyId, String operator) {
        String key = persistenceService.trim(queryKey);
        if (key == null || historyId == null || historyId <= 0) {
            return false;
        }
        try {
            Map<String, Object> before = persistenceService.loadSolutionMemoryByKey(key);
            if (before == null) {
                return false;
            }
            Map<String, Object> history = jdbcTemplate.query(
                    """
                    SELECT old_query_text, old_solution_text, old_review_required
                      FROM ai_agent_solution_memory_history
                     WHERE id = ? AND query_key = ?
                     LIMIT 1
                    """,
                    rs -> rs.next() ? Map.of(
                            "old_query_text", persistenceService.trim(rs.getString("old_query_text")),
                            "old_solution_text", persistenceService.trim(rs.getString("old_solution_text")),
                            "old_review_required", rs.getInt("old_review_required")) : null,
                    historyId,
                    key
            );
            if (history == null) {
                return false;
            }
            String rollbackQuery = persistenceService.trim(persistenceService.safe(history.get("old_query_text")));
            String rollbackSolution = persistenceService.trim(persistenceService.safe(history.get("old_solution_text")));
            boolean rollbackReview = persistenceService.isTrue(history.get("old_review_required"));
            if (rollbackQuery == null || rollbackSolution == null) {
                return false;
            }
            int updated = jdbcTemplate.update(
                    """
                    UPDATE ai_agent_solution_memory
                       SET query_text = ?,
                           solution_text = ?,
                           review_required = ?,
                           pending_solution_text = NULL,
                           last_operator = ?,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE query_key = ?
                    """,
                    persistenceService.cut(rollbackQuery, 600),
                    persistenceService.cut(rollbackSolution, 2000),
                    rollbackReview ? 1 : 0,
                    persistenceService.trim(operator),
                    key
            );
            if (updated > 0) {
                persistenceService.applyMemoryGovernance(
                        key,
                        rollbackReview ? "draft" : "approved",
                        rollbackReview ? "low" : "medium",
                        null,
                        null,
                        null,
                        "normal",
                        "operator",
                        !rollbackReview,
                        persistenceService.trim(operator)
                );
                persistenceService.insertSolutionMemoryHistory(
                        key,
                        persistenceService.trim(operator),
                        "manual",
                        "rollback",
                        persistenceService.safe(before.get("query_text")),
                        persistenceService.safe(before.get("solution_text")),
                        persistenceService.isTrue(before.get("review_required")),
                        persistenceService.cut(rollbackQuery, 600),
                        persistenceService.cut(rollbackSolution, 2000),
                        rollbackReview,
                        "rollback_to_history_id=" + historyId
                );
                persistenceService.syncFromMemory(key);
                return true;
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    public void markMemoryUsage(String queryKey) {
        persistenceService.markMemoryUsage(queryKey);
    }
}
