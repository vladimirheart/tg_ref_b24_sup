package com.example.panel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DialogAiAssistantService {
    private static final Logger log = LoggerFactory.getLogger(DialogAiAssistantService.class);
    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Pattern ENTITY_HINT_PATTERN = Pattern.compile("(#?[a-zA-Zа-яА-Я]{2,}[\\-_]?[0-9]{2,}|\\+?[0-9][0-9\\-()\\s]{6,}|[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
    private static final Set<String> STOP = Set.of("Рё","РІ","РЅР°","РЅРµ","С‡С‚Рѕ","РєР°Рє","РґР»СЏ","РёР»Рё","РїРѕ","РёР·","Рє","Сѓ","Рѕ","РѕР±","the","a","an","to","of","in","on","for","and","or","is","are","be");
    private static final double AUTO_REPLY_THRESHOLD_DEFAULT = 0.62d;
    private static final double SUGGEST_THRESHOLD_DEFAULT = 0.46d;
    private static final double DIFFERENCE_THRESHOLD_DEFAULT = 0.42d;
    private static final int MAX_AUTO_REPLIES_PER_DIALOG_DEFAULT = 3;
    private static final int AUTO_REPLY_WINDOW_MINUTES_DEFAULT = 60;
    private static final int AUTO_REPLY_COOLDOWN_SECONDS_DEFAULT = 90;
    private static final int DEFAULT_SUGGESTION_LIMIT = 3;
    private static final String MODE_AUTO_REPLY = "auto_reply";
    private static final String MODE_ASSIST_ONLY = "assist_only";
    private static final String MODE_ESCALATE_ONLY = "escalate_only";

    private final JdbcTemplate jdbcTemplate;
    private final DialogReplyService dialogReplyService;
    private final NotificationService notificationService;
    private final SharedConfigService sharedConfigService;
    private final ObjectMapper objectMapper;

    public DialogAiAssistantService(JdbcTemplate jdbcTemplate, DialogReplyService dialogReplyService, NotificationService notificationService, SharedConfigService sharedConfigService, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialogReplyService = dialogReplyService;
        this.notificationService = notificationService;
        this.sharedConfigService = sharedConfigService;
        this.objectMapper = objectMapper;
    }

    public void processIncomingClientMessage(String ticketId, String message) {
        String t = trim(ticketId);
        String m = trim(message);
        if (t == null || m == null) return;

        DialogAiControl control = loadDialogControl(t);
        if (control.aiDisabled()) {
            clearProcessing(t, "disabled_for_dialog", control.reason(), "disabled", "dialog_override_disabled", null);
            recordAiEvent(t, "ai_agent_disabled_for_dialog", null, "disabled", "dialog_override_disabled", null, null, control.reason(), Map.of(
                    "ai_disabled", true,
                    "auto_reply_blocked", control.autoReplyBlocked()
            ));
            return;
        }

        if (!isAgentEnabled()) {
            clearProcessing(t, "disabled", null, "disabled", "config_disabled", null);
            recordAiEvent(t, "ai_agent_decision_made", null, "disabled", "config_disabled", null, null, "Agent disabled in dialog_config", Map.of(
                    "action", "disabled"
            ));
            return;
        }

        if (requiresHumanImmediately(m)) {
            clearProcessing(t, "manual_requested", null, "escalate", "manual_requested", null);
            notifyOperatorsEscalation(t, m, "Клиент запросил оператора.");
            recordAiEvent(t, "ai_agent_escalated", null, "escalate", "manual_requested", null, null, "Client explicitly requested operator", Map.of(
                    "message_preview", cut(m, 200)
            ));
            return;
        }

        String mode = resolveAgentMode();
        markProcessing(t, "processing", null, null, "processing", "incoming_message", null, mode);
        List<AiSuggestion> suggestions = findSuggestions(t, m, DEFAULT_SUGGESTION_LIMIT);
        String sourceHits = encodeSourceHits(suggestions);

        if (suggestions.isEmpty()) {
            clearProcessing(t, "no_match", "Нет релевантных источников.", "escalate", "no_match", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "Агент не нашел релевантный ответ.");
            recordAiEvent(t, "ai_agent_escalated", null, "escalate", "no_match", null, null, "No relevant sources", Map.of(
                    "source_hits", sourceHits
            ));
            return;
        }

        AiSuggestion top = suggestions.get(0);
        double autoReplyThreshold = resolveAutoReplyThreshold();
        double suggestThreshold = resolveSuggestThreshold();

        if (MODE_ESCALATE_ONLY.equals(mode)) {
            clearProcessing(t, "escalated", "Режим escalate_only", "escalate", "mode_escalate_only", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "Включен режим escalate_only.");
            recordAiEvent(t, "ai_agent_escalated", null, "escalate", "mode_escalate_only", top.source, top.score, "Escalate-only mode", Map.of(
                    "source_hits", sourceHits
            ));
            return;
        }

        if (top.score < suggestThreshold) {
            clearProcessing(t, "low_confidence", "Низкая уверенность (" + formatScore(top.score) + ").", "escalate", "below_suggest_threshold", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "Низкая уверенность агента: " + formatScore(top.score));
            recordAiEvent(t, "ai_agent_escalated", null, "escalate", "below_suggest_threshold", top.source, top.score, "Low confidence", Map.of(
                    "source_hits", sourceHits,
                    "suggest_threshold", suggestThreshold
            ));
            return;
        }

        if (MODE_ASSIST_ONLY.equals(mode) || top.score < autoReplyThreshold || control.autoReplyBlocked()) {
            String decisionReason = control.autoReplyBlocked()
                    ? "dialog_override_auto_reply_blocked"
                    : (MODE_ASSIST_ONLY.equals(mode) ? "mode_assist_only" : "below_auto_reply_threshold");
            markProcessing(
                    t,
                    "suggest_only",
                    top,
                    null,
                    "suggest_only",
                    decisionReason,
                    sourceHits,
                    mode
            );
            recordAiEvent(t, "ai_agent_suggestion_shown", null, "suggest_only", decisionReason, top.source, top.score, "Suggestion shown to operator", Map.of(
                    "source_hits", sourceHits,
                    "auto_reply_threshold", autoReplyThreshold
            ));
            return;
        }

        AutoReplyGuard guard = evaluateAutoReplyGuard(t);
        if (!guard.allowed()) {
            clearProcessing(t, "auto_reply_suppressed", guard.reason(), "suppressed", "loop_guard", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "Автоответ подавлен защитой от циклов: " + guard.reason());
            recordAiEvent(t, "ai_agent_decision_made", null, "suppressed", "loop_guard", top.source, top.score, guard.reason(), Map.of(
                    "source_hits", sourceHits
            ));
            return;
        }

        String reply = buildAutoReply(top);
        DialogReplyService.DialogReplyResult result = dialogReplyService.sendReply(t, reply, null, null, "ai_agent");
        if (!result.success()) {
            clearProcessing(t, "send_failed", result.error(), "escalate", "send_failed", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "Ошибка отправки автоответа: " + result.error());
            recordAiEvent(t, "ai_agent_escalated", null, "escalate", "send_failed", top.source, top.score, result.error(), Map.of(
                    "source_hits", sourceHits
            ));
            return;
        }

        if (top.memoryKey != null) {
            markMemoryUsage(top.memoryKey);
        }
        markProcessing(t, "auto_replied", top, null, "auto_reply", "score_above_threshold", sourceHits, mode);
        recordAiEvent(t, "ai_agent_auto_reply_sent", "ai_agent", "auto_reply", "score_above_threshold", top.source, top.score, "Auto reply sent", Map.of(
                "source_hits", sourceHits,
                "reply_preview", cut(reply, 300)
        ));
    }

    public void registerOperatorReply(String ticketId, String operatorReply, String operator) {
        try {
            String t = trim(ticketId), r = trim(operatorReply);
            if (t == null || r == null) return;
            String lastClient = loadLastClientMessage(t);
            if (!StringUtils.hasText(lastClient)) return;
            String key = upsertLearningSolution(t, lastClient, r, operator);
            if (key == null) return;
            String suggested = loadLastSuggestedReply(t);
            if (!StringUtils.hasText(suggested) || !isMeaningfullyDifferent(suggested, r) || hasOpenCorrectionRequest(t)) return;
            notificationService.notifyDialogParticipants(
                    t,
                    "AI-СЂРµС€РµРЅРёРµ РїРѕ РѕР±СЂР°С‰РµРЅРёСЋ " + t + " РѕС‚Р»РёС‡Р°РµС‚СЃСЏ РѕС‚ РѕС‚РІРµС‚Р° РѕРїРµСЂР°С‚РѕСЂР°. Р’РЅРµСЃРёС‚Рµ РїСЂР°РІРєРё РІ СЃРѕС…СЂР°РЅРµРЅРЅРѕРµ СЂРµС€РµРЅРёРµ.",
                    "/dialogs?ticketId=" + t,
                    null
            );
            clearProcessing(t, "operator_correction_requested", "operator_reply_differs");
            recordAiEvent(t, "ai_agent_correction_requested", trim(operator), "review", "operator_reply_differs", null, null, "Operator reply differs from AI memory", Map.of(
                    "ticket_id", t,
                    "memory_key", key
            ));
            jdbcTemplate.update("UPDATE ai_agent_solution_memory SET review_required = 1, pending_solution_text = ?, updated_at = CURRENT_TIMESTAMP WHERE query_key = ?", cut(r, 2000), key);
        } catch (Exception ex) {
            log.debug("registerOperatorReply failed for {}: {}", ticketId, ex.getMessage());
        }
    }

    public List<Map<String, Object>> loadOperatorSuggestions(String ticketId, Integer limit) {
        String t = trim(ticketId);
        if (t == null) return List.of();
        String lastClient = loadLastClientMessage(t);
        if (!StringUtils.hasText(lastClient)) return List.of();
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : DEFAULT_SUGGESTION_LIMIT, 8));
        List<Map<String, Object>> payload = new ArrayList<>();
        for (AiSuggestion s : findSuggestions(t, lastClient, safeLimit)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", s.source);
            item.put("title", s.title);
            item.put("score", s.score);
            item.put("score_label", formatScore(s.score));
            item.put("snippet", s.snippet);
            item.put("reply", buildOperatorReplySuggestion(s));
            item.put("explain", buildSuggestionExplain(s));
            payload.add(item);
        }
        return payload;
    }

    public Map<String, Object> loadDialogControlState(String ticketId) {
        DialogAiControl control = loadDialogControl(ticketId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ticket_id", trim(ticketId));
        payload.put("ai_disabled", control.aiDisabled());
        payload.put("auto_reply_blocked", control.autoReplyBlocked());
        payload.put("reason", control.reason());
        payload.put("updated_by", control.updatedBy());
        payload.put("updated_at", control.updatedAt());
        return payload;
    }

    public boolean updateDialogControlState(String ticketId,
                                            Boolean aiDisabled,
                                            Boolean autoReplyBlocked,
                                            String reason,
                                            String actor) {
        String t = trim(ticketId);
        if (t == null) return false;
        DialogAiControl current = loadDialogControl(t);
        boolean nextAiDisabled = aiDisabled != null ? aiDisabled : current.aiDisabled();
        boolean nextAutoReplyBlocked = autoReplyBlocked != null ? autoReplyBlocked : current.autoReplyBlocked();
        try {
            jdbcTemplate.update("""
                    INSERT INTO ticket_ai_agent_dialog_control(ticket_id, ai_disabled, auto_reply_blocked, reason, updated_by, updated_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(ticket_id) DO UPDATE SET
                        ai_disabled = excluded.ai_disabled,
                        auto_reply_blocked = excluded.auto_reply_blocked,
                        reason = excluded.reason,
                        updated_by = excluded.updated_by,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    t,
                    nextAiDisabled ? 1 : 0,
                    nextAutoReplyBlocked ? 1 : 0,
                    cut(reason, 500),
                    trim(actor)
            );
            recordAiEvent(t, "ai_agent_control_changed", trim(actor), "control_update", "dialog_control_updated", null, null, trim(reason), Map.of(
                    "ai_disabled", nextAiDisabled,
                    "auto_reply_blocked", nextAutoReplyBlocked
            ));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public void recordSuggestionFeedback(String ticketId,
                                         String decision,
                                         String source,
                                         String title,
                                         String snippet,
                                         String suggestedReply,
                                         String actor) {
        String t = trim(ticketId);
        String d = trim(decision);
        if (t == null || d == null) return;
        try {
            jdbcTemplate.update("""
                    INSERT INTO ai_agent_suggestion_feedback(ticket_id, decision, source, title, snippet, suggested_reply, actor, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    t,
                    cut(d.toLowerCase(Locale.ROOT), 64),
                    cut(source, 128),
                    cut(title, 255),
                    cut(snippet, 2000),
                    cut(suggestedReply, 2000),
                    trim(actor)
            );
            String normalizedDecision = d.toLowerCase(Locale.ROOT);
            String eventType = "accepted".equals(normalizedDecision)
                    ? "ai_agent_suggestion_applied"
                    : ("rejected".equals(normalizedDecision) ? "ai_agent_suggestion_rejected" : "ai_agent_suggestion_feedback");
            recordAiEvent(t, eventType, trim(actor), "suggestion_feedback", normalizedDecision, trim(source), null, trim(title), Map.of(
                    "decision", normalizedDecision
            ));
        } catch (Exception ex) {
            log.debug("Failed to persist ai feedback for {}: {}", t, ex.getMessage());
        }
    }

    public Map<String, Object> loadPendingReview(String ticketId) {
        String t = trim(ticketId);
        if (t == null) return Map.of("pending", false);
        String lastClient = loadLastClientMessage(t);
        if (!StringUtils.hasText(lastClient)) return Map.of("pending", false);
        String key = buildKey(lastClient);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT query_key, query_text, solution_text, pending_solution_text, review_required, updated_at FROM ai_agent_solution_memory WHERE query_key = ? LIMIT 1",
                    key
            );
            if (rows.isEmpty()) return Map.of("pending", false);
            Map<String, Object> row = rows.get(0);
            boolean pending = isTrue(row.get("review_required")) && trim(safe(row.get("pending_solution_text"))) != null;
            if (!pending) return Map.of("pending", false, "query_key", key);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("pending", true);
            payload.put("ticket_id", t);
            payload.put("query_key", key);
            payload.put("query_text", safe(row.get("query_text")));
            payload.put("current_solution", safe(row.get("solution_text")));
            payload.put("pending_solution", safe(row.get("pending_solution_text")));
            payload.put("updated_at", safe(row.get("updated_at")));
            return payload;
        } catch (Exception ex) {
            return Map.of("pending", false);
        }
    }

    public boolean approvePendingReview(String ticketId, String operator) {
        String t = trim(ticketId);
        if (t == null) return false;
        String lastClient = loadLastClientMessage(t);
        if (!StringUtils.hasText(lastClient)) return false;
        String key = buildKey(lastClient);
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE ai_agent_solution_memory SET solution_text = pending_solution_text, pending_solution_text = NULL, review_required = 0, times_confirmed = COALESCE(times_confirmed,0) + 1, last_operator = ?, updated_at = CURRENT_TIMESTAMP WHERE query_key = ? AND COALESCE(review_required,0) = 1 AND trim(COALESCE(pending_solution_text,'')) <> ''",
                    trim(operator),
                    key
            );
            if (updated > 0) {
                clearProcessing(t, "operator_correction_approved", null);
                recordAiEvent(t, "ai_agent_correction_approved", trim(operator), "review", "approved", null, null, null, null);
                return true;
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean rejectPendingReview(String ticketId, String operator) {
        String t = trim(ticketId);
        if (t == null) return false;
        String lastClient = loadLastClientMessage(t);
        if (!StringUtils.hasText(lastClient)) return false;
        String key = buildKey(lastClient);
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE ai_agent_solution_memory SET pending_solution_text = NULL, review_required = 0, last_operator = ?, updated_at = CURRENT_TIMESTAMP WHERE query_key = ? AND COALESCE(review_required,0) = 1",
                    trim(operator),
                    key
            );
            if (updated > 0) {
                clearProcessing(t, "operator_correction_rejected", null);
                recordAiEvent(t, "ai_agent_correction_rejected", trim(operator), "review", "rejected", null, null, null, null);
                return true;
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean isProcessing(String ticketId) {
        String t = trim(ticketId);
        if (t == null) return false;
        try { Integer v = jdbcTemplate.queryForObject("SELECT COALESCE(is_processing, 0) FROM ticket_ai_agent_state WHERE ticket_id = ?", Integer.class, t); return v != null && v > 0; }
        catch (Exception ex) { return false; }
    }

    public void clearProcessing(String ticketId, String action, String error) {
        clearProcessing(ticketId, action, error, null, null, null);
    }

    private void clearProcessing(String ticketId, String action, String error, String decisionType, String decisionReason, String sourceHits) {
        clearProcessing(ticketId, action, error, decisionType, decisionReason, sourceHits, resolveAgentMode());
    }

    private void clearProcessing(String ticketId, String action, String error, String decisionType, String decisionReason, String sourceHits, String mode) {
        upsertState(ticketId, false, mode, action, error, null, null, null, decisionType, decisionReason, sourceHits);
    }

    private void markProcessing(String ticketId, String action, AiSuggestion suggestion, String error, String decisionType, String decisionReason, String sourceHits, String mode) {
        upsertState(
                ticketId,
                true,
                mode,
                action,
                error,
                suggestion != null ? suggestion.source : null,
                suggestion != null ? suggestion.score : null,
                suggestion != null ? trim(buildAutoReply(suggestion)) : null,
                decisionType,
                decisionReason,
                sourceHits
        );
    }

    private void upsertState(String ticketId,
                             boolean processing,
                             String mode,
                             String action,
                             String error,
                             String source,
                             Double score,
                             String suggestedReply,
                             String decisionType,
                             String decisionReason,
                             String sourceHits) {
        String t = trim(ticketId);
        if (t == null) return;
        try {
            jdbcTemplate.update("""
                    INSERT INTO ticket_ai_agent_state(ticket_id, is_processing, mode, last_action, last_error, last_source, last_score, last_suggested_reply, decision_type, decision_reason, source_hits, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(ticket_id) DO UPDATE SET
                        is_processing = excluded.is_processing,
                        mode = excluded.mode,
                        last_action = excluded.last_action,
                        last_error = excluded.last_error,
                        last_source = excluded.last_source,
                        last_score = excluded.last_score,
                        last_suggested_reply = excluded.last_suggested_reply,
                        decision_type = excluded.decision_type,
                        decision_reason = excluded.decision_reason,
                        source_hits = excluded.source_hits,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    t, processing ? 1 : 0, trim(mode), trim(action), trim(error), trim(source), score, trim(suggestedReply),
                    trim(decisionType), trim(decisionReason), trim(sourceHits));
        } catch (Exception schemaMismatch) {
            jdbcTemplate.update("""
                    INSERT INTO ticket_ai_agent_state(ticket_id, is_processing, mode, last_action, last_error, last_source, last_score, last_suggested_reply, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT(ticket_id) DO UPDATE SET
                        is_processing = excluded.is_processing,
                        mode = excluded.mode,
                        last_action = excluded.last_action,
                        last_error = excluded.last_error,
                        last_source = excluded.last_source,
                        last_score = excluded.last_score,
                        last_suggested_reply = excluded.last_suggested_reply,
                        updated_at = CURRENT_TIMESTAMP
                    """,
                    t, processing ? 1 : 0, trim(mode), trim(action), trim(error), trim(source), score, trim(suggestedReply));
        }
    }

    private List<AiSuggestion> findSuggestions(String ticketId, String query, int limit) {
        Set<String> q = tokenize(query);
        Set<String> entities = extractEntityHints(query);
        if (q.isEmpty() && entities.isEmpty()) return List.of();
        Long applicantUserId = resolveApplicantUserId(ticketId);
        List<AiSuggestion> candidates = new ArrayList<>();
        candidates.addAll(loadMemoryCandidates(q, entities, limit * 4));
        candidates.addAll(loadKnowledgeCandidates(q, entities, limit * 4));
        candidates.addAll(loadTaskCandidates(q, entities, limit * 4));
        candidates.addAll(loadHistoryCandidates(ticketId, q, entities, limit * 6));
        candidates.addAll(loadApplicantHistoryCandidates(applicantUserId, ticketId, q, entities, limit * 6));
        return rerankSuggestions(q, candidates)
                .stream()
                .filter(x -> x.score > 0d)
                .sorted(Comparator.comparingDouble((AiSuggestion x) -> x.score).reversed())
                .limit(limit)
                .toList();
    }

    private List<AiSuggestion> loadMemoryCandidates(Set<String> q, Set<String> entities, int limit) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList("SELECT query_key, query_text, solution_text, times_confirmed, times_corrected FROM ai_agent_solution_memory WHERE COALESCE(review_required,0)=0 AND solution_text IS NOT NULL AND trim(solution_text)<>'' ORDER BY COALESCE(updated_at, created_at) DESC LIMIT ?", limit);
        } catch (Exception ex) {
            return List.of();
        }
        List<AiSuggestion> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String key = safe(row.get("query_key")), qt = safe(row.get("query_text")), st = safe(row.get("solution_text"));
            double s = scoreByTokens(q, entities, join(qt, st));
            int conf = toInt(row.get("times_confirmed")), corr = toInt(row.get("times_corrected"));
            s = Math.max(0d, Math.min(1d, s + Math.min(0.20d, conf * 0.02d) - Math.min(0.12d, corr * 0.02d)));
            if (s > 0d) out.add(new AiSuggestion("memory", "РџСЂРѕРІРµСЂРµРЅРЅРѕРµ СЂРµС€РµРЅРёРµ", cut(st, 320), s, trim(key)));
        }
        return out;
    }

    private List<AiSuggestion> loadKnowledgeCandidates(Set<String> q, Set<String> entities, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT title, summary, content FROM knowledge_articles ORDER BY COALESCE(updated_at, created_at) DESC LIMIT ?", limit);
        List<AiSuggestion> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String title = safe(row.get("title"));
            String text = cleanTextForRetrieval(join(title, safe(row.get("summary")), safe(row.get("content"))));
            double s = scoreByTokens(q, entities, text);
            if (s > 0d) out.add(new AiSuggestion("knowledge", StringUtils.hasText(title) ? title : "РЎС‚Р°С‚СЊСЏ Р±Р°Р·С‹ Р·РЅР°РЅРёР№", cut(text, 280), s, null));
        }
        return out;
    }

    private List<AiSuggestion> loadTaskCandidates(Set<String> q, Set<String> entities, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT title, body_html, status FROM tasks ORDER BY COALESCE(last_activity_at, created_at) DESC LIMIT ?", limit);
        List<AiSuggestion> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String title = safe(row.get("title"));
            String text = cleanTextForRetrieval(join(title, stripHtml(safe(row.get("body_html"))), safe(row.get("status"))));
            double s = scoreByTokens(q, entities, text);
            if (s > 0d) out.add(new AiSuggestion("tasks", StringUtils.hasText(title) ? title : "РџРѕС…РѕР¶Р°СЏ Р·Р°РґР°С‡Р°", cut(text, 280), s, null));
        }
        return out;
    }

    private List<AiSuggestion> loadHistoryCandidates(String ticketId, Set<String> q, Set<String> entities, int limit) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT ticket_id, message FROM chat_history WHERE ticket_id <> ? AND lower(sender) IN ('operator','support','admin','system','ai_agent') AND message IS NOT NULL AND trim(message)<>'' ORDER BY id DESC LIMIT ?", ticketId, limit);
        List<AiSuggestion> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String ticket = safe(row.get("ticket_id"));
            String msg = cleanTextForRetrieval(safe(row.get("message")));
            double s = scoreByTokens(q, entities, msg);
            if (s > 0d) out.add(new AiSuggestion("history", StringUtils.hasText(ticket) ? "РџРѕС…РѕР¶РёР№ РґРёР°Р»РѕРі #" + ticket : "РџРѕС…РѕР¶РёР№ РґРёР°Р»РѕРі", cut(msg, 260), s, null));
        }
        return out;
    }

    private List<AiSuggestion> loadApplicantHistoryCandidates(Long userId, String ticketId, Set<String> q, Set<String> entities, int limit) {
        if (userId == null || !StringUtils.hasText(ticketId)) {
            return List.of();
        }
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    """
                    SELECT ch.ticket_id, ch.message
                      FROM chat_history ch
                      JOIN messages m ON m.ticket_id = ch.ticket_id
                     WHERE m.user_id = ?
                       AND ch.ticket_id <> ?
                       AND lower(COALESCE(ch.sender, '')) IN ('operator','support','admin','system','ai_agent')
                       AND ch.message IS NOT NULL
                       AND trim(ch.message) <> ''
                     ORDER BY ch.id DESC
                     LIMIT ?
                    """,
                    userId,
                    ticketId,
                    limit
            );
        } catch (Exception ex) {
            return List.of();
        }
        List<AiSuggestion> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String prevTicket = safe(row.get("ticket_id"));
            String msg = cleanTextForRetrieval(safe(row.get("message")));
            double s = scoreByTokens(q, entities, msg);
            if (s <= 0d) {
                continue;
            }
            // Applicant-specific history is more relevant than generic history.
            double boosted = Math.max(0d, Math.min(1d, s + 0.12d));
            out.add(new AiSuggestion(
                    "applicant_history",
                    StringUtils.hasText(prevTicket) ? "История заявителя #" + prevTicket : "История заявителя",
                    cut(msg, 260),
                    boosted,
                    null
            ));
        }
        return out;
    }

    private String upsertLearningSolution(String ticketId, String clientQuestion, String operatorReply, String operator) {
        String q = trim(clientQuestion), r = trim(operatorReply); if (q == null || r == null) return null;
        String key = buildKey(q);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT solution_text,pending_solution_text,review_required FROM ai_agent_solution_memory WHERE query_key=? LIMIT 1", key);
        if (rows.isEmpty()) {
            jdbcTemplate.update("INSERT INTO ai_agent_solution_memory(query_key,query_text,solution_text,source,times_used,times_confirmed,times_corrected,review_required,pending_solution_text,last_operator,last_ticket_id,last_client_message,created_at,updated_at) VALUES (?,?,?,?,0,1,0,0,NULL,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", key, cut(q, 600), cut(r, 2000), "operator", trim(operator), trim(ticketId), cut(q, 600));
            return key;
        }
        Map<String, Object> ex = rows.get(0);
        String sol = trim(safe(ex.get("solution_text"))), pending = trim(safe(ex.get("pending_solution_text")));
        boolean review = isTrue(ex.get("review_required"));
        if (review && pending != null && !isMeaningfullyDifferent(pending, r)) {
            jdbcTemplate.update("UPDATE ai_agent_solution_memory SET query_text=?,solution_text=?,review_required=0,pending_solution_text=NULL,times_confirmed=COALESCE(times_confirmed,0)+1,last_operator=?,last_ticket_id=?,last_client_message=?,updated_at=CURRENT_TIMESTAMP WHERE query_key=?", cut(q, 600), cut(r, 2000), trim(operator), trim(ticketId), cut(q, 600), key);
            return key;
        }
        if (sol != null && isMeaningfullyDifferent(sol, r)) {
            jdbcTemplate.update("UPDATE ai_agent_solution_memory SET query_text=?,review_required=1,pending_solution_text=?,times_corrected=COALESCE(times_corrected,0)+1,last_operator=?,last_ticket_id=?,last_client_message=?,updated_at=CURRENT_TIMESTAMP WHERE query_key=?", cut(q, 600), cut(r, 2000), trim(operator), trim(ticketId), cut(q, 600), key);
            return key;
        }
        jdbcTemplate.update("UPDATE ai_agent_solution_memory SET query_text=?,solution_text=?,review_required=0,pending_solution_text=NULL,times_confirmed=COALESCE(times_confirmed,0)+1,last_operator=?,last_ticket_id=?,last_client_message=?,updated_at=CURRENT_TIMESTAMP WHERE query_key=?", cut(q, 600), cut(r, 2000), trim(operator), trim(ticketId), cut(q, 600), key);
        return key;
    }

    public List<Map<String, Object>> loadPendingReviewsQueue(Integer limit) {
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : 25, 200));
        try {
            return jdbcTemplate.queryForList(
                    "SELECT query_key, query_text, solution_text, pending_solution_text, last_ticket_id, updated_at, times_confirmed, times_corrected FROM ai_agent_solution_memory WHERE COALESCE(review_required,0)=1 AND trim(COALESCE(pending_solution_text,''))<>'' ORDER BY COALESCE(updated_at, created_at) DESC LIMIT ?",
                    safeLimit
            );
        } catch (Exception ex) {
            return List.of();
        }
    }

    public Map<String, Object> loadMonitoringSummary(Integer days) {
        int safeDays = Math.max(1, Math.min(days != null ? days : 7, 90));
        String sinceExpr = "-" + safeDays + " days";

        int inboundMessages = queryCount(
                "SELECT COUNT(*) FROM chat_history WHERE lower(COALESCE(sender,'')) NOT IN ('operator','support','admin','system','ai_agent') AND datetime(substr(COALESCE(timestamp,''),1,19)) >= datetime('now', ?)",
                sinceExpr
        );
        int autoReplies = queryCount(
                "SELECT COUNT(*) FROM chat_history WHERE lower(COALESCE(sender,'')) = 'ai_agent' AND datetime(substr(COALESCE(timestamp,''),1,19)) >= datetime('now', ?)",
                sinceExpr
        );
        int suggestOnly = queryCount(
                "SELECT COUNT(*) FROM ticket_ai_agent_state WHERE lower(COALESCE(last_action,'')) = 'suggest_only' AND datetime(substr(COALESCE(updated_at,''),1,19)) >= datetime('now', ?)",
                sinceExpr
        );
        int escalations = queryCount(
                "SELECT COUNT(*) FROM ticket_ai_agent_state WHERE lower(COALESCE(decision_type,'')) = 'escalate' AND datetime(substr(COALESCE(updated_at,''),1,19)) >= datetime('now', ?)",
                sinceExpr
        );
        int operatorCorrections = queryCount(
                "SELECT COUNT(*) FROM ticket_ai_agent_state WHERE lower(COALESCE(last_action,'')) = 'operator_correction_requested' AND datetime(substr(COALESCE(updated_at,''),1,19)) >= datetime('now', ?)",
                sinceExpr
        );
        int rejectedSuggestions = queryCount(
                "SELECT COUNT(*) FROM ai_agent_suggestion_feedback WHERE lower(COALESCE(decision,'')) = 'rejected' AND datetime(substr(COALESCE(created_at,''),1,19)) >= datetime('now', ?)",
                sinceExpr
        );
        int acceptedSuggestions = queryCount(
                "SELECT COUNT(*) FROM ai_agent_suggestion_feedback WHERE lower(COALESCE(decision,'')) = 'accepted' AND datetime(substr(COALESCE(created_at,''),1,19)) >= datetime('now', ?)",
                sinceExpr
        );

        double autoReplyRate = safeRate(autoReplies, inboundMessages);
        double assistUsageRate = safeRate(suggestOnly, inboundMessages);
        double escalationRate = safeRate(escalations, inboundMessages);
        double correctionRate = safeRate(operatorCorrections, Math.max(1, autoReplies + suggestOnly));
        double rejectionRate = safeRate(rejectedSuggestions, Math.max(1, rejectedSuggestions + acceptedSuggestions));

        List<Map<String, Object>> alerts = buildMonitoringAlerts(
                inboundMessages,
                autoReplies,
                suggestOnly,
                escalations,
                operatorCorrections,
                rejectedSuggestions,
                autoReplyRate,
                escalationRate,
                correctionRate,
                rejectionRate
        );

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("inbound_messages", inboundMessages);
        kpis.put("auto_replies", autoReplies);
        kpis.put("suggest_only", suggestOnly);
        kpis.put("escalations", escalations);
        kpis.put("operator_corrections", operatorCorrections);
        kpis.put("rejected_suggestions", rejectedSuggestions);
        kpis.put("accepted_suggestions", acceptedSuggestions);
        kpis.put("auto_reply_rate", autoReplyRate);
        kpis.put("assist_usage_rate", assistUsageRate);
        kpis.put("escalation_rate", escalationRate);
        kpis.put("correction_rate", correctionRate);
        kpis.put("suggestion_rejection_rate", rejectionRate);

        Map<String, Object> runbook = new LinkedHashMap<>();
        runbook.put("title", "AI Agent Incident Runbook");
        runbook.put("items", List.of(
                "Проверьте всплески escalation_rate и correction_rate.",
                "Если escalation_rate > 35%, переключите ai_agent_mode в assist_only.",
                "Если correction_rate > 25%, откройте очередь ревизий и обновите память решений.",
                "При массовых ошибках отключите AI для проблемных диалогов через быстрые действия."
        ));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("window_days", safeDays);
        payload.put("generated_at", Instant.now().toString());
        payload.put("kpis", kpis);
        payload.put("alerts", alerts);
        payload.put("runbook", runbook);
        return payload;
    }

    public List<Map<String, Object>> loadMonitoringEvents(Integer days,
                                                          Integer limit,
                                                          String ticketId,
                                                          String eventType,
                                                          String actor) {
        int safeDays = Math.max(1, Math.min(days != null ? days : 7, 90));
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : 50, 200));
        String sinceExpr = "-" + safeDays + " days";
        String ticket = trim(ticketId);
        String event = trim(eventType);
        String who = trim(actor);
        try {
            List<Object> params = new ArrayList<>();
            StringBuilder sql = new StringBuilder("""
                    SELECT id, ticket_id, event_type, actor, decision_type, decision_reason, source, score, detail, payload_json, created_at
                      FROM ai_agent_event_log
                     WHERE datetime(substr(COALESCE(created_at,''),1,19)) >= datetime('now', ?)
                    """);
            params.add(sinceExpr);
            if (ticket != null) {
                sql.append(" AND ticket_id = ?");
                params.add(ticket);
            }
            if (event != null) {
                sql.append(" AND lower(COALESCE(event_type,'')) = ?");
                params.add(event.toLowerCase(Locale.ROOT));
            }
            if (who != null) {
                sql.append(" AND lower(COALESCE(actor,'')) = ?");
                params.add(who.toLowerCase(Locale.ROOT));
            }
            sql.append(" ORDER BY id DESC LIMIT ?");
            params.add(safeLimit);
            return jdbcTemplate.queryForList(sql.toString(), params.toArray());
        } catch (Exception ex) {
            return List.of();
        }
    }

    public boolean approvePendingReviewByKey(String queryKey, String operator) {
        String key = trim(queryKey);
        if (key == null) return false;
        try {
            String ticketId = jdbcTemplate.query(
                    "SELECT last_ticket_id FROM ai_agent_solution_memory WHERE query_key = ? LIMIT 1",
                    rs -> rs.next() ? trim(rs.getString("last_ticket_id")) : null,
                    key
            );
            int updated = jdbcTemplate.update(
                    "UPDATE ai_agent_solution_memory SET solution_text = pending_solution_text, pending_solution_text = NULL, review_required = 0, times_confirmed = COALESCE(times_confirmed,0) + 1, last_operator = ?, updated_at = CURRENT_TIMESTAMP WHERE query_key = ? AND COALESCE(review_required,0)=1 AND trim(COALESCE(pending_solution_text,''))<>''",
                    trim(operator),
                    key
            );
            if (updated > 0 && ticketId != null) {
                clearProcessing(ticketId, "operator_correction_approved", null);
                recordAiEvent(ticketId, "ai_agent_correction_approved", trim(operator), "review", "approved", null, null, null, Map.of(
                        "query_key", key
                ));
            }
            return updated > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean rejectPendingReviewByKey(String queryKey, String operator) {
        String key = trim(queryKey);
        if (key == null) return false;
        try {
            String ticketId = jdbcTemplate.query(
                    "SELECT last_ticket_id FROM ai_agent_solution_memory WHERE query_key = ? LIMIT 1",
                    rs -> rs.next() ? trim(rs.getString("last_ticket_id")) : null,
                    key
            );
            int updated = jdbcTemplate.update(
                    "UPDATE ai_agent_solution_memory SET pending_solution_text = NULL, review_required = 0, last_operator = ?, updated_at = CURRENT_TIMESTAMP WHERE query_key = ? AND COALESCE(review_required,0)=1",
                    trim(operator),
                    key
            );
            if (updated > 0 && ticketId != null) {
                clearProcessing(ticketId, "operator_correction_rejected", null);
                recordAiEvent(ticketId, "ai_agent_correction_rejected", trim(operator), "review", "rejected", null, null, null, Map.of(
                        "query_key", key
                ));
            }
            return updated > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private void markMemoryUsage(String key) { try { jdbcTemplate.update("UPDATE ai_agent_solution_memory SET times_used=COALESCE(times_used,0)+1,updated_at=CURRENT_TIMESTAMP WHERE query_key=?", key); } catch (Exception ignored) {} }
    private boolean hasOpenCorrectionRequest(String ticketId) { String a = jdbcTemplate.query("SELECT last_action FROM ticket_ai_agent_state WHERE ticket_id=? LIMIT 1", rs -> rs.next() ? trim(rs.getString("last_action")) : null, ticketId); return "operator_correction_requested".equalsIgnoreCase(String.valueOf(a)); }
    private String loadLastSuggestedReply(String ticketId) { return jdbcTemplate.query("SELECT last_suggested_reply FROM ticket_ai_agent_state WHERE ticket_id=? LIMIT 1", rs -> rs.next() ? trim(rs.getString("last_suggested_reply")) : null, ticketId); }
    private Long resolveApplicantUserId(String ticketId) {
        String t = trim(ticketId);
        if (t == null) {
            return null;
        }
        try {
            return jdbcTemplate.query(
                    "SELECT user_id FROM messages WHERE ticket_id = ? AND user_id IS NOT NULL LIMIT 1",
                    rs -> rs.next() ? rs.getLong("user_id") : null,
                    t
            );
        } catch (Exception ex) {
            return null;
        }
    }
    private String loadLastClientMessage(String ticketId) { return jdbcTemplate.query("SELECT message FROM chat_history WHERE ticket_id=? AND lower(sender) NOT IN ('operator','support','admin','system','ai_agent') AND message IS NOT NULL AND trim(message)<>'' ORDER BY id DESC LIMIT 1", rs -> rs.next() ? trim(rs.getString("message")) : null, ticketId); }
    private String buildAutoReply(AiSuggestion s) {
        String body = cleanTextForRetrieval(s != null ? s.snippet : null);
        if (!StringUtils.hasText(body)) {
            body = "Не найдено готовое решение в базе знаний.";
        }
        List<String> steps = splitIntoSteps(body, 3);
        StringBuilder reply = new StringBuilder();
        reply.append("Коротко: ").append(cut(firstSentence(body), 220)).append("\n\n");
        reply.append("Что сделать:\n");
        if (steps.isEmpty()) {
            reply.append("1. ").append(cut(body, 280)).append("\n");
        } else {
            for (int i = 0; i < steps.size(); i++) {
                reply.append(i + 1).append(". ").append(steps.get(i)).append("\n");
            }
        }
        reply.append("\nЕсли не поможет, напишите «оператор» — подключим специалиста.");
        return reply.toString();
    }
    private String buildOperatorReplySuggestion(AiSuggestion s) {
        String sourceLabel = switch (s.source) {
            case "memory" -> "проверенным решениям";
            case "knowledge" -> "базе знаний";
            case "tasks" -> "истории задач";
            case "history" -> "похожим диалогам";
            case "applicant_history" -> "истории этого заявителя";
            default -> "внутренним данным";
        };
        String prepared = buildAutoReply(s);
        return "По " + sourceLabel + " предлагаемый ответ:\n\n" + prepared;
    }
    private String buildSuggestionExplain(AiSuggestion s) {
        String sourceExplain = switch (String.valueOf(s.source).toLowerCase(Locale.ROOT)) {
            case "memory" -> "Выбрано из подтвержденных операторских решений.";
            case "knowledge" -> "Выбрано из статей базы знаний.";
            case "tasks" -> "Выбрано из похожих задач.";
            case "history" -> "Выбрано из похожих диалогов.";
            case "applicant_history" -> "Выбрано из прошлых диалогов этого заявителя.";
            default -> "Выбрано из внутренних источников.";
        };
        return sourceExplain + " Оценка релевантности: " + formatScore(s.score) + ".";
    }
    private void notifyOperatorsEscalation(String ticketId, String msg, String reason) { String t = "AI-Р°РіРµРЅС‚ СЌСЃРєР°Р»РёСЂРѕРІР°Р» РѕР±СЂР°С‰РµРЅРёРµ " + ticketId + ". " + reason; if (StringUtils.hasText(msg)) t += " Р’РѕРїСЂРѕСЃ РєР»РёРµРЅС‚Р°: " + cut(msg, 140); notificationService.notifyAllOperators(t, "/dialogs?ticketId=" + ticketId, null); }
    private boolean isAgentEnabled() { try { Object d = sharedConfigService.loadSettings().get("dialog_config"); if (d instanceof Map<?,?> m) { Object e = m.get("ai_agent_enabled"); if (e instanceof Boolean b) return b; String n = String.valueOf(e).trim().toLowerCase(Locale.ROOT); return !"false".equals(n) && !"0".equals(n) && !"off".equals(n); } } catch (Exception ex) { log.debug("ai_agent_enabled read failed: {}", ex.getMessage()); } return true; }
    private boolean requiresHumanImmediately(String m) { String n = String.valueOf(m).toLowerCase(Locale.ROOT); return n.contains("РѕРїРµСЂР°С‚РѕСЂ") || n.contains("С‡РµР»РѕРІРµРє") || n.contains("РјРµРЅРµРґР¶РµСЂ") || n.contains("РїРѕР·РІРѕРЅРёС‚Рµ"); }
    private boolean isMeaningfullyDifferent(String a, String b) { return similarity(a, b) < resolveDifferenceThreshold(); }
    private double similarity(String a, String b) { Set<String> x = tokenize(a), y = tokenize(b); if (x.isEmpty() || y.isEmpty()) return 0d; int i = 0; for (String t : x) if (y.contains(t)) i++; int u = x.size() + y.size() - i; return u <= 0 ? 0d : i / (double) u; }
    private double scoreByTokens(Set<String> q, Set<String> entities, String src) {
        Set<String> s = tokenize(src);
        if (q.isEmpty() && (entities == null || entities.isEmpty())) return 0d;
        if (s.isEmpty()) return 0d;
        int overlap = 0;
        for (String t : q) if (s.contains(t)) overlap++;
        double base = q.isEmpty() ? 0d : overlap / (double) q.size();
        String normalized = normalize(src);
        double phraseBoost = 0d;
        for (String token : q) {
            if (token.length() >= 5 && normalized.contains(token)) phraseBoost += 0.02d;
        }
        double entityBoost = 0d;
        if (entities != null && !entities.isEmpty()) {
            int entityHits = 0;
            for (String hint : entities) {
                if (normalized.contains(hint)) entityHits++;
            }
            entityBoost = Math.min(0.24d, entityHits * 0.08d);
        }
        return Math.max(0d, Math.min(1d, base + phraseBoost + entityBoost));
    }
    private Set<String> tokenize(String v) { String n = normalize(v); if (!StringUtils.hasText(n)) return Set.of(); Set<String> out = new LinkedHashSet<>(); for (String t : TOKEN_SPLIT.split(n)) { String x = trim(t); if (x == null || x.length() < 2 || STOP.contains(x)) continue; out.add(x); } return out; }
    private String normalize(String v) { if (!StringUtils.hasText(v)) return ""; return v.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435'); }
    private String cleanTextForRetrieval(String value) {
        if (!StringUtils.hasText(value)) return "";
        String text = stripHtml(value);
        text = text.replaceAll("```[\\s\\S]*?```", " ");
        text = text.replaceAll("\\[[^\\]]+\\]\\([^\\)]+\\)", " ");
        text = text.replaceAll("https?://\\S+", " ");
        text = text.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ");
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }
    private Set<String> extractEntityHints(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        var matcher = ENTITY_HINT_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String hit = trim(matcher.group());
            if (hit == null) continue;
            if (hit.length() >= 4) out.add(hit);
        }
        return out;
    }
    private List<AiSuggestion> rerankSuggestions(Set<String> queryTokens, List<AiSuggestion> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<AiSuggestion> weighted = new ArrayList<>();
        for (AiSuggestion candidate : candidates) {
            double score = applySourceWeight(candidate.source, candidate.score);
            weighted.add(new AiSuggestion(candidate.source, candidate.title, candidate.snippet, score, candidate.memoryKey));
        }
        Map<String, AiSuggestion> bestBySource = new HashMap<>();
        for (AiSuggestion candidate : weighted) {
            AiSuggestion current = bestBySource.get(candidate.source);
            if (current == null || candidate.score > current.score) {
                bestBySource.put(candidate.source, candidate);
            }
        }
        List<AiSuggestion> reranked = new ArrayList<>();
        for (AiSuggestion candidate : weighted) {
            double penalty = 0d;
            for (AiSuggestion anchor : bestBySource.values()) {
                if (anchor.source.equals(candidate.source)) continue;
                if (anchor.score < 0.45d || candidate.score < 0.45d) continue;
                double sim = similarity(anchor.snippet, candidate.snippet);
                if (sim < 0.12d) {
                    penalty = Math.max(penalty, 0.08d);
                }
            }
            if ("memory".equals(candidate.source)) {
                penalty = Math.max(0d, penalty - 0.03d);
            }
            double tokenCover = queryTokens.isEmpty() ? 0d : similarity(String.join(" ", queryTokens), candidate.snippet);
            double adjusted = Math.max(0d, Math.min(1d, candidate.score - penalty + Math.min(0.06d, tokenCover * 0.1d)));
            reranked.add(new AiSuggestion(candidate.source, candidate.title, candidate.snippet, adjusted, candidate.memoryKey));
        }
        return reranked;
    }
    private double applySourceWeight(String source, double score) {
        double weight = switch (String.valueOf(source).toLowerCase(Locale.ROOT)) {
            case "memory" -> 1.15d;
            case "knowledge" -> 1.08d;
            case "tasks" -> 1.00d;
            case "history" -> 0.92d;
            default -> 1.0d;
        };
        return Math.max(0d, Math.min(1d, score * weight));
    }
    private String firstSentence(String text) {
        String prepared = cleanTextForRetrieval(text);
        if (!StringUtils.hasText(prepared)) return "";
        String[] parts = prepared.split("(?<=[.!?])\\s+");
        return parts.length > 0 ? parts[0].trim() : prepared;
    }
    private List<String> splitIntoSteps(String text, int max) {
        String prepared = cleanTextForRetrieval(text);
        if (!StringUtils.hasText(prepared) || max <= 0) return List.of();
        String[] parts = prepared.split("(?<=[.!?])\\s+");
        List<String> steps = new ArrayList<>();
        for (String part : parts) {
            String step = cut(part, 180);
            if (!StringUtils.hasText(step)) continue;
            steps.add(step);
            if (steps.size() >= max) break;
        }
        return steps;
    }
    private String stripHtml(String v) { if (!StringUtils.hasText(v)) return ""; return v.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim(); }
    private String join(String... chunks) { StringBuilder b = new StringBuilder(); if (chunks == null) return ""; for (String c : chunks) { String t = trim(c); if (t == null) continue; if (!b.isEmpty()) b.append(". "); b.append(t); } return b.toString(); }
    private String cut(String text, int len) { String t = trim(text); if (t == null) return ""; String c = t.replaceAll("\\s+", " ").trim(); return c.length() <= len ? c : c.substring(0, Math.max(0, len - 3)) + "..."; }
    private String safe(Object v) { return v != null ? String.valueOf(v) : ""; }
    private String trim(String v) { if (!StringUtils.hasText(v)) return null; String t = v.trim(); return t.isEmpty() ? null : t; }
    private int toInt(Object v) { try { return Integer.parseInt(String.valueOf(v)); } catch (Exception ex) { return 0; } }
    private boolean isTrue(Object v) { if (v instanceof Boolean b) return b; String n = String.valueOf(v).trim().toLowerCase(Locale.ROOT); return "1".equals(n) || "true".equals(n) || "yes".equals(n) || "on".equals(n); }
    private String formatScore(double score) { return String.format(Locale.ROOT, "%.2f", Math.max(0d, Math.min(1d, score))); }
    private String buildKey(String question) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalize(question).getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { return Integer.toHexString(normalize(question).hashCode()); } }
    private int queryCount(String sql, Object... params) {
        try {
            Integer value = jdbcTemplate.queryForObject(sql, Integer.class, params);
            return value != null ? Math.max(0, value) : 0;
        } catch (Exception ex) {
            return 0;
        }
    }
    private double safeRate(int numerator, int denominator) {
        if (denominator <= 0) return 0d;
        return Math.max(0d, Math.min(1d, numerator / (double) denominator));
    }
    private List<Map<String, Object>> buildMonitoringAlerts(int inboundMessages,
                                                            int autoReplies,
                                                            int suggestOnly,
                                                            int escalations,
                                                            int operatorCorrections,
                                                            int rejectedSuggestions,
                                                            double autoReplyRate,
                                                            double escalationRate,
                                                            double correctionRate,
                                                            double rejectionRate) {
        List<Map<String, Object>> alerts = new ArrayList<>();
        if (inboundMessages == 0) {
            alerts.add(alert("info", "Нет входящих сообщений в анализируемом окне.", 0d, 1d));
            return alerts;
        }
        if (escalations >= 5 && escalationRate > 0.35d) {
            alerts.add(alert("warning", "Всплеск эскалаций: агент слишком часто передает оператору.", escalationRate, 0.35d));
        }
        if (operatorCorrections >= 3 && correctionRate > 0.25d) {
            alerts.add(alert("warning", "Рост коррекций: ответы агента часто требуют правок оператора.", correctionRate, 0.25d));
        }
        if (rejectedSuggestions >= 5 && rejectionRate > 0.40d) {
            alerts.add(alert("warning", "Высокий процент отклонённых подсказок операторами.", rejectionRate, 0.40d));
        }
        if ((autoReplies + suggestOnly) >= 10 && autoReplyRate < 0.05d) {
            alerts.add(alert("info", "Низкий auto-reply rate: проверьте пороги и качество retrieval.", autoReplyRate, 0.05d));
        }
        if (alerts.isEmpty()) {
            alerts.add(alert("ok", "Критичных отклонений в метриках AI-агента не обнаружено.", 0d, 0d));
        }
        return alerts;
    }
    private Map<String, Object> alert(String severity, String message, double value, double threshold) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("severity", severity);
        item.put("message", message);
        item.put("value", Math.round(value * 10000d) / 10000d);
        item.put("threshold", Math.round(threshold * 10000d) / 10000d);
        return item;
    }
    private double resolveAutoReplyThreshold() { return resolveDialogConfigDouble("ai_agent_auto_reply_threshold", AUTO_REPLY_THRESHOLD_DEFAULT, 0.2d, 0.95d); }
    private double resolveSuggestThreshold() { return resolveDialogConfigDouble("ai_agent_suggest_threshold", SUGGEST_THRESHOLD_DEFAULT, 0.1d, 0.95d); }
    private double resolveDifferenceThreshold() { return resolveDialogConfigDouble("ai_agent_difference_threshold", DIFFERENCE_THRESHOLD_DEFAULT, 0.1d, 0.9d); }
    private String resolveAgentMode() {
        return resolveDialogConfigString("ai_agent_mode", MODE_AUTO_REPLY, Set.of(MODE_AUTO_REPLY, MODE_ASSIST_ONLY, MODE_ESCALATE_ONLY));
    }
    private int resolveMaxAutoRepliesPerDialog() {
        return resolveDialogConfigInt("ai_agent_max_auto_replies_per_dialog", MAX_AUTO_REPLIES_PER_DIALOG_DEFAULT, 1, 20);
    }
    private int resolveAutoReplyWindowMinutes() {
        return resolveDialogConfigInt("ai_agent_auto_reply_window_minutes", AUTO_REPLY_WINDOW_MINUTES_DEFAULT, 1, 1440);
    }
    private int resolveAutoReplyCooldownSeconds() {
        return resolveDialogConfigInt("ai_agent_auto_reply_cooldown_seconds", AUTO_REPLY_COOLDOWN_SECONDS_DEFAULT, 0, 3600);
    }
    private AutoReplyGuard evaluateAutoReplyGuard(String ticketId) {
        int maxReplies = resolveMaxAutoRepliesPerDialog();
        int windowMinutes = resolveAutoReplyWindowMinutes();
        int cooldownSeconds = resolveAutoReplyCooldownSeconds();
        String windowExpr = "-" + windowMinutes + " minutes";
        Integer replyCount = null;
        try {
            replyCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM chat_history WHERE ticket_id = ? AND lower(COALESCE(sender,'')) = 'ai_agent' AND datetime(substr(COALESCE(timestamp,''),1,19)) >= datetime('now', ?)",
                    Integer.class,
                    ticketId,
                    windowExpr
            );
        } catch (Exception ignored) {
        }
        if (replyCount != null && replyCount >= maxReplies) {
            return new AutoReplyGuard(false, "limit_reached:" + replyCount + "/" + maxReplies);
        }
        if (cooldownSeconds <= 0) {
            return new AutoReplyGuard(true, null);
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT last_action, updated_at FROM ticket_ai_agent_state WHERE ticket_id = ? LIMIT 1",
                    ticketId
            );
            if (!rows.isEmpty()) {
                String lastAction = trim(safe(rows.get(0).get("last_action")));
                Instant updatedAt = parseInstant(rows.get(0).get("updated_at"));
                if ("auto_reply".equalsIgnoreCase(lastAction) && updatedAt != null) {
                    long elapsed = Duration.between(updatedAt, Instant.now()).getSeconds();
                    if (elapsed >= 0 && elapsed < cooldownSeconds) {
                        return new AutoReplyGuard(false, "cooldown:" + elapsed + "/" + cooldownSeconds + "s");
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return new AutoReplyGuard(true, null);
    }
    private DialogAiControl loadDialogControl(String ticketId) {
        String t = trim(ticketId);
        if (t == null) return DialogAiControl.DEFAULT;
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT ai_disabled, auto_reply_blocked, reason, updated_by, updated_at FROM ticket_ai_agent_dialog_control WHERE ticket_id = ? LIMIT 1",
                    t
            );
            if (rows.isEmpty()) return DialogAiControl.DEFAULT;
            Map<String, Object> row = rows.get(0);
            return new DialogAiControl(
                    isTrue(row.get("ai_disabled")),
                    isTrue(row.get("auto_reply_blocked")),
                    trim(safe(row.get("reason"))),
                    trim(safe(row.get("updated_by"))),
                    trim(safe(row.get("updated_at")))
            );
        } catch (Exception ex) {
            return DialogAiControl.DEFAULT;
        }
    }
    private String encodeSourceHits(List<AiSuggestion> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) return null;
        List<Map<String, Object>> payload = new ArrayList<>();
        for (AiSuggestion s : suggestions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", s.source);
            row.put("title", s.title);
            row.put("score", Math.round(Math.max(0d, Math.min(1d, s.score)) * 1000d) / 1000d);
            row.put("memory_key", s.memoryKey);
            payload.add(row);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
    private Instant parseInstant(Object value) {
        String raw = trim(value != null ? String.valueOf(value) : null);
        if (raw == null) return null;
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(raw).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            String normalized = raw.replace(' ', 'T');
            if (normalized.length() == 19) {
                return LocalDateTime.parse(normalized).toInstant(ZoneOffset.UTC);
            }
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }
    private void recordAiEvent(String ticketId,
                               String eventType,
                               String actor,
                               String decisionType,
                               String decisionReason,
                               String source,
                               Double score,
                               String detail,
                               Map<String, Object> payload) {
        String type = trim(eventType);
        if (type == null) return;
        try {
            jdbcTemplate.update("""
                    INSERT INTO ai_agent_event_log(
                        ticket_id, event_type, actor, decision_type, decision_reason, source, score, detail, payload_json, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    trim(ticketId),
                    cut(type, 80),
                    trim(actor),
                    trim(decisionType),
                    trim(decisionReason),
                    trim(source),
                    score,
                    cut(detail, 2000),
                    payload != null && !payload.isEmpty() ? cut(toJson(payload), 5000) : null
            );
        } catch (Exception ex) {
            log.debug("Failed to record ai event '{}' for ticket {}: {}", type, ticketId, ex.getMessage());
        }
    }
    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }
    private int resolveDialogConfigInt(String key, int fallback, int min, int max) {
        try {
            Object raw = sharedConfigService.loadSettings().get("dialog_config");
            if (!(raw instanceof Map<?, ?> map)) return fallback;
            Object value = map.get(key);
            if (value == null) return fallback;
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (Exception ex) {
            return fallback;
        }
    }
    private String resolveDialogConfigString(String key, String fallback, Set<String> allowedValues) {
        try {
            Object raw = sharedConfigService.loadSettings().get("dialog_config");
            if (!(raw instanceof Map<?, ?> map)) return fallback;
            Object rawValue = map.get(key);
            if (rawValue == null) return fallback;
            String value = trim(String.valueOf(rawValue));
            if (value == null) return fallback;
            String normalized = value.toLowerCase(Locale.ROOT);
            return allowedValues.contains(normalized) ? normalized : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }
    private double resolveDialogConfigDouble(String key, double fallback, double min, double max) {
        try {
            Object raw = sharedConfigService.loadSettings().get("dialog_config");
            if (!(raw instanceof Map<?,?> map)) return fallback;
            Object val = map.get(key);
            if (val == null) return fallback;
            double parsed = Double.parseDouble(String.valueOf(val).trim());
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) return fallback;
            return Math.max(min, Math.min(max, parsed));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private record AutoReplyGuard(boolean allowed, String reason) {}
    private record DialogAiControl(boolean aiDisabled,
                                   boolean autoReplyBlocked,
                                   String reason,
                                   String updatedBy,
                                   String updatedAt) {
        static final DialogAiControl DEFAULT = new DialogAiControl(false, false, null, null, null);
    }

    private static final class AiSuggestion {
        final String source; final String title; final String snippet; final double score; final String memoryKey;
        AiSuggestion(String source, String title, String snippet, double score, String memoryKey) {
            this.source = source; this.title = title; this.snippet = snippet; this.score = score; this.memoryKey = memoryKey;
        }
    }
}

