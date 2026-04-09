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
    private static final Pattern ENTITY_HINT_PATTERN = Pattern.compile("(#?[\\p{L}]{2,}[\\-_]?[0-9]{2,}|\\+?[0-9][0-9\\-()\\s]{6,}|[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})");
    private static final Set<String> STOP = Set.of("Р С‘","Р Р†","Р Р…Р В°","Р Р…Р Вµ","РЎвЂЎРЎвЂљР С•","Р С”Р В°Р С”","Р Т‘Р В»РЎРЏ","Р С‘Р В»Р С‘","Р С—Р С•","Р С‘Р В·","Р С”","РЎС“","Р С•","Р С•Р В±","the","a","an","to","of","in","on","for","and","or","is","are","be");
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
    private static final Set<String> JUNK_MEDIA_TYPES = Set.of(
            "animation",
            "sticker",
            "emoji",
            "reaction",
            "gif",
            "system_notification"
    );
    private static final Set<String> ACTIONABLE_MEDIA_TYPES = Set.of(
            "photo",
            "image",
            "video",
            "video_note",
            "voice",
            "audio",
            "document",
            "file"
    );

    private final JdbcTemplate jdbcTemplate;
    private final DialogService dialogService;
    private final DialogReplyService dialogReplyService;
    private final NotificationService notificationService;
    private final SharedConfigService sharedConfigService;
    private final ObjectMapper objectMapper;

    public DialogAiAssistantService(JdbcTemplate jdbcTemplate,
                                    DialogService dialogService,
                                    DialogReplyService dialogReplyService,
                                    NotificationService notificationService,
                                    SharedConfigService sharedConfigService,
                                    ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialogService = dialogService;
        this.dialogReplyService = dialogReplyService;
        this.notificationService = notificationService;
        this.sharedConfigService = sharedConfigService;
        this.objectMapper = objectMapper;
    }

    public void processIncomingClientMessage(String ticketId, String message) {
        processIncomingClientMessage(ticketId, message, null, null);
    }

    public void processIncomingClientMessage(String ticketId, String message, String messageType, String attachment) {
        String t = trim(ticketId);
        if (t == null) return;
        IncomingClientPayload payload = normalizeIncomingPayload(t, message, messageType, attachment);
        if (payload == null) {
            clearProcessing(t, "ignored_media_noise", null, "ignored", "junk_or_noise_media", null);
            recordAiEvent(t, "ai_agent_message_ignored", null, "ignored", "junk_or_noise_media", null, null, "Ignored non-actionable media/noise message", null);
            return;
        }
        String m = payload.message();

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
            markProcessing(t, "manual_requested", null, null, "escalate", "manual_requested", null, resolveAgentMode());
            notifyOperatorsEscalation(t, m, "Client requested operator.");
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
            markProcessing(t, "no_match", null, "No relevant sources found.", "escalate", "no_match", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "AI agent did not find a relevant answer.");
            recordAiEvent(t, "ai_agent_escalated", null, "escalate", "no_match", null, null, "No relevant sources", Map.of(
                    "source_hits", sourceHits
            ));
            return;
        }

        AiSuggestion top = suggestions.get(0);
        double autoReplyThreshold = resolveAutoReplyThreshold();
        double suggestThreshold = resolveSuggestThreshold();

        if (MODE_ESCALATE_ONLY.equals(mode)) {
            markProcessing(t, "escalated", top, "Mode escalate_only is enabled", "escalate", "mode_escalate_only", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "AI mode is escalate_only.");
            recordAiEvent(t, "ai_agent_escalated", null, "escalate", "mode_escalate_only", top.source, top.score, "Escalate-only mode", Map.of(
                    "source_hits", sourceHits
            ));
            return;
        }

        if (top.score < suggestThreshold) {
            markProcessing(t, "low_confidence", top, "Low confidence (" + formatScore(top.score) + ").", "escalate", "below_suggest_threshold", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "Low confidence score: " + formatScore(top.score));
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
            markProcessing(t, "auto_reply_suppressed", top, guard.reason(), "suppressed", "loop_guard", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "Auto-reply suppressed by loop guard: " + guard.reason());
            recordAiEvent(t, "ai_agent_decision_made", null, "suppressed", "loop_guard", top.source, top.score, guard.reason(), Map.of(
                    "source_hits", sourceHits
            ));
            return;
        }

        String reply = buildAutoReply(top);
        DialogReplyService.DialogReplyResult result = dialogReplyService.sendReply(t, reply, null, null, "ai_agent");
        if (!result.success()) {
            markProcessing(t, "send_failed", top, result.error(), "escalate", "send_failed", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "Failed to send AI reply: " + result.error());
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
                    "AI suggestion for ticket " + t + " differs from operator reply. " +
                            "Please refine learning markup: 1) which client message describes the issue; " +
                            "2) which operator message is the correct solution.",
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
        IncomingClientPayload payload = loadLastClientPayload(t);
        String lastClient = payload != null ? payload.message() : null;
        if (!StringUtils.hasText(lastClient)) return List.of();
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : DEFAULT_SUGGESTION_LIMIT, 8));
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiSuggestion s : findSuggestions(t, lastClient, safeLimit)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", s.source);
            item.put("title", s.title);
            item.put("score", s.score);
            item.put("score_label", formatScore(s.score));
            item.put("snippet", s.snippet);
            item.put("reply", buildOperatorReplySuggestion(s));
            item.put("explain", buildSuggestionExplain(s));
            result.add(item);
        }
        return result;
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
        try {
            Map<String, Object> row = loadPendingReviewRowByTicket(t);
            if (row == null) return Map.of("pending", false);
            String key = trim(safe(row.get("query_key")));
            if (key == null) return Map.of("pending", false);
            List<Map<String, Object>> problemCandidates = loadReviewMessageCandidates(t, false, 20);
            List<Map<String, Object>> solutionCandidates = loadReviewMessageCandidates(t, true, 20);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("pending", true);
            payload.put("ticket_id", t);
            payload.put("query_key", key);
            payload.put("query_text", safe(row.get("query_text")));
            payload.put("current_solution", safe(row.get("solution_text")));
            payload.put("pending_solution", safe(row.get("pending_solution_text")));
            payload.put("updated_at", safe(row.get("updated_at")));
            payload.put("problem_message_candidates", problemCandidates);
            payload.put("solution_message_candidates", solutionCandidates);
            payload.put("selected_problem_message_id", resolveReviewMessageSelection(problemCandidates, safe(row.get("query_text"))));
            payload.put("selected_solution_message_id", resolveReviewMessageSelection(solutionCandidates, safe(row.get("pending_solution_text"))));
            return payload;
        } catch (Exception ex) {
            return Map.of("pending", false);
        }
    }

    public boolean approvePendingReview(String ticketId, String operator) {
        return approvePendingReview(ticketId, operator, null, null);
    }

    public boolean approvePendingReview(String ticketId, String operator, Long clientMessageId, Long operatorMessageId) {
        String t = trim(ticketId);
        if (t == null) return false;
        try {
            Map<String, Object> reviewRow = loadPendingReviewRowByTicket(t);
            if (reviewRow == null) return false;
            String sourceKey = trim(safe(reviewRow.get("query_key")));
            if (sourceKey == null) return false;

            String selectedClientMessage = loadReviewMessageText(t, clientMessageId, false);
            String selectedOperatorMessage = loadReviewMessageText(t, operatorMessageId, true);

            String resolvedQueryText = trim(selectedClientMessage);
            if (resolvedQueryText == null) resolvedQueryText = trim(safe(reviewRow.get("query_text")));
            if (resolvedQueryText == null) resolvedQueryText = loadLastClientMessage(t);

            String resolvedSolutionText = trim(selectedOperatorMessage);
            if (resolvedSolutionText == null) resolvedSolutionText = trim(safe(reviewRow.get("pending_solution_text")));

            if (resolvedQueryText == null || resolvedSolutionText == null) {
                return false;
            }

            String targetKey = buildKey(resolvedQueryText);
            String safeOperator = trim(operator);
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
                        cut(resolvedQueryText, 600),
                        cut(resolvedSolutionText, 2000),
                        safeOperator,
                        t,
                        cut(resolvedQueryText, 600),
                        sourceKey
                );
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
                        cut(resolvedQueryText, 600),
                        cut(resolvedSolutionText, 2000),
                        safeOperator,
                        t,
                        cut(resolvedQueryText, 600),
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
                            cut(resolvedQueryText, 600),
                            cut(resolvedSolutionText, 2000),
                            safeOperator,
                            t,
                            cut(resolvedQueryText, 600)
                    );
                }
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
            }
            if (sourceUpdated > 0) {
                clearProcessing(t, "operator_correction_approved", null);
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("query_key", targetKey);
                payload.put("source_query_key", sourceKey);
                payload.put("message_mapping_updated", !targetKey.equals(sourceKey));
                if (clientMessageId != null && clientMessageId > 0) payload.put("client_message_id", clientMessageId);
                if (operatorMessageId != null && operatorMessageId > 0) payload.put("operator_message_id", operatorMessageId);
                recordAiEvent(t, "ai_agent_correction_approved", safeOperator, "review", "approved", null, null, null, payload);
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
        try {
            Map<String, Object> reviewRow = loadPendingReviewRowByTicket(t);
            if (reviewRow == null) return false;
            String key = trim(safe(reviewRow.get("query_key")));
            if (key == null) return false;
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

    private Map<String, Object> loadPendingReviewRowByTicket(String ticketId) {
        String t = trim(ticketId);
        if (t == null) {
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
                t
        );
        if (!rows.isEmpty()) {
            return rows.get(0);
        }
        String lastClient = loadLastClientMessage(t);
        if (!StringUtils.hasText(lastClient)) {
            return null;
        }
        String key = buildKey(lastClient);
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
        String t = trim(ticketId);
        if (t == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String senderFilter = operatorMessages
                ? "IN ('operator','support','admin','system')"
                : "NOT IN ('operator','support','admin','system','ai_agent')";
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, sender, message, timestamp FROM chat_history WHERE ticket_id = ? AND lower(COALESCE(sender,'')) " + senderFilter + " AND message IS NOT NULL AND trim(message) <> '' ORDER BY id DESC LIMIT ?",
                    t,
                    safeLimit
            );
            List<Map<String, Object>> out = new ArrayList<>();
            for (int i = rows.size() - 1; i >= 0; i--) {
                Map<String, Object> row = rows.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", toLong(row.get("id")));
                item.put("sender", safe(row.get("sender")));
                item.put("text", safe(row.get("message")));
                item.put("timestamp", safe(row.get("timestamp")));
                out.add(item);
            }
            return out;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Long resolveReviewMessageSelection(List<Map<String, Object>> candidates, String targetText) {
        String target = trim(targetText);
        if (target == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        for (Map<String, Object> candidate : candidates) {
            if (target.equals(trim(safe(candidate.get("text"))))) {
                return toLong(candidate.get("id"));
            }
        }
        return toLong(candidates.get(candidates.size() - 1).get("id"));
    }

    private String loadReviewMessageText(String ticketId, Long messageId, boolean operatorMessage) {
        String t = trim(ticketId);
        if (t == null || messageId == null || messageId <= 0) {
            return null;
        }
        String senderFilter = operatorMessage
                ? "IN ('operator','support','admin','system')"
                : "NOT IN ('operator','support','admin','system','ai_agent')";
        try {
            return jdbcTemplate.query(
                    "SELECT message FROM chat_history WHERE ticket_id = ? AND id = ? AND lower(COALESCE(sender,'')) " + senderFilter + " AND message IS NOT NULL AND trim(message) <> '' LIMIT 1",
                    rs -> rs.next() ? trim(rs.getString("message")) : null,
                    t,
                    messageId
            );
        } catch (Exception ex) {
            return null;
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
            if (s > 0d) out.add(new AiSuggestion("memory", "Р СџРЎР‚Р С•Р Р†Р ВµРЎР‚Р ВµР Р…Р Р…Р С•Р Вµ РЎР‚Р ВµРЎв‚¬Р ВµР Р…Р С‘Р Вµ", cut(st, 320), s, trim(key)));
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
            if (s > 0d) out.add(new AiSuggestion("knowledge", StringUtils.hasText(title) ? title : "Р РЋРЎвЂљР В°РЎвЂљРЎРЉРЎРЏ Р В±Р В°Р В·РЎвЂ№ Р В·Р Р…Р В°Р Р…Р С‘Р в„–", cut(text, 280), s, null));
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
            if (s > 0d) out.add(new AiSuggestion("tasks", StringUtils.hasText(title) ? title : "Р СџР С•РЎвЂ¦Р С•Р В¶Р В°РЎРЏ Р В·Р В°Р Т‘Р В°РЎвЂЎР В°", cut(text, 280), s, null));
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
            if (s > 0d) out.add(new AiSuggestion("history", StringUtils.hasText(ticket) ? "Р СџР С•РЎвЂ¦Р С•Р В¶Р С‘Р в„– Р Т‘Р С‘Р В°Р В»Р С•Р С– #" + ticket : "Р СџР С•РЎвЂ¦Р С•Р В¶Р С‘Р в„– Р Т‘Р С‘Р В°Р В»Р С•Р С–", cut(msg, 260), s, null));
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
                    StringUtils.hasText(prevTicket) ? "Р ВРЎРѓРЎвЂљР С•РЎР‚Р С‘РЎРЏ Р В·Р В°РЎРЏР Р†Р С‘РЎвЂљР ВµР В»РЎРЏ #" + prevTicket : "Р ВРЎРѓРЎвЂљР С•РЎР‚Р С‘РЎРЏ Р В·Р В°РЎРЏР Р†Р С‘РЎвЂљР ВµР В»РЎРЏ",
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
            insertSolutionMemoryHistory(
                    key,
                    trim(operator),
                    "learning",
                    "insert",
                    null,
                    null,
                    false,
                    cut(q, 600),
                    cut(r, 2000),
                    false,
                    "learned_from_operator_reply"
            );
            return key;
        }
        Map<String, Object> ex = rows.get(0);
        String sol = trim(safe(ex.get("solution_text"))), pending = trim(safe(ex.get("pending_solution_text")));
        boolean review = isTrue(ex.get("review_required"));
        if (review && pending != null && !isMeaningfullyDifferent(pending, r)) {
            jdbcTemplate.update("UPDATE ai_agent_solution_memory SET query_text=?,solution_text=?,review_required=0,pending_solution_text=NULL,times_confirmed=COALESCE(times_confirmed,0)+1,last_operator=?,last_ticket_id=?,last_client_message=?,updated_at=CURRENT_TIMESTAMP WHERE query_key=?", cut(q, 600), cut(r, 2000), trim(operator), trim(ticketId), cut(q, 600), key);
            insertSolutionMemoryHistory(
                    key,
                    trim(operator),
                    "learning",
                    "confirm_pending",
                    cut(q, 600),
                    cut(sol, 2000),
                    true,
                    cut(q, 600),
                    cut(r, 2000),
                    false,
                    "pending_solution_confirmed"
            );
            return key;
        }
        if (sol != null && isMeaningfullyDifferent(sol, r)) {
            jdbcTemplate.update("UPDATE ai_agent_solution_memory SET query_text=?,review_required=1,pending_solution_text=?,times_corrected=COALESCE(times_corrected,0)+1,last_operator=?,last_ticket_id=?,last_client_message=?,updated_at=CURRENT_TIMESTAMP WHERE query_key=?", cut(q, 600), cut(r, 2000), trim(operator), trim(ticketId), cut(q, 600), key);
            insertSolutionMemoryHistory(
                    key,
                    trim(operator),
                    "learning",
                    "correction_requested",
                    cut(q, 600),
                    cut(sol, 2000),
                    false,
                    cut(q, 600),
                    cut(r, 2000),
                    true,
                    "operator_reply_differs"
            );
            return key;
        }
        jdbcTemplate.update("UPDATE ai_agent_solution_memory SET query_text=?,solution_text=?,review_required=0,pending_solution_text=NULL,times_confirmed=COALESCE(times_confirmed,0)+1,last_operator=?,last_ticket_id=?,last_client_message=?,updated_at=CURRENT_TIMESTAMP WHERE query_key=?", cut(q, 600), cut(r, 2000), trim(operator), trim(ticketId), cut(q, 600), key);
        insertSolutionMemoryHistory(
                key,
                trim(operator),
                "learning",
                "update_confirmed",
                cut(q, 600),
                cut(sol, 2000),
                review,
                cut(q, 600),
                cut(r, 2000),
                false,
                "operator_reply_confirmed"
        );
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

    public List<Map<String, Object>> loadSolutionMemory(Integer limit, String query) {
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : 100, 500));
        String q = trim(query);
        try {
            if (q == null) {
                return jdbcTemplate.queryForList(
                        """
                        SELECT query_key, query_text, solution_text, pending_solution_text, review_required,
                               times_used, times_confirmed, times_corrected, last_operator, last_ticket_id,
                               created_at, updated_at
                          FROM ai_agent_solution_memory
                         ORDER BY COALESCE(updated_at, created_at) DESC
                         LIMIT ?
                        """,
                        safeLimit
                );
            }
            String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
            return jdbcTemplate.queryForList(
                    """
                    SELECT query_key, query_text, solution_text, pending_solution_text, review_required,
                           times_used, times_confirmed, times_corrected, last_operator, last_ticket_id,
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
        String key = trim(queryKey);
        String q = trim(queryText);
        String s = trim(solutionText);
        if (key == null || q == null || s == null) {
            return false;
        }
        boolean requireReview = reviewRequired != null && reviewRequired;
        try {
            Map<String, Object> before = loadSolutionMemoryByKey(key);
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
                    cut(q, 600),
                    cut(s, 2000),
                    requireReview ? 1 : 0,
                    requireReview ? 1 : 0,
                    trim(operator),
                    key
            );
            if (updated > 0) {
                insertSolutionMemoryHistory(
                        key,
                        trim(operator),
                        "manual",
                        "update",
                        safe(before.get("query_text")),
                        safe(before.get("solution_text")),
                        isTrue(before.get("review_required")),
                        cut(q, 600),
                        cut(s, 2000),
                        requireReview,
                        "manual_edit"
                );
                return true;
            }
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    public List<Map<String, Object>> loadSolutionMemoryHistory(String queryKey, Integer limit) {
        String key = trim(queryKey);
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
        String key = trim(queryKey);
        if (key == null || historyId == null || historyId <= 0) {
            return false;
        }
        try {
            Map<String, Object> before = loadSolutionMemoryByKey(key);
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
                            "old_query_text", trim(rs.getString("old_query_text")),
                            "old_solution_text", trim(rs.getString("old_solution_text")),
                            "old_review_required", rs.getInt("old_review_required")) : null,
                    historyId,
                    key
            );
            if (history == null) {
                return false;
            }
            String rollbackQuery = trim(safe(history.get("old_query_text")));
            String rollbackSolution = trim(safe(history.get("old_solution_text")));
            boolean rollbackReview = isTrue(history.get("old_review_required"));
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
                    cut(rollbackQuery, 600),
                    cut(rollbackSolution, 2000),
                    rollbackReview ? 1 : 0,
                    trim(operator),
                    key
            );
            if (updated > 0) {
                insertSolutionMemoryHistory(
                        key,
                        trim(operator),
                        "manual",
                        "rollback",
                        safe(before.get("query_text")),
                        safe(before.get("solution_text")),
                        isTrue(before.get("review_required")),
                        cut(rollbackQuery, 600),
                        cut(rollbackSolution, 2000),
                        rollbackReview,
                        "rollback_to_history_id=" + historyId
                );
                return true;
            }
            return false;
        } catch (Exception ex) {
            return false;
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
                "Проверьте значения escalation_rate и correction_rate.",
                "Если escalation_rate > 35%, временно переключите ai_agent_mode в assist_only.",
                "Если correction_rate > 25%, обновите базу знаний и скорректируйте шаблоны ответов.",
                "При массовых ошибках отключите AI для канала и назначьте срочный разбор."
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
    private IncomingClientPayload normalizeIncomingPayload(String ticketId, String message, String messageType, String attachment) {
        IncomingClientPayload incoming = new IncomingClientPayload(trim(message), normalize(trim(messageType)), trim(attachment));
        IncomingClientPayload payload = incoming;
        if (payload.message() == null && payload.type() == null && payload.attachment() == null) {
            payload = loadLastClientPayload(ticketId);
        }
        if (payload == null) {
            return null;
        }
        if (isJunkMediaType(payload.type())) {
            return null;
        }
        String text = trim(payload.message());
        if (payload.attachment() != null && (isActionableMediaType(payload.type()) || text == null)) {
            text = trim(buildMediaContext(payload.type(), payload.attachment(), text));
        }
        if (isNoiseClientMessage(text)) {
            return null;
        }
        return new IncomingClientPayload(text, payload.type(), payload.attachment());
    }

    private IncomingClientPayload loadLastClientPayload(String ticketId) {
        String t = trim(ticketId);
        if (t == null) {
            return null;
        }
        try {
            return jdbcTemplate.query(
                    """
                    SELECT message, message_type, attachment
                      FROM chat_history
                     WHERE ticket_id = ?
                       AND lower(COALESCE(sender, '')) NOT IN ('operator','support','admin','system','ai_agent')
                     ORDER BY id DESC
                     LIMIT 1
                    """,
                    rs -> rs.next()
                            ? new IncomingClientPayload(
                                    trim(rs.getString("message")),
                                    normalize(trim(rs.getString("message_type"))),
                                    trim(rs.getString("attachment")))
                            : null,
                    t
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isJunkMediaType(String type) {
        String normalized = normalize(type);
        return normalized != null && JUNK_MEDIA_TYPES.contains(normalized);
    }

    private boolean isActionableMediaType(String type) {
        String normalized = normalize(type);
        return normalized != null && ACTIONABLE_MEDIA_TYPES.contains(normalized);
    }

    private boolean isNoiseClientMessage(String text) {
        String normalized = trim(text);
        if (normalized == null) {
            return true;
        }
        if (normalized.length() <= 2 && !normalized.chars().anyMatch(Character::isLetterOrDigit)) {
            return true;
        }
        boolean hasAlphaNum = normalized.chars().anyMatch(Character::isLetterOrDigit);
        if (!hasAlphaNum) {
            return true;
        }
        Set<String> tokens = tokenize(normalized);
        Set<String> entities = extractEntityHints(normalized);
        return tokens.isEmpty() && entities.isEmpty();
    }

    private String buildMediaContext(String messageType, String attachment, String caption) {
        String type = trim(messageType);
        String mediaType = type != null ? type : "media";
        String fileName = fileNameFromAttachment(attachment);
        String cap = trim(caption);
        if (cap != null) {
            return "client sent " + mediaType + " " + fileName + ". caption: " + cap;
        }
        return "client sent " + mediaType + " " + fileName;
    }

    private String fileNameFromAttachment(String attachment) {
        String value = trim(attachment);
        if (value == null) {
            return "attachment";
        }
        String normalized = value.replace('\\', '/');
        int idx = normalized.lastIndexOf('/');
        String raw = idx >= 0 ? normalized.substring(idx + 1) : normalized;
        String trimmed = trim(raw);
        return trimmed != null ? trimmed : "attachment";
    }

    private Map<String, Object> loadSolutionMemoryByKey(String queryKey) {
        String key = trim(queryKey);
        if (key == null) {
            return null;
        }
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT query_text, solution_text, review_required FROM ai_agent_solution_memory WHERE query_key = ? LIMIT 1",
                    key
            );
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception ex) {
            return null;
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
        try {
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
        } catch (Exception ex) {
            log.debug("Failed to insert ai solution memory history for {}: {}", queryKey, ex.getMessage());
        }
    }

    private String loadLastClientMessage(String ticketId) { return jdbcTemplate.query("SELECT message FROM chat_history WHERE ticket_id=? AND lower(sender) NOT IN ('operator','support','admin','system','ai_agent') AND message IS NOT NULL AND trim(message)<>'' ORDER BY id DESC LIMIT 1", rs -> rs.next() ? trim(rs.getString("message")) : null, ticketId); }
    private String buildAutoReply(AiSuggestion s) {
        String body = cleanTextForRetrieval(s != null ? s.snippet : null);
        if (!StringUtils.hasText(body)) {
            body = "Р СњР Вµ РЎС“Р Т‘Р В°Р В»Р С•РЎРѓРЎРЉ Р С—Р С•Р Т‘Р С–Р С•РЎвЂљР С•Р Р†Р С‘РЎвЂљРЎРЉ Р С•РЎвЂљР Р†Р ВµРЎвЂљ Р С—Р С• РЎРЊРЎвЂљР С•Р СРЎС“ Р В·Р В°Р С—РЎР‚Р С•РЎРѓРЎС“.";
        }
        List<String> steps = splitIntoSteps(body, 3);
        StringBuilder reply = new StringBuilder();
        reply.append("Р С›РЎвЂљР Р†Р ВµРЎвЂљ: ").append(cut(firstSentence(body), 220)).append("\n\n");
        reply.append("Р В§РЎвЂљР С• РЎРѓР Т‘Р ВµР В»Р В°РЎвЂљРЎРЉ:\n");
        if (steps.isEmpty()) {
            reply.append("1. ").append(cut(body, 280)).append("\n");
        } else {
            for (int i = 0; i < steps.size(); i++) {
                reply.append(i + 1).append(". ").append(steps.get(i)).append("\n");
            }
        }
        reply.append("\nР вЂўРЎРѓР В»Р С‘ РЎРЊРЎвЂљР С• Р Р…Р Вµ Р С—Р С•Р СР С•Р С–Р В»Р С•, Р С—Р С•Р Т‘Р С”Р В»РЎР‹РЎвЂЎР С‘Р С Р С•Р С—Р ВµРЎР‚Р В°РЎвЂљР С•РЎР‚Р В° Р С‘ РЎС“РЎвЂљР С•РЎвЂЎР Р…Р С‘Р С Р Т‘Р ВµРЎвЂљР В°Р В»Р С‘.");
        return reply.toString();
    }
    private String buildOperatorReplySuggestion(AiSuggestion s) {
        String sourceLabel = switch (s.source) {
            case "memory" -> "Р С—Р В°Р СРЎРЏРЎвЂљРЎРЉ РЎР‚Р ВµРЎв‚¬Р ВµР Р…Р С‘Р в„–";
            case "knowledge" -> "Р В±Р В°Р В·Р В° Р В·Р Р…Р В°Р Р…Р С‘Р в„–";
            case "tasks" -> "Р С—Р С•РЎвЂ¦Р С•Р В¶Р С‘Р Вµ Р В·Р В°Р Т‘Р В°РЎвЂЎР С‘";
            case "history" -> "Р С‘РЎРѓРЎвЂљР С•РЎР‚Р С‘РЎРЏ Р Т‘Р С‘Р В°Р В»Р С•Р С–Р С•Р Р†";
            case "applicant_history" -> "Р С‘РЎРѓРЎвЂљР С•РЎР‚Р С‘РЎРЏ Р В·Р В°РЎРЏР Р†Р С‘РЎвЂљР ВµР В»РЎРЏ";
            default -> "РЎР‚Р В°Р В±Р С•РЎвЂЎР С‘Р Вµ Р С‘РЎРѓРЎвЂљР С•РЎвЂЎР Р…Р С‘Р С”Р С‘";
        };
        String prepared = buildAutoReply(s);
        return "Р СџР С• Р С‘РЎРѓРЎвЂљР С•РЎвЂЎР Р…Р С‘Р С”РЎС“ \"" + sourceLabel + "\" Р С—РЎР‚Р ВµР Т‘Р В»Р В°Р С–Р В°Р ВµРЎвЂљРЎРѓРЎРЏ:\n\n" + prepared;
    }
    private String buildSuggestionExplain(AiSuggestion s) {
        String sourceExplain = switch (String.valueOf(s.source).toLowerCase(Locale.ROOT)) {
            case "memory" -> "Р С›РЎРѓР Р…Р С•Р Р†Р В°Р Р…Р С• Р Р…Р В° РЎР‚Р В°Р Р…Р ВµР Вµ Р С—Р С•Р Т‘РЎвЂљР Р†Р ВµРЎР‚Р В¶Р Т‘Р ВµР Р…Р Р…Р С•Р С РЎР‚Р ВµРЎв‚¬Р ВµР Р…Р С‘Р С‘.";
            case "knowledge" -> "Р С›РЎРѓР Р…Р С•Р Р†Р В°Р Р…Р С• Р Р…Р В° РЎРѓРЎвЂљР В°РЎвЂљРЎРЉР Вµ Р С‘Р В· Р В±Р В°Р В·РЎвЂ№ Р В·Р Р…Р В°Р Р…Р С‘Р в„–.";
            case "tasks" -> "Р С›РЎРѓР Р…Р С•Р Р†Р В°Р Р…Р С• Р Р…Р В° Р С—Р С•РЎвЂ¦Р С•Р В¶Р С‘РЎвЂ¦ Р В·Р В°Р Т‘Р В°РЎвЂЎР В°РЎвЂ¦.";
            case "history" -> "Р С›РЎРѓР Р…Р С•Р Р†Р В°Р Р…Р С• Р Р…Р В° Р С—Р С•РЎвЂ¦Р С•Р В¶Р С‘РЎвЂ¦ Р Т‘Р С‘Р В°Р В»Р С•Р С–Р В°РЎвЂ¦.";
            case "applicant_history" -> "Р С›РЎРѓР Р…Р С•Р Р†Р В°Р Р…Р С• Р Р…Р В° Р С—РЎР‚Р ВµР Т‘РЎвЂ№Р Т‘РЎС“РЎвЂ°Р С‘РЎвЂ¦ Р С•Р В±РЎР‚Р В°РЎвЂ°Р ВµР Р…Р С‘РЎРЏРЎвЂ¦ Р В·Р В°РЎРЏР Р†Р С‘РЎвЂљР ВµР В»РЎРЏ.";
            default -> "Р С›РЎРѓР Р…Р С•Р Р†Р В°Р Р…Р С• Р Р…Р В° РЎР‚Р ВµР В»Р ВµР Р†Р В°Р Р…РЎвЂљР Р…РЎвЂ№РЎвЂ¦ Р С‘РЎРѓРЎвЂљР С•РЎвЂЎР Р…Р С‘Р С”Р В°РЎвЂ¦.";
        };
        return sourceExplain + " Р Р€РЎР‚Р С•Р Р†Р ВµР Р…РЎРЉ РЎС“Р Р†Р ВµРЎР‚Р ВµР Р…Р Р…Р С•РЎРѓРЎвЂљР С‘: " + formatScore(s.score) + ".";
    }
    private void notifyOperatorsEscalation(String ticketId, String msg, String reason) { String t = "AI-Р В°Р С–Р ВµР Р…РЎвЂљ РЎРЊРЎРѓР С”Р В°Р В»Р С‘РЎР‚Р С•Р Р†Р В°Р В» Р С•Р В±РЎР‚Р В°РЎвЂ°Р ВµР Р…Р С‘Р Вµ " + ticketId + ". " + reason; if (StringUtils.hasText(msg)) t += " Р вЂ™Р С•Р С—РЎР‚Р С•РЎРѓ Р С”Р В»Р С‘Р ВµР Р…РЎвЂљР В°: " + cut(msg, 140); notificationService.notifyAllOperators(t, "/dialogs?ticketId=" + ticketId, null); }
    private boolean isAgentEnabled() { try { Object d = sharedConfigService.loadSettings().get("dialog_config"); if (d instanceof Map<?,?> m) { Object e = m.get("ai_agent_enabled"); if (e instanceof Boolean b) return b; String n = String.valueOf(e).trim().toLowerCase(Locale.ROOT); return !"false".equals(n) && !"0".equals(n) && !"off".equals(n); } } catch (Exception ex) { log.debug("ai_agent_enabled read failed: {}", ex.getMessage()); } return true; }
    private boolean requiresHumanImmediately(String m) { String n = String.valueOf(m).toLowerCase(Locale.ROOT); return n.contains("Р С•Р С—Р ВµРЎР‚Р В°РЎвЂљР С•РЎР‚") || n.contains("РЎвЂЎР ВµР В»Р С•Р Р†Р ВµР С”") || n.contains("Р СР ВµР Р…Р ВµР Т‘Р В¶Р ВµРЎР‚") || n.contains("Р С—Р С•Р В·Р Р†Р С•Р Р…Р С‘РЎвЂљР Вµ"); }
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
    private Long toLong(Object v) { try { if (v == null) return null; return Long.parseLong(String.valueOf(v)); } catch (Exception ex) { return null; } }
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
            alerts.add(alert("info", "Нет входящих сообщений в выбранном окне.", 0d, 1d));
            return alerts;
        }
        if (escalations >= 5 && escalationRate > 0.35d) {
            alerts.add(alert("warning", "Высокий escalation rate: чаще подключайте оператора и проверяйте источники.", escalationRate, 0.35d));
        }
        if (operatorCorrections >= 3 && correctionRate > 0.25d) {
            alerts.add(alert("warning", "Высокий correction rate: AI-ответы часто требуют правок оператора.", correctionRate, 0.25d));
        }
        if (rejectedSuggestions >= 5 && rejectionRate > 0.40d) {
            alerts.add(alert("warning", "Операторы часто отклоняют подсказки AI.", rejectionRate, 0.40d));
        }
        if ((autoReplies + suggestOnly) >= 10 && autoReplyRate < 0.05d) {
            alerts.add(alert("info", "Низкий auto-reply rate: проверьте пороги и качество retrieval.", autoReplyRate, 0.05d));
        }
        if (alerts.isEmpty()) {
            alerts.add(alert("ok", "Показатели стабильны, критичных отклонений не обнаружено.", 0d, 0d));
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
    private record IncomingClientPayload(String message, String type, String attachment) {}

    private static final class AiSuggestion {
        final String source; final String title; final String snippet; final double score; final String memoryKey;
        AiSuggestion(String source, String title, String snippet, double score, String memoryKey) {
            this.source = source; this.title = title; this.snippet = snippet; this.score = score; this.memoryKey = memoryKey;
        }
    }
}



