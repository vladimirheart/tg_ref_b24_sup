package com.example.panel.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class AiLearningService {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Set<String> STOP = Set.of("и","в","на","не","что","как","для","или","по","из","к","у","о","об","the","a","an","to","of","in","on","for","and","or","is","are","be");

    private final JdbcTemplate jdbcTemplate;
    private final AiPolicyService aiPolicyService;
    private final AiIntentService aiIntentService;

    public AiLearningService(JdbcTemplate jdbcTemplate,
                             AiPolicyService aiPolicyService,
                             AiIntentService aiIntentService) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiPolicyService = aiPolicyService;
        this.aiIntentService = aiIntentService;
    }

    public UpsertResult upsertLearningSolution(String ticketId,
                                               String clientQuestion,
                                               String operatorReply,
                                               String operator,
                                               double differenceThreshold) {
        String question = trim(clientQuestion);
        String reply = trim(operatorReply);
        if (question == null || reply == null) {
            return null;
        }
        AiIntentService.IntentMatch intentMatch = aiIntentService.extract(question);
        String key = buildKey(question);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT solution_text,pending_solution_text,review_required FROM ai_agent_solution_memory WHERE query_key=? LIMIT 1",
                key
        );
        if (rows.isEmpty()) {
            insertDraftRow(key, question, reply, operator, ticketId);
            insertSolutionMemoryHistory(
                    key,
                    trim(operator),
                    "learning",
                    "insert_draft",
                    null,
                    null,
                    false,
                    cut(question, 600),
                    cut(reply, 2000),
                    true,
                    "learned_from_operator_reply_pending_review"
            );
            applyIntentMetadata(key, intentMatch);
            return new UpsertResult(key, "inserted_draft");
        }
        Map<String, Object> existing = rows.get(0);
        String currentSolution = trim(safe(existing.get("solution_text")));
        String pending = trim(safe(existing.get("pending_solution_text")));
        boolean review = isTrue(existing.get("review_required"));
        if (review && pending != null && !isMeaningfullyDifferent(pending, reply, differenceThreshold)) {
            jdbcTemplate.update(
                    "UPDATE ai_agent_solution_memory SET query_text=?,last_operator=?,last_ticket_id=?,last_client_message=?,updated_at=CURRENT_TIMESTAMP WHERE query_key=?",
                    cut(question, 600),
                    trim(operator),
                    trim(ticketId),
                    cut(question, 600),
                    key
            );
            insertSolutionMemoryHistory(
                    key,
                    trim(operator),
                    "learning",
                    "refresh_pending",
                    cut(question, 600),
                    cut(currentSolution, 2000),
                    review,
                    cut(question, 600),
                    cut(pending, 2000),
                    true,
                    "pending_solution_kept"
            );
            applyDraftGovernance(key);
            applyIntentMetadata(key, intentMatch);
            return new UpsertResult(key, "pending_unchanged");
        }
        if (currentSolution != null && !isMeaningfullyDifferent(currentSolution, reply, differenceThreshold)) {
            jdbcTemplate.update(
                    "UPDATE ai_agent_solution_memory SET query_text=?,times_confirmed=COALESCE(times_confirmed,0)+1,last_operator=?,last_ticket_id=?,last_client_message=?,updated_at=CURRENT_TIMESTAMP WHERE query_key=?",
                    cut(question, 600),
                    trim(operator),
                    trim(ticketId),
                    cut(question, 600),
                    key
            );
            insertSolutionMemoryHistory(
                    key,
                    trim(operator),
                    "learning",
                    "confirm_existing",
                    cut(question, 600),
                    cut(currentSolution, 2000),
                    review,
                    cut(question, 600),
                    cut(currentSolution, 2000),
                    review,
                    "operator_confirmed_existing_solution"
            );
            applyIntentMetadata(key, intentMatch);
            return new UpsertResult(key, "confirmed_existing");
        }
        jdbcTemplate.update(
                "UPDATE ai_agent_solution_memory SET query_text=?,review_required=1,pending_solution_text=?,times_corrected=COALESCE(times_corrected,0)+1,last_operator=?,last_ticket_id=?,last_client_message=?,updated_at=CURRENT_TIMESTAMP WHERE query_key=?",
                cut(question, 600),
                cut(reply, 2000),
                trim(operator),
                trim(ticketId),
                cut(question, 600),
                key
        );
        insertSolutionMemoryHistory(
                key,
                trim(operator),
                "learning",
                "pending_review_requested",
                cut(question, 600),
                cut(currentSolution, 2000),
                review,
                cut(question, 600),
                cut(reply, 2000),
                true,
                "operator_reply_requires_review"
        );
        applyDraftGovernance(key);
        applyIntentMetadata(key, intentMatch);
        return new UpsertResult(key, "pending_review_requested");
    }

    private void insertDraftRow(String key,
                                String question,
                                String reply,
                                String operator,
                                String ticketId) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_agent_solution_memory(
                        query_key, query_text, solution_text, source, times_used, times_confirmed, times_corrected,
                        review_required, pending_solution_text, last_operator, last_ticket_id, last_client_message,
                        status, trust_level, source_type, safety_level, created_at, updated_at
                    ) VALUES (?,?,?,?,0,0,0,1,?,?,?,?,?,?,?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    key,
                    cut(question, 600),
                    null,
                    "operator",
                    cut(reply, 2000),
                    trim(operator),
                    trim(ticketId),
                    cut(question, 600),
                    "draft",
                    "low",
                    "operator",
                    "normal"
            );
            return;
        } catch (Exception ignored) {
            // Fall through to old schema insert.
        }
        jdbcTemplate.update(
                "INSERT INTO ai_agent_solution_memory(query_key,query_text,solution_text,source,times_used,times_confirmed,times_corrected,review_required,pending_solution_text,last_operator,last_ticket_id,last_client_message,created_at,updated_at) VALUES (?,?,?,?,0,0,0,1,?,?,?, ?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                key,
                cut(question, 600),
                null,
                "operator",
                cut(reply, 2000),
                trim(operator),
                trim(ticketId),
                cut(question, 600)
        );
        applyDraftGovernance(key);
    }

    private void applyDraftGovernance(String key) {
        String normalizedKey = trim(key);
        if (normalizedKey == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    """
                    UPDATE ai_agent_solution_memory
                       SET status = ?,
                           trust_level = ?,
                           source_type = ?,
                           safety_level = ?,
                           verified_by = NULL,
                           last_verified_at = NULL,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE query_key = ?
                    """,
                    aiPolicyService.normalizeStatus("draft", "draft"),
                    aiPolicyService.normalizeTrustLevel("low", "low"),
                    aiPolicyService.normalizeSourceType("memory", "operator"),
                    aiPolicyService.normalizeSafetyLevel("normal", "normal"),
                    normalizedKey
            );
        } catch (Exception ignored) {
            // Backward compatibility for DB without governance columns.
        }
    }

    private void applyIntentMetadata(String key, AiIntentService.IntentMatch intentMatch) {
        String normalizedKey = trim(key);
        if (normalizedKey == null || intentMatch == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    """
                    UPDATE ai_agent_solution_memory
                       SET intent_key = ?,
                           slot_signature = ?,
                           slots_json = ?,
                           scope_channel = COALESCE(?, scope_channel),
                           scope_business = COALESCE(?, scope_business),
                           scope_location = COALESCE(?, scope_location),
                           updated_at = CURRENT_TIMESTAMP
                     WHERE query_key = ?
                    """,
                    cut(trim(intentMatch.intentKey()), 120),
                    cut(trim(intentMatch.slotSignature()), 300),
                    cut(trim(intentMatch.slotsJson()), 3000),
                    cut(trim(intentMatch.slots().get("channel")), 120),
                    cut(trim(intentMatch.slots().get("business")), 120),
                    cut(trim(intentMatch.slots().get("location")), 120),
                    normalizedKey
            );
        } catch (Exception ignored) {
            // Backward compatibility for DB without intent columns.
        }
    }

    private void insertSolutionMemoryHistory(String queryKey,
                                             String changedBy,
                                             String changeSource,
                                             String changeAction,
                                             String oldQueryText,
                                             String oldSolutionText,
                                             boolean oldReviewRequired,
                                             String newQueryText,
                                             String newSolutionText,
                                             boolean newReviewRequired,
                                             String note) {
        jdbcTemplate.update(
                """
                INSERT INTO ai_agent_solution_memory_history(
                    query_key, changed_by, change_source, change_action,
                    old_query_text, old_solution_text, old_review_required,
                    new_query_text, new_solution_text, new_review_required, note, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                trim(queryKey),
                trim(changedBy),
                cut(trim(changeSource), 64),
                cut(trim(changeAction), 64),
                cut(trim(oldQueryText), 600),
                cut(trim(oldSolutionText), 2000),
                oldReviewRequired ? 1 : 0,
                cut(trim(newQueryText), 600),
                cut(trim(newSolutionText), 2000),
                newReviewRequired ? 1 : 0,
                cut(trim(note), 500)
        );
    }

    private boolean isMeaningfullyDifferent(String left, String right, double differenceThreshold) {
        return similarity(left, right) < differenceThreshold;
    }

    private double similarity(String left, String right) {
        Set<String> first = tokenize(left);
        Set<String> second = tokenize(right);
        if (first.isEmpty() || second.isEmpty()) {
            return 0d;
        }
        int intersection = 0;
        for (String token : first) if (second.contains(token)) intersection++;
        int union = first.size() + second.size() - intersection;
        return union <= 0 ? 0d : intersection / (double) union;
    }

    private Set<String> tokenize(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) return Set.of();
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String token : TOKEN_SPLIT.split(normalized)) {
            String item = trim(token);
            if (item == null || item.length() < 2 || STOP.contains(item)) continue;
            out.add(item);
        }
        return out;
    }

    private String buildKey(String question) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalize(question).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return Integer.toHexString(normalize(question).hashCode());
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) return "";
        return value.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435');
    }

    private String safe(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean b) return b;
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized);
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String cut(String text, int len) {
        String value = trim(text);
        if (value == null) return "";
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= len ? compact : compact.substring(0, Math.max(0, len - 3)) + "...";
    }

    public record UpsertResult(String queryKey, String action) {
    }
}
