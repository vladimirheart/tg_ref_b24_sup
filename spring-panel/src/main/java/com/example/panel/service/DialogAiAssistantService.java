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
    private static final Set<String> STOP = Set.of("и","в","на","не","что","как","для","или","по","из","к","у","о","об","the","a","an","to","of","in","on","for","and","or","is","are","be");
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
    private final DialogService dialogService;
    private final DialogReplyService dialogReplyService;
    private final NotificationService notificationService;
    private final SharedConfigService sharedConfigService;
    private final AiPolicyService aiPolicyService;
    private final AiRetrievalService aiRetrievalService;
    private final AiDecisionService aiDecisionService;
    private final AiLearningService aiLearningService;
    private final AiMonitoringService aiMonitoringService;
    private final AiInputNormalizerService aiInputNormalizerService;
    private final ObjectMapper objectMapper;

    public DialogAiAssistantService(JdbcTemplate jdbcTemplate,
                                    DialogService dialogService,
                                    DialogReplyService dialogReplyService,
                                    NotificationService notificationService,
                                    SharedConfigService sharedConfigService,
                                    AiPolicyService aiPolicyService,
                                    AiRetrievalService aiRetrievalService,
                                    AiDecisionService aiDecisionService,
                                    AiLearningService aiLearningService,
                                    AiMonitoringService aiMonitoringService,
                                    AiInputNormalizerService aiInputNormalizerService,
                                    ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialogService = dialogService;
        this.dialogReplyService = dialogReplyService;
        this.notificationService = notificationService;
        this.sharedConfigService = sharedConfigService;
        this.aiPolicyService = aiPolicyService;
        this.aiRetrievalService = aiRetrievalService;
        this.aiDecisionService = aiDecisionService;
        this.aiLearningService = aiLearningService;
        this.aiMonitoringService = aiMonitoringService;
        this.aiInputNormalizerService = aiInputNormalizerService;
        this.objectMapper = objectMapper;
    }

    public void processIncomingClientMessage(String ticketId, String message) {
        processIncomingClientMessage(ticketId, message, null, null);
    }

    public void processIncomingClientMessage(String ticketId, String message, String messageType, String attachment) {
        String t = trim(ticketId);
        if (t == null) return;
        AiInputNormalizerService.IncomingPayload payload = aiInputNormalizerService.normalizeIncomingPayload(t, message, messageType, attachment);
        if (payload == null) {
            clearProcessing(t, "ignored_media_noise", null, "ignored", "junk_or_noise_media", null);
            recordAiEvent(t, "ai_agent_message_ignored", null, "ignored", "junk_or_noise_media", null, null, "Ignored non-actionable media/noise message", Map.of(
                    "policy_stage", "normalize_input",
                    "policy_outcome", "ignored",
                    "sensitive_topic", 0
            ));
            return;
        }
        String m = payload.message();

        DialogAiControl control = loadDialogControl(t);
        String mode = resolveAgentMode();
        boolean agentEnabled = isAgentEnabled();
        PolicyOrderDecision preRouting = evaluatePreRoutingPolicy(t, m, mode, control, agentEnabled);
        if (preRouting.stopProcessing()) {
            clearProcessing(t, preRouting.action(), preRouting.detail(), preRouting.decisionType(), preRouting.decisionReason(), null, preRouting.effectiveMode());
            if ("escalate".equals(preRouting.decisionType())) {
                notifyOperatorsEscalation(t, m, preRouting.detail());
            }
            recordAiEvent(
                    t,
                    preRouting.eventType(),
                    null,
                    preRouting.decisionType(),
                    preRouting.decisionReason(),
                    null,
                    null,
                    preRouting.detail(),
                    mergePayloads(
                            preRouting.sensitiveMatch().asPayload(),
                            Map.of(
                                    "policy_stage", preRouting.policyStage(),
                                    "policy_outcome", preRouting.policyOutcome(),
                                    "message_preview", cut(m, 200),
                                    "ai_disabled", control.aiDisabled(),
                                    "auto_reply_blocked", control.autoReplyBlocked()
                            )
                    )
            );
            return;
        }

        mode = preRouting.effectiveMode();
        dialogService.assignResponsibleIfMissing(t, "ai_agent");
        markProcessing(t, "processing", null, null, "processing", "incoming_message", null, mode);
        List<AiSuggestion> suggestions = findSuggestions(t, m, DEFAULT_SUGGESTION_LIMIT);
        String sourceHits = encodeSourceHits(suggestions);

        if (suggestions.isEmpty()) {
            markProcessing(t, "no_match", null, "No relevant sources found.", "escalate", "no_match", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "AI agent did not find a relevant answer.");
            recordAiEvent(t, "ai_agent_escalated", null, "escalate", "no_match", null, null, "No relevant sources", Map.of(
                    "source_hits", sourceHits,
                    "policy_stage", "4_evidence",
                    "policy_outcome", "insufficient_evidence",
                    "sensitive_topic", preRouting.sensitiveMatch().matched() ? 1 : 0
            ));
            return;
        }

        AiSuggestion top = suggestions.get(0);
        double autoReplyThreshold = resolveAutoReplyThreshold();
        double suggestThreshold = resolveSuggestThreshold();
        boolean sourceEligibleForAutoReply = aiPolicyService.isAutoReplyEligibleSource(
                top.source,
                top.status,
                top.trustLevel,
                top.sourceType,
                top.safetyLevel
        );
        AiDecisionService.Decision decision = aiDecisionService.evaluateCandidateDecision(
                mode,
                top.score,
                suggestThreshold,
                autoReplyThreshold,
                control.autoReplyBlocked(),
                sourceEligibleForAutoReply
        );
        if (decision.action() == AiDecisionService.DecisionAction.ESCALATE) {
            markProcessing(
                    t,
                    decision.processingAction(),
                    top,
                    decision.detail(),
                    decision.decisionType(),
                    decision.decisionReason(),
                    sourceHits,
                    mode
            );
            notifyOperatorsEscalation(t, m, "mode_escalate_only".equals(decision.decisionReason())
                    ? "AI mode is escalate_only."
                    : ("below_suggest_threshold".equals(decision.decisionReason())
                    ? "Low confidence score: " + formatScore(top.score)
                    : "Escalated by decision policy."));
            recordAiEvent(t, "ai_agent_escalated", null, decision.decisionType(), decision.decisionReason(), top.source, top.score, decision.detail(), Map.of(
                    "source_hits", sourceHits,
                    "suggest_threshold", suggestThreshold,
                    "policy_stage", decision.policyStage(),
                    "policy_outcome", decision.policyOutcome(),
                    "top_candidate_trust", top.trustLevel,
                    "top_candidate_source_type", top.sourceType,
                    "sensitive_topic", preRouting.sensitiveMatch().matched() ? 1 : 0
            ));
            return;
        }
        if (decision.action() == AiDecisionService.DecisionAction.SUGGEST_ONLY) {
            markProcessing(t, decision.processingAction(), top, null, decision.decisionType(), decision.decisionReason(), sourceHits, mode);
            recordAiEvent(t, "ai_agent_suggestion_shown", null, decision.decisionType(), decision.decisionReason(), top.source, top.score, "Suggestion shown to operator", Map.of(
                    "source_hits", sourceHits,
                    "auto_reply_threshold", autoReplyThreshold,
                    "policy_stage", decision.policyStage(),
                    "policy_outcome", decision.policyOutcome(),
                    "top_candidate_trust", top.trustLevel,
                    "top_candidate_source_type", top.sourceType,
                    "sensitive_topic", preRouting.sensitiveMatch().matched() ? 1 : 0
            ));
            return;
        }

        AutoReplyGuard guard = evaluateAutoReplyGuard(t);
        if (!guard.allowed()) {
            markProcessing(t, "auto_reply_suppressed", top, guard.reason(), "suppressed", "loop_guard", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "Auto-reply suppressed by loop guard: " + guard.reason());
            recordAiEvent(t, "ai_agent_decision_made", null, "suppressed", "loop_guard", top.source, top.score, guard.reason(), Map.of(
                    "source_hits", sourceHits,
                    "policy_stage", "6_loop_guard",
                    "policy_outcome", "suppressed",
                    "top_candidate_trust", top.trustLevel,
                    "top_candidate_source_type", top.sourceType,
                    "sensitive_topic", preRouting.sensitiveMatch().matched() ? 1 : 0
            ));
            return;
        }

        String reply = buildAutoReply(top);
        DialogReplyService.DialogReplyResult result = dialogReplyService.sendReply(t, reply, null, null, "ai_agent");
        if (!result.success()) {
            markProcessing(t, "send_failed", top, result.error(), "escalate", "send_failed", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "Failed to send AI reply: " + result.error());
            recordAiEvent(t, "ai_agent_escalated", null, "escalate", "send_failed", top.source, top.score, result.error(), Map.of(
                    "source_hits", sourceHits,
                    "policy_stage", "7_auto_reply_allowed",
                    "policy_outcome", "send_failed",
                    "top_candidate_trust", top.trustLevel,
                    "top_candidate_source_type", top.sourceType,
                    "sensitive_topic", preRouting.sensitiveMatch().matched() ? 1 : 0
            ));
            return;
        }

        if (top.memoryKey != null) {
            markMemoryUsage(top.memoryKey);
        }
        markProcessing(t, "auto_replied", top, null, "auto_reply", "score_above_threshold", sourceHits, mode);
        recordAiEvent(t, "ai_agent_auto_reply_sent", "ai_agent", "auto_reply", "score_above_threshold", top.source, top.score, "Auto reply sent", Map.of(
                "source_hits", sourceHits,
                "reply_preview", cut(reply, 300),
                "policy_stage", "7_auto_reply_allowed",
                "policy_outcome", "auto_reply",
                "top_candidate_trust", top.trustLevel,
                "top_candidate_source_type", top.sourceType,
                "sensitive_topic", preRouting.sensitiveMatch().matched() ? 1 : 0
        ));
    }

    public void registerOperatorReply(String ticketId, String operatorReply, String operator) {
        try {
            String t = trim(ticketId), r = trim(operatorReply);
            if (t == null || r == null) return;
            String lastClient = loadLastClientMessage(t);
            if (!StringUtils.hasText(lastClient)) return;
            AiLearningService.UpsertResult upsertResult = aiLearningService.upsertLearningSolution(
                    t,
                    lastClient,
                    r,
                    operator,
                    resolveDifferenceThreshold()
            );
            if (upsertResult == null) return;
            String key = upsertResult.queryKey();
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
                    "memory_key", key,
                    "learning_action", upsertResult.action()
            ));
            jdbcTemplate.update("UPDATE ai_agent_solution_memory SET review_required = 1, pending_solution_text = ?, updated_at = CURRENT_TIMESTAMP WHERE query_key = ?", cut(r, 2000), key);
        } catch (Exception ex) {
            log.debug("registerOperatorReply failed for {}: {}", ticketId, ex.getMessage());
        }
    }

    public boolean submitOperatorLearningMapping(String ticketId,
                                                 String clientProblemMessage,
                                                 String operatorSolutionMessage,
                                                 String operator) {
        String t = trim(ticketId);
        String client = trim(clientProblemMessage);
        String solution = trim(operatorSolutionMessage);
        if (t == null || client == null || solution == null) {
            return false;
        }
        try {
            AiLearningService.UpsertResult result = aiLearningService.upsertLearningSolution(
                    t,
                    client,
                    solution,
                    trim(operator),
                    resolveDifferenceThreshold()
            );
            if (result == null) {
                return false;
            }
            clearProcessing(t, "operator_learning_mapping_submitted", null, "review", "operator_mapping_submitted", null);
            recordAiEvent(t, "ai_agent_operator_mapping_submitted", trim(operator), "review", "operator_mapping_submitted", null, null, result.action(), Map.of(
                    "query_key", result.queryKey(),
                    "mapping_action", result.action()
            ));
            return true;
        } catch (Exception ex) {
            log.debug("submitOperatorLearningMapping failed for {}: {}", ticketId, ex.getMessage());
            return false;
        }
    }

    public List<Map<String, Object>> loadOperatorSuggestions(String ticketId, Integer limit) {
        String t = trim(ticketId);
        if (t == null) return List.of();
        AiInputNormalizerService.IncomingPayload payload = aiInputNormalizerService.loadLastClientPayload(t);
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
                if (sourceUpdated > 0) {
                    applyMemoryGovernance(
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
                applyMemoryGovernance(
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
                    applyMemoryGovernance(
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
                applyMemoryGovernance(
                        key,
                        "rejected",
                        "low",
                        null,
                        null,
                        null,
                        "normal",
                        "operator",
                        false,
                        trim(operator)
                );
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
        List<AiRetrievalService.Candidate> candidates = aiRetrievalService.findSuggestions(ticketId, query, limit);
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<AiSuggestion> mapped = new ArrayList<>(candidates.size());
        for (AiRetrievalService.Candidate candidate : candidates) {
            mapped.add(new AiSuggestion(
                    candidate.source(),
                    candidate.title(),
                    candidate.snippet(),
                    candidate.score(),
                    candidate.memoryKey(),
                    candidate.status(),
                    candidate.trustLevel(),
                    candidate.sourceType(),
                    candidate.safetyLevel()
            ));
        }
        return mapped;
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

    public List<Map<String, Object>> loadSolutionMemory(Integer limit, String query) {
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : 100, 500));
        String q = trim(query);
        try {
            if (q == null) {
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
            String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
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
                applyMemoryGovernance(
                        key,
                        requireReview ? "draft" : "approved",
                        requireReview ? "low" : "medium",
                        null,
                        null,
                        null,
                        "normal",
                        "operator",
                        !requireReview,
                        trim(operator)
                );
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

    public boolean deleteSolutionMemory(String queryKey, String operator) {
        String key = trim(queryKey);
        if (key == null) {
            return false;
        }
        try {
            Map<String, Object> before = loadSolutionMemoryByKey(key);
            if (before == null) {
                return false;
            }
            int deleted = jdbcTemplate.update(
                    "DELETE FROM ai_agent_solution_memory WHERE query_key = ?",
                    key
            );
            if (deleted <= 0) {
                return false;
            }
            insertSolutionMemoryHistory(
                    key,
                    trim(operator),
                    "manual",
                    "delete",
                    safe(before.get("query_text")),
                    safe(before.get("solution_text")),
                    isTrue(before.get("review_required")),
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
                applyMemoryGovernance(
                        key,
                        rollbackReview ? "draft" : "approved",
                        rollbackReview ? "low" : "medium",
                        null,
                        null,
                        null,
                        "normal",
                        "operator",
                        !rollbackReview,
                        trim(operator)
                );
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
        return aiMonitoringService.loadMonitoringSummary(days);
    }

    public List<Map<String, Object>> loadMonitoringEvents(Integer days,
                                                          Integer limit,
                                                          String ticketId,
                                                          String eventType,
                                                          String actor) {
        return aiMonitoringService.loadMonitoringEvents(days, limit, ticketId, eventType, actor);
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
            if (updated > 0) {
                applyMemoryGovernance(
                        key,
                        "approved",
                        "medium",
                        null,
                        null,
                        null,
                        "normal",
                        "operator",
                        true,
                        trim(operator)
                );
            }
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
            if (updated > 0) {
                applyMemoryGovernance(
                        key,
                        "rejected",
                        "low",
                        null,
                        null,
                        null,
                        "normal",
                        "operator",
                        false,
                        trim(operator)
                );
            }
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
            body = "Не удалось найти точный ответ в базе. Ниже безопасный общий план действий.";
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
        reply.append("\nЕсли после этих шагов проблема не решится, напишите, что именно не сработало.");
        return reply.toString();
    }

    private String buildOperatorReplySuggestion(AiSuggestion s) {
        String sourceLabel = switch (s.source) {
            case "memory" -> "память подтвержденных решений";
            case "knowledge" -> "база знаний";
            case "tasks" -> "связанные задачи";
            case "history" -> "история похожих диалогов";
            case "applicant_history" -> "история заявителя";
            default -> "доступные данные";
        };
        String prepared = buildAutoReply(s);
        return "Подсказка на основе источника \"" + sourceLabel + "\":\n\n" + prepared;
    }

    private String buildSuggestionExplain(AiSuggestion s) {
        String sourceExplain = switch (String.valueOf(s.source).toLowerCase(Locale.ROOT)) {
            case "memory" -> "Подсказка выбрана из подтвержденной памяти решений.";
            case "knowledge" -> "Подсказка собрана из базы знаний.";
            case "tasks" -> "Подсказка опирается на связанные задачи и инструкции.";
            case "history" -> "Подсказка основана на похожих диалогах.";
            case "applicant_history" -> "Подсказка учитывает историю заявителя.";
            default -> "Подсказка сформирована на основе доступных данных.";
        };
        String trust = StringUtils.hasText(s.trustLevel) ? s.trustLevel : "unknown";
        return sourceExplain + " Итоговый confidence: " + formatScore(s.score) + ". trust=" + trust + ".";
    }

    private void notifyOperatorsEscalation(String ticketId, String msg, String reason) {
        String text = "AI-агент эскалировал обращение " + ticketId + ". " + reason;
        if (StringUtils.hasText(msg)) {
            text += " Вопрос клиента: " + cut(msg, 140);
        }
        notificationService.notifyAllOperators(text, "/dialogs?ticketId=" + ticketId, null);
    }

    private boolean isAgentEnabled() { try { Object d = sharedConfigService.loadSettings().get("dialog_config"); if (d instanceof Map<?,?> m) { Object e = m.get("ai_agent_enabled"); if (e instanceof Boolean b) return b; String n = String.valueOf(e).trim().toLowerCase(Locale.ROOT); return !"false".equals(n) && !"0".equals(n) && !"off".equals(n); } } catch (Exception ex) { log.debug("ai_agent_enabled read failed: {}", ex.getMessage()); } return true; }

    private boolean requiresHumanImmediately(String m) {
        String n = normalize(m);
        return n.contains("оператор")
                || n.contains("человек")
                || n.contains("менеджер")
                || n.contains("позвон")
                || n.contains("свяж")
                || n.contains("живой");
    }

    private boolean isMeaningfullyDifferent(String a, String b) { return similarity(a, b) < resolveDifferenceThreshold(); }
    private double similarity(String a, String b) { Set<String> x = tokenize(a), y = tokenize(b); if (x.isEmpty() || y.isEmpty()) return 0d; int i = 0; for (String t : x) if (y.contains(t)) i++; int u = x.size() + y.size() - i; return u <= 0 ? 0d : i / (double) u; }
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
    private String cut(String text, int len) { String t = trim(text); if (t == null) return ""; String c = t.replaceAll("\\s+", " ").trim(); return c.length() <= len ? c : c.substring(0, Math.max(0, len - 3)) + "..."; }
    private String safe(Object v) { return v != null ? String.valueOf(v) : ""; }
    private String trim(String v) { if (!StringUtils.hasText(v)) return null; String t = v.trim(); return t.isEmpty() ? null : t; }
    private Long toLong(Object v) { try { if (v == null) return null; return Long.parseLong(String.valueOf(v)); } catch (Exception ex) { return null; } }
    private boolean isTrue(Object v) { if (v instanceof Boolean b) return b; String n = String.valueOf(v).trim().toLowerCase(Locale.ROOT); return "1".equals(n) || "true".equals(n) || "yes".equals(n) || "on".equals(n); }
    private String formatScore(double score) { return String.format(Locale.ROOT, "%.2f", Math.max(0d, Math.min(1d, score))); }
    private String buildKey(String question) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalize(question).getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { return Integer.toHexString(normalize(question).hashCode()); } }
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
    private PolicyOrderDecision evaluatePreRoutingPolicy(String ticketId,
                                                         String message,
                                                         String baseMode,
                                                         DialogAiControl control,
                                                         boolean agentEnabled) {
        AiPolicyService.SensitiveTopicMatch sensitiveMatch = aiPolicyService.detectSensitiveTopic(message);
        String effectiveMode = aiPolicyService.applySensitiveModeOverride(baseMode, sensitiveMatch);

        if (control.aiDisabled()) {
            return new PolicyOrderDecision(
                    true,
                    "disabled_for_dialog",
                    "ai_agent_disabled_for_dialog",
                    "disabled",
                    "dialog_override_disabled",
                    "1_dialog_override",
                    "blocked",
                    effectiveMode,
                    control.reason() != null ? control.reason() : "Dialog override disabled AI",
                    sensitiveMatch
            );
        }
        if (!agentEnabled) {
            return new PolicyOrderDecision(
                    true,
                    "disabled",
                    "ai_agent_decision_made",
                    "disabled",
                    "config_disabled",
                    "1_global_config",
                    "blocked",
                    effectiveMode,
                    "Agent disabled in dialog_config",
                    sensitiveMatch
            );
        }
        if (aiPolicyService.requiresEscalation(sensitiveMatch)) {
            return new PolicyOrderDecision(
                    true,
                    "sensitive_topic_escalated",
                    "ai_agent_escalated",
                    "escalate",
                    "sensitive_topic_escalate_only",
                    "2_sensitive_topic",
                    "escalate",
                    effectiveMode,
                    "Sensitive topic routed to operator: " + trim(sensitiveMatch.topicKey()),
                    sensitiveMatch
            );
        }
        if (requiresHumanImmediately(message)) {
            return new PolicyOrderDecision(
                    true,
                    "manual_requested",
                    "ai_agent_escalated",
                    "escalate",
                    "manual_requested",
                    "3_human_request",
                    "escalate",
                    effectiveMode,
                    "Client requested operator.",
                    sensitiveMatch
            );
        }
        return new PolicyOrderDecision(
                false,
                "continue",
                "ai_agent_decision_made",
                "processing",
                "continue",
                "precheck_passed",
                "continue",
                effectiveMode,
                null,
                sensitiveMatch
        );
    }

    private Map<String, Object> mergePayloads(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (left != null && !left.isEmpty()) {
            payload.putAll(left);
        }
        if (right != null && !right.isEmpty()) {
            payload.putAll(right);
        }
        return payload;
    }

    private void applyMemoryGovernance(String queryKey,
                                       String status,
                                       String trustLevel,
                                       String intentKey,
                                       String slotSignature,
                                       String scopeChannel,
                                       String safetyLevel,
                                       String sourceType,
                                       boolean verified,
                                       String verifiedBy) {
        String key = trim(queryKey);
        if (key == null) {
            return;
        }
        try {
            jdbcTemplate.update(
                    """
                    UPDATE ai_agent_solution_memory
                       SET status = ?,
                           trust_level = ?,
                           intent_key = ?,
                           slot_signature = ?,
                           scope_channel = COALESCE(scope_channel, ?),
                           safety_level = ?,
                           source_type = ?,
                           last_verified_at = CASE WHEN ? = 1 THEN CURRENT_TIMESTAMP ELSE last_verified_at END,
                           verified_by = CASE WHEN ? = 1 THEN ? ELSE verified_by END,
                           updated_at = CURRENT_TIMESTAMP
                     WHERE query_key = ?
                    """,
                    aiPolicyService.normalizeStatus(status, "draft"),
                    aiPolicyService.normalizeTrustLevel(trustLevel, "low"),
                    cut(trim(intentKey), 120),
                    cut(trim(slotSignature), 300),
                    cut(trim(scopeChannel), 120),
                    aiPolicyService.normalizeSafetyLevel(safetyLevel, "normal"),
                    aiPolicyService.normalizeSourceType("memory", sourceType),
                    verified ? 1 : 0,
                    verified ? 1 : 0,
                    cut(trim(verifiedBy), 120),
                    key
            );
        } catch (Exception ignored) {
            // Backward compatibility for databases where governance columns are not present yet.
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
            row.put("status", s.status);
            row.put("trust_level", s.trustLevel);
            row.put("source_type", s.sourceType);
            row.put("safety_level", s.safetyLevel);
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
        String policyStage = payload != null ? trim(safe(payload.get("policy_stage"))) : null;
        String policyOutcome = payload != null ? trim(safe(payload.get("policy_outcome"))) : null;
        String intentKey = payload != null ? trim(safe(payload.get("intent_key"))) : null;
        Integer sensitiveTopic = payload != null && isTrue(payload.get("sensitive_topic")) ? 1 : 0;
        String topCandidateTrust = payload != null ? trim(safe(payload.get("top_candidate_trust"))) : null;
        String topCandidateSourceType = payload != null ? trim(safe(payload.get("top_candidate_source_type"))) : null;
        try {
            jdbcTemplate.update("""
                    INSERT INTO ai_agent_event_log(
                        ticket_id, event_type, actor, decision_type, decision_reason, source, score, detail, payload_json,
                        policy_stage, policy_outcome, intent_key, sensitive_topic, top_candidate_trust, top_candidate_source_type, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    trim(ticketId),
                    cut(type, 80),
                    trim(actor),
                    trim(decisionType),
                    trim(decisionReason),
                    trim(source),
                    score,
                    cut(detail, 2000),
                    payload != null && !payload.isEmpty() ? cut(toJson(payload), 5000) : null,
                    cut(policyStage, 80),
                    cut(policyOutcome, 80),
                    cut(intentKey, 120),
                    sensitiveTopic,
                    cut(topCandidateTrust, 32),
                    cut(topCandidateSourceType, 64)
            );
            return;
        } catch (Exception ignored) {
            // Fallback for pre-migration schema.
        }
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
    private record PolicyOrderDecision(boolean stopProcessing,
                                       String action,
                                       String eventType,
                                       String decisionType,
                                       String decisionReason,
                                       String policyStage,
                                       String policyOutcome,
                                       String effectiveMode,
                                       String detail,
                                       AiPolicyService.SensitiveTopicMatch sensitiveMatch) {
    }
    private record DialogAiControl(boolean aiDisabled,
                                   boolean autoReplyBlocked,
                                   String reason,
                                   String updatedBy,
                                   String updatedAt) {
        static final DialogAiControl DEFAULT = new DialogAiControl(false, false, null, null, null);
    }
    private static final class AiSuggestion {
        final String source;
        final String title;
        final String snippet;
        final double score;
        final String memoryKey;
        final String status;
        final String trustLevel;
        final String sourceType;
        final String safetyLevel;

        AiSuggestion(String source,
                     String title,
                     String snippet,
                     double score,
                     String memoryKey,
                     String status,
                     String trustLevel,
                     String sourceType,
                     String safetyLevel) {
            this.source = source;
            this.title = title;
            this.snippet = snippet;
            this.score = score;
            this.memoryKey = memoryKey;
            this.status = status;
            this.trustLevel = trustLevel;
            this.sourceType = sourceType;
            this.safetyLevel = safetyLevel;
        }
    }
}



