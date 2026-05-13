package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DialogAiAssistantService {
    private static final int DEFAULT_SUGGESTION_LIMIT = 3;
    private static final String MODE_AUTO_REPLY = "auto_reply";
    private static final String MODE_ASSIST_ONLY = "assist_only";
    private static final String MODE_ESCALATE_ONLY = "escalate_only";

    private final DialogReplyService dialogReplyService;
    private final NotificationService notificationService;
    private final AiPolicyService aiPolicyService;
    private final AiRetrievalService aiRetrievalService;
    private final AiDecisionService aiDecisionService;
    private final AiMonitoringService aiMonitoringService;
    private final AiInputNormalizerService aiInputNormalizerService;
    private final AiControlledLlmService aiControlledLlmService;
    private final DialogAiAssistantReviewService dialogAiAssistantReviewService;
    private final DialogAiSolutionMemoryService dialogAiSolutionMemoryService;
    private final DialogAiAssistantStateService dialogAiAssistantStateService;
    private final DialogAiAssistantConfigService dialogAiAssistantConfigService;
    private final DialogAiAssistantOperatorFeedbackService dialogAiAssistantOperatorFeedbackService;
    private final DialogAiAssistantPolicyService dialogAiAssistantPolicyService;
    private final DialogAiAssistantSuggestionService dialogAiAssistantSuggestionService;
    private final DialogAiAssistantEventService dialogAiAssistantEventService;

    public DialogAiAssistantService(DialogReplyService dialogReplyService,
                                    NotificationService notificationService,
                                    AiPolicyService aiPolicyService,
                                    AiRetrievalService aiRetrievalService,
                                    AiDecisionService aiDecisionService,
                                    AiMonitoringService aiMonitoringService,
                                    AiInputNormalizerService aiInputNormalizerService,
                                    AiControlledLlmService aiControlledLlmService,
                                    DialogAiAssistantReviewService dialogAiAssistantReviewService,
                                    DialogAiSolutionMemoryService dialogAiSolutionMemoryService,
                                    DialogAiAssistantStateService dialogAiAssistantStateService,
                                    DialogAiAssistantConfigService dialogAiAssistantConfigService,
                                    DialogAiAssistantOperatorFeedbackService dialogAiAssistantOperatorFeedbackService,
                                    DialogAiAssistantPolicyService dialogAiAssistantPolicyService,
                                    DialogAiAssistantSuggestionService dialogAiAssistantSuggestionService,
                                    DialogAiAssistantEventService dialogAiAssistantEventService) {
        this.dialogReplyService = dialogReplyService;
        this.notificationService = notificationService;
        this.aiPolicyService = aiPolicyService;
        this.aiRetrievalService = aiRetrievalService;
        this.aiDecisionService = aiDecisionService;
        this.aiMonitoringService = aiMonitoringService;
        this.aiInputNormalizerService = aiInputNormalizerService;
        this.aiControlledLlmService = aiControlledLlmService;
        this.dialogAiAssistantReviewService = dialogAiAssistantReviewService;
        this.dialogAiSolutionMemoryService = dialogAiSolutionMemoryService;
        this.dialogAiAssistantStateService = dialogAiAssistantStateService;
        this.dialogAiAssistantConfigService = dialogAiAssistantConfigService;
        this.dialogAiAssistantOperatorFeedbackService = dialogAiAssistantOperatorFeedbackService;
        this.dialogAiAssistantPolicyService = dialogAiAssistantPolicyService;
        this.dialogAiAssistantSuggestionService = dialogAiAssistantSuggestionService;
        this.dialogAiAssistantEventService = dialogAiAssistantEventService;
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

        DialogAiAssistantStateService.DialogAiControl control = dialogAiAssistantStateService.loadDialogControl(t);
        String mode = dialogAiAssistantConfigService.resolveAgentMode();
        boolean agentEnabled = dialogAiAssistantConfigService.isAgentEnabled();
        DialogAiAssistantPolicyService.PreRoutingDecision preRouting =
                dialogAiAssistantPolicyService.evaluatePreRoutingPolicy(m, mode, control, agentEnabled);
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
        markProcessing(t, "processing", null, null, "processing", "incoming_message", null, mode);
        AiControlledLlmService.RewriteResult rewriteResult = aiControlledLlmService.rewriteQuery(t, m);
        String retrievalQuery = firstNonBlank(trim(rewriteResult.effectiveQuery()), m);
        AiRetrievalService.RetrievalResult retrievalResult = aiRetrievalService.retrieve(t, retrievalQuery, DEFAULT_SUGGESTION_LIMIT);
        mode = applyIntentModeOverride(mode, retrievalResult.context().intentPolicy());
        List<DialogAiAssistantSuggestionCandidate> suggestions = mapSuggestions(retrievalResult.candidates());
        String sourceHits = dialogAiAssistantEventService.encodeSourceHits(suggestions);

        if (suggestions.isEmpty()) {
            markProcessing(t, "no_match", null, "No relevant sources found.", "escalate", "no_match", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "AI agent did not find a relevant answer.");
            Map<String, Object> eventPayload = dialogAiAssistantEventService.buildRetrievalPayload(sourceHits, null, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult);
            eventPayload.put("policy_stage", "4_evidence");
            eventPayload.put("policy_outcome", "insufficient_evidence");
            recordAiEvent(t, "ai_agent_escalated", null, "escalate", "no_match", null, null, "No relevant sources", eventPayload);
            return;
        }

        DialogAiAssistantSuggestionCandidate top = suggestions.get(0);
        double autoReplyThreshold = dialogAiAssistantConfigService.resolveAutoReplyThreshold();
        double suggestThreshold = dialogAiAssistantConfigService.resolveSuggestThreshold();
        boolean sourceEligibleForAutoReply = aiPolicyService.isAutoReplyEligibleSource(
                top.source(),
                top.status(),
                top.trustLevel(),
                top.sourceType(),
                top.safetyLevel()
        ) && retrievalResult.context().intentPolicy().autoReplyAllowed()
                && !retrievalResult.context().intentPolicy().requiresOperator();
        AiDecisionService.Decision decision = aiDecisionService.evaluateCandidateDecision(
                mode,
                top.score(),
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
                    ? "Low confidence score: " + formatScore(top.score())
                    : "Escalated by decision policy."));
            Map<String, Object> eventPayload = dialogAiAssistantEventService.buildRetrievalPayload(sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult);
            eventPayload.put("suggest_threshold", suggestThreshold);
            eventPayload.put("auto_reply_threshold", autoReplyThreshold);
            eventPayload.put("policy_stage", decision.policyStage());
            eventPayload.put("policy_outcome", decision.policyOutcome());
            recordAiEvent(t, "ai_agent_escalated", null, decision.decisionType(), decision.decisionReason(), top.source(), top.score(), decision.detail(), eventPayload);
            return;
        }
        if (decision.action() == AiDecisionService.DecisionAction.SUGGEST_ONLY) {
            markProcessing(t, decision.processingAction(), top, null, decision.decisionType(), decision.decisionReason(), sourceHits, mode);
            Map<String, Object> eventPayload = dialogAiAssistantEventService.buildRetrievalPayload(sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult);
            eventPayload.put("auto_reply_threshold", autoReplyThreshold);
            eventPayload.put("suggest_threshold", suggestThreshold);
            eventPayload.put("policy_stage", decision.policyStage());
            eventPayload.put("policy_outcome", decision.policyOutcome());
            recordAiEvent(t, "ai_agent_suggestion_shown", null, decision.decisionType(), decision.decisionReason(), top.source(), top.score(), "Suggestion shown to operator", eventPayload);
            return;
        }

        if (!retrievalResult.consistency().autoReplyAllowed()) {
            String detail = "evidence_conflict".equals(retrievalResult.consistency().reason())
                    ? "Conflicting evidence across top candidates."
                    : "Not enough independent confirmations for auto-reply.";
            String decisionType = retrievalResult.consistency().hasConflict() ? "escalate" : "suggest_only";
            String decisionReason = retrievalResult.consistency().reason();
            String action = retrievalResult.consistency().hasConflict() ? "escalated" : "suggest_only";
            markProcessing(t, action, top, detail, decisionType, decisionReason, sourceHits, mode);
            if (retrievalResult.consistency().hasConflict()) {
                notifyOperatorsEscalation(t, m, detail);
            }
            Map<String, Object> eventPayload = dialogAiAssistantEventService.buildRetrievalPayload(sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult);
            eventPayload.put("auto_reply_threshold", autoReplyThreshold);
            eventPayload.put("suggest_threshold", suggestThreshold);
            eventPayload.put("policy_stage", "6_consistency");
            eventPayload.put("policy_outcome", retrievalResult.consistency().hasConflict() ? "blocked_by_conflict" : "blocked_by_insufficient_confirmation");
            recordAiEvent(
                    t,
                    retrievalResult.consistency().hasConflict() ? "ai_agent_escalated" : "ai_agent_suggestion_shown",
                    null,
                    decisionType,
                    decisionReason,
                    top.source(),
                    top.score(),
                    detail,
                    eventPayload
            );
            return;
        }

        DialogAiAssistantConfigService.AutoReplyGuard guard = dialogAiAssistantConfigService.evaluateAutoReplyGuard(t);
        if (!guard.allowed()) {
            markProcessing(t, "auto_reply_suppressed", top, guard.reason(), "suppressed", "loop_guard", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "Auto-reply suppressed by loop guard: " + guard.reason());
            Map<String, Object> eventPayload = dialogAiAssistantEventService.buildRetrievalPayload(sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult);
            eventPayload.put("auto_reply_threshold", autoReplyThreshold);
            eventPayload.put("suggest_threshold", suggestThreshold);
            eventPayload.put("policy_stage", "7_loop_guard");
            eventPayload.put("policy_outcome", "suppressed");
            recordAiEvent(t, "ai_agent_decision_made", null, "suppressed", "loop_guard", top.source(), top.score(), guard.reason(), eventPayload);
            return;
        }

        String reply = dialogAiAssistantSuggestionService.buildAutoReply(t, m, top, retrievalResult.context().intentPolicy(), true);
        DialogReplyService.DialogReplyResult result = dialogReplyService.sendReply(t, reply, null, null, "ai_agent");
        if (!result.success()) {
            markProcessing(t, "send_failed", top, result.error(), "escalate", "send_failed", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "Failed to send AI reply: " + result.error());
            Map<String, Object> eventPayload = dialogAiAssistantEventService.buildRetrievalPayload(sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult);
            eventPayload.put("auto_reply_threshold", autoReplyThreshold);
            eventPayload.put("suggest_threshold", suggestThreshold);
            eventPayload.put("policy_stage", "8_auto_reply_allowed");
            eventPayload.put("policy_outcome", "send_failed");
            recordAiEvent(t, "ai_agent_escalated", null, "escalate", "send_failed", top.source(), top.score(), result.error(), eventPayload);
            return;
        }

        if (top.memoryKey() != null) {
            dialogAiSolutionMemoryService.markMemoryUsage(top.memoryKey());
        }
        markProcessing(t, "auto_replied", top, null, "auto_reply", "score_above_threshold", sourceHits, mode);
        Map<String, Object> eventPayload = dialogAiAssistantEventService.buildRetrievalPayload(sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult);
        eventPayload.put("reply_preview", cut(reply, 300));
        eventPayload.put("auto_reply_threshold", autoReplyThreshold);
        eventPayload.put("suggest_threshold", suggestThreshold);
        eventPayload.put("policy_stage", "8_auto_reply_allowed");
        eventPayload.put("policy_outcome", "auto_reply");
        recordAiEvent(t, "ai_agent_auto_reply_sent", "ai_agent", "auto_reply", "score_above_threshold", top.source(), top.score(), "Auto reply sent", eventPayload);
    }

    public void registerOperatorReply(String ticketId, String operatorReply, String operator) {
        dialogAiAssistantOperatorFeedbackService.registerOperatorReply(ticketId, operatorReply, operator);
    }

    public boolean submitOperatorLearningMapping(String ticketId,
                                                 String clientProblemMessage,
                                                 String operatorSolutionMessage,
                                                 String operator) {
        return dialogAiAssistantOperatorFeedbackService.submitOperatorLearningMapping(
                ticketId,
                clientProblemMessage,
                operatorSolutionMessage,
                operator
        );
    }

    public List<Map<String, Object>> loadOperatorSuggestions(String ticketId, Integer limit) {
        String t = trim(ticketId);
        if (t == null) return List.of();
        AiInputNormalizerService.IncomingPayload payload = aiInputNormalizerService.loadLastClientPayload(t);
        String lastClient = payload != null ? payload.message() : null;
        if (!StringUtils.hasText(lastClient)) return List.of();
        int safeLimit = Math.max(1, Math.min(limit != null ? limit : DEFAULT_SUGGESTION_LIMIT, 8));
        List<Map<String, Object>> result = new ArrayList<>();
        for (DialogAiAssistantSuggestionCandidate s : findSuggestions(t, lastClient, safeLimit)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", s.source());
            item.put("title", s.title());
            item.put("score", s.score());
            item.put("score_label", formatScore(s.score()));
            item.put("snippet", s.snippet());
            item.put("reply", dialogAiAssistantSuggestionService.buildOperatorReplySuggestion(t, lastClient, s));
            item.put("explain", dialogAiAssistantSuggestionService.buildSuggestionExplain(t, lastClient, s));
            result.add(item);
        }
        return result;
    }

    public Map<String, Object> loadDialogControlState(String ticketId) {
        return dialogAiAssistantStateService.loadDialogControlState(ticketId);
    }

    public boolean updateDialogControlState(String ticketId,
                                            Boolean aiDisabled,
                                            Boolean autoReplyBlocked,
                                            String reason,
                                            String actor) {
        return dialogAiAssistantStateService.updateDialogControlState(
                ticketId,
                aiDisabled,
                autoReplyBlocked,
                reason,
                actor,
                dialogAiAssistantConfigService.resolveAgentMode()
        );
    }

    public void recordSuggestionFeedback(String ticketId,
                                         String decision,
                                         String source,
                                         String title,
                                         String snippet,
                                         String suggestedReply,
                                         String actor) {
        dialogAiAssistantOperatorFeedbackService.recordSuggestionFeedback(
                ticketId,
                decision,
                source,
                title,
                snippet,
                suggestedReply,
                actor
        );
    }

    public Map<String, Object> loadPendingReview(String ticketId) {
        return dialogAiAssistantReviewService.loadPendingReview(ticketId);
    }

    public boolean approvePendingReview(String ticketId, String operator) {
        return dialogAiAssistantReviewService.approvePendingReview(ticketId, operator);
    }

    public boolean approvePendingReview(String ticketId, String operator, Long clientMessageId, Long operatorMessageId) {
        return dialogAiAssistantReviewService.approvePendingReview(ticketId, operator, clientMessageId, operatorMessageId);
    }

    public boolean rejectPendingReview(String ticketId, String operator) {
        return dialogAiAssistantReviewService.rejectPendingReview(ticketId, operator);
    }

    public boolean isProcessing(String ticketId) {
        return dialogAiAssistantStateService.isProcessing(ticketId);
    }

    public void clearProcessing(String ticketId, String action, String error) {
        dialogAiAssistantStateService.clearProcessing(ticketId, action, error);
    }

    private void clearProcessing(String ticketId, String action, String error, String decisionType, String decisionReason, String sourceHits) {
        clearProcessing(ticketId, action, error, decisionType, decisionReason, sourceHits, dialogAiAssistantConfigService.resolveAgentMode());
    }

    private void clearProcessing(String ticketId, String action, String error, String decisionType, String decisionReason, String sourceHits, String mode) {
        dialogAiAssistantStateService.clearProcessing(ticketId, action, error, decisionType, decisionReason, sourceHits, mode);
    }

    private void markProcessing(String ticketId, String action, DialogAiAssistantSuggestionCandidate suggestion, String error, String decisionType, String decisionReason, String sourceHits, String mode) {
        dialogAiAssistantStateService.markProcessing(
                ticketId,
                action,
                error,
                suggestion != null ? suggestion.source() : null,
                suggestion != null ? suggestion.score() : null,
                suggestion != null ? trim(dialogAiAssistantSuggestionService.buildDeterministicReply(suggestion)) : null,
                decisionType,
                decisionReason,
                sourceHits,
                mode
        );
    }

    private List<DialogAiAssistantSuggestionCandidate> findSuggestions(String ticketId, String query, int limit) {
        return mapSuggestions(aiRetrievalService.findSuggestions(ticketId, query, limit));
    }

    private List<DialogAiAssistantSuggestionCandidate> mapSuggestions(List<AiRetrievalService.Candidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        List<DialogAiAssistantSuggestionCandidate> mapped = new java.util.ArrayList<>(candidates.size());
        for (AiRetrievalService.Candidate candidate : candidates) {
            mapped.add(new DialogAiAssistantSuggestionCandidate(
                    candidate.source(),
                    candidate.title(),
                    candidate.snippet(),
                    candidate.score(),
                    candidate.memoryKey(),
                    candidate.status(),
                    candidate.trustLevel(),
                    candidate.sourceType(),
                    candidate.safetyLevel(),
                    candidate.sourceRef(),
                    candidate.intentKey(),
                    candidate.slotSignature(),
                    candidate.canonicalKey(),
                    candidate.evidenceCount(),
                    candidate.trace(),
                    candidate.updatedAt(),
                    candidate.stale()
            ));
        }
        return mapped;
    }

    public List<Map<String, Object>> loadPendingReviewsQueue(Integer limit) {
        return dialogAiAssistantReviewService.loadPendingReviewsQueue(limit);
    }

    public List<Map<String, Object>> loadSolutionMemory(Integer limit, String query) {
        return dialogAiSolutionMemoryService.loadSolutionMemory(limit, query);
    }

    public boolean updateSolutionMemory(String queryKey,
                                        String queryText,
                                        String solutionText,
                                        Boolean reviewRequired,
                                        String operator) {
        return dialogAiSolutionMemoryService.updateSolutionMemory(queryKey, queryText, solutionText, reviewRequired, operator);
    }

    public boolean deleteSolutionMemory(String queryKey, String operator) {
        return dialogAiSolutionMemoryService.deleteSolutionMemory(queryKey, operator);
    }

    public List<Map<String, Object>> loadSolutionMemoryHistory(String queryKey, Integer limit) {
        return dialogAiSolutionMemoryService.loadSolutionMemoryHistory(queryKey, limit);
    }

    public boolean rollbackSolutionMemory(String queryKey, Long historyId, String operator) {
        return dialogAiSolutionMemoryService.rollbackSolutionMemory(queryKey, historyId, operator);
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
        return dialogAiAssistantReviewService.approvePendingReviewByKey(queryKey, operator);
    }

    public boolean rejectPendingReviewByKey(String queryKey, String operator) {
        return dialogAiAssistantReviewService.rejectPendingReviewByKey(queryKey, operator);
    }

    private void notifyOperatorsEscalation(String ticketId, String msg, String reason) {
        String text = "AI-агент эскалировал обращение " + ticketId + ". " + reason;
        if (StringUtils.hasText(msg)) {
            text += " Вопрос клиента: " + cut(msg, 140);
        }
        notificationService.notifyAllOperators(text, "/dialogs?ticketId=" + ticketId, null);
    }
    private String cut(String text, int len) { String t = trim(text); if (t == null) return ""; String c = t.replaceAll("\\s+", " ").trim(); return c.length() <= len ? c : c.substring(0, Math.max(0, len - 3)) + "..."; }
    private String trim(String v) { if (!StringUtils.hasText(v)) return null; String t = v.trim(); return t.isEmpty() ? null : t; }
    private Long toLong(Object v) { try { if (v == null) return null; return Long.parseLong(String.valueOf(v)); } catch (Exception ex) { return null; } }
    private String formatScore(double score) { return String.format(Locale.ROOT, "%.2f", Math.max(0d, Math.min(1d, score))); }

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

    private String applyIntentModeOverride(String currentMode, AiIntentService.IntentPolicy intentPolicy) {
        if (intentPolicy == null) {
            return currentMode;
        }
        if (intentPolicy.requiresOperator()) {
            return MODE_ESCALATE_ONLY;
        }
        if (intentPolicy.assistOnly() && !MODE_ESCALATE_ONLY.equals(currentMode)) {
            return MODE_ASSIST_ONLY;
        }
        return currentMode;
    }
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trim(value);
            if (normalized != null) {
                return normalized;
            }
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
        dialogAiAssistantEventService.recordAiEvent(ticketId, eventType, actor, decisionType, decisionReason, source, score, detail, payload);
    }
}



