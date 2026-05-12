package com.example.panel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private static final Logger log = LoggerFactory.getLogger(DialogAiAssistantService.class);
    private static final int DEFAULT_SUGGESTION_LIMIT = 3;
    private static final String MODE_AUTO_REPLY = "auto_reply";
    private static final String MODE_ASSIST_ONLY = "assist_only";
    private static final String MODE_ESCALATE_ONLY = "escalate_only";

    private final JdbcTemplate jdbcTemplate;
    private final DialogReplyService dialogReplyService;
    private final NotificationService notificationService;
    private final AiPolicyService aiPolicyService;
    private final AiRetrievalService aiRetrievalService;
    private final AiIntentService aiIntentService;
    private final AiDecisionService aiDecisionService;
    private final AiMonitoringService aiMonitoringService;
    private final AiInputNormalizerService aiInputNormalizerService;
    private final AiControlledLlmService aiControlledLlmService;
    private final DialogAiAssistantReviewService dialogAiAssistantReviewService;
    private final DialogAiSolutionMemoryService dialogAiSolutionMemoryService;
    private final DialogAiAssistantStateService dialogAiAssistantStateService;
    private final DialogAiAssistantConfigService dialogAiAssistantConfigService;
    private final DialogAiAssistantOperatorFeedbackService dialogAiAssistantOperatorFeedbackService;
    private final ObjectMapper objectMapper;

    public DialogAiAssistantService(JdbcTemplate jdbcTemplate,
                                    DialogReplyService dialogReplyService,
                                    NotificationService notificationService,
                                    AiPolicyService aiPolicyService,
                                    AiRetrievalService aiRetrievalService,
                                    AiIntentService aiIntentService,
                                    AiDecisionService aiDecisionService,
                                    AiMonitoringService aiMonitoringService,
                                    AiInputNormalizerService aiInputNormalizerService,
                                    AiControlledLlmService aiControlledLlmService,
                                    DialogAiAssistantReviewService dialogAiAssistantReviewService,
                                    DialogAiSolutionMemoryService dialogAiSolutionMemoryService,
                                    DialogAiAssistantStateService dialogAiAssistantStateService,
                                    DialogAiAssistantConfigService dialogAiAssistantConfigService,
                                    DialogAiAssistantOperatorFeedbackService dialogAiAssistantOperatorFeedbackService,
                                    ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialogReplyService = dialogReplyService;
        this.notificationService = notificationService;
        this.aiPolicyService = aiPolicyService;
        this.aiRetrievalService = aiRetrievalService;
        this.aiIntentService = aiIntentService;
        this.aiDecisionService = aiDecisionService;
        this.aiMonitoringService = aiMonitoringService;
        this.aiInputNormalizerService = aiInputNormalizerService;
        this.aiControlledLlmService = aiControlledLlmService;
        this.dialogAiAssistantReviewService = dialogAiAssistantReviewService;
        this.dialogAiSolutionMemoryService = dialogAiSolutionMemoryService;
        this.dialogAiAssistantStateService = dialogAiAssistantStateService;
        this.dialogAiAssistantConfigService = dialogAiAssistantConfigService;
        this.dialogAiAssistantOperatorFeedbackService = dialogAiAssistantOperatorFeedbackService;
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

        DialogAiAssistantStateService.DialogAiControl control = dialogAiAssistantStateService.loadDialogControl(t);
        String mode = dialogAiAssistantConfigService.resolveAgentMode();
        boolean agentEnabled = dialogAiAssistantConfigService.isAgentEnabled();
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
        markProcessing(t, "processing", null, null, "processing", "incoming_message", null, mode);
        AiControlledLlmService.RewriteResult rewriteResult = aiControlledLlmService.rewriteQuery(t, m);
        String retrievalQuery = firstNonBlank(trim(rewriteResult.effectiveQuery()), m);
        AiRetrievalService.RetrievalResult retrievalResult = aiRetrievalService.retrieve(t, retrievalQuery, DEFAULT_SUGGESTION_LIMIT);
        mode = applyIntentModeOverride(mode, retrievalResult.context().intentPolicy());
        List<AiSuggestion> suggestions = mapSuggestions(retrievalResult.candidates());
        String sourceHits = encodeSourceHits(suggestions);

        if (suggestions.isEmpty()) {
            markProcessing(t, "no_match", null, "No relevant sources found.", "escalate", "no_match", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "AI agent did not find a relevant answer.");
            Map<String, Object> eventPayload = buildRetrievalPayload(sourceHits, null, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult);
            eventPayload.put("policy_stage", "4_evidence");
            eventPayload.put("policy_outcome", "insufficient_evidence");
            recordAiEvent(t, "ai_agent_escalated", null, "escalate", "no_match", null, null, "No relevant sources", eventPayload);
            return;
        }

        AiSuggestion top = suggestions.get(0);
        double autoReplyThreshold = dialogAiAssistantConfigService.resolveAutoReplyThreshold();
        double suggestThreshold = dialogAiAssistantConfigService.resolveSuggestThreshold();
        boolean sourceEligibleForAutoReply = aiPolicyService.isAutoReplyEligibleSource(
                top.source,
                top.status,
                top.trustLevel,
                top.sourceType,
                top.safetyLevel
        ) && retrievalResult.context().intentPolicy().autoReplyAllowed()
                && !retrievalResult.context().intentPolicy().requiresOperator();
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
            Map<String, Object> eventPayload = buildRetrievalPayload(sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult);
            eventPayload.put("suggest_threshold", suggestThreshold);
            eventPayload.put("auto_reply_threshold", autoReplyThreshold);
            eventPayload.put("policy_stage", decision.policyStage());
            eventPayload.put("policy_outcome", decision.policyOutcome());
            recordAiEvent(t, "ai_agent_escalated", null, decision.decisionType(), decision.decisionReason(), top.source, top.score, decision.detail(), eventPayload);
            return;
        }
        if (decision.action() == AiDecisionService.DecisionAction.SUGGEST_ONLY) {
            markProcessing(t, decision.processingAction(), top, null, decision.decisionType(), decision.decisionReason(), sourceHits, mode);
            Map<String, Object> eventPayload = buildRetrievalPayload(sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult);
            eventPayload.put("auto_reply_threshold", autoReplyThreshold);
            eventPayload.put("suggest_threshold", suggestThreshold);
            eventPayload.put("policy_stage", decision.policyStage());
            eventPayload.put("policy_outcome", decision.policyOutcome());
            recordAiEvent(t, "ai_agent_suggestion_shown", null, decision.decisionType(), decision.decisionReason(), top.source, top.score, "Suggestion shown to operator", eventPayload);
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
            Map<String, Object> eventPayload = buildRetrievalPayload(sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult);
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
                    top.source,
                    top.score,
                    detail,
                    eventPayload
            );
            return;
        }

        DialogAiAssistantConfigService.AutoReplyGuard guard = dialogAiAssistantConfigService.evaluateAutoReplyGuard(t);
        if (!guard.allowed()) {
            markProcessing(t, "auto_reply_suppressed", top, guard.reason(), "suppressed", "loop_guard", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "Auto-reply suppressed by loop guard: " + guard.reason());
            Map<String, Object> eventPayload = buildRetrievalPayload(sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult);
            eventPayload.put("auto_reply_threshold", autoReplyThreshold);
            eventPayload.put("suggest_threshold", suggestThreshold);
            eventPayload.put("policy_stage", "7_loop_guard");
            eventPayload.put("policy_outcome", "suppressed");
            recordAiEvent(t, "ai_agent_decision_made", null, "suppressed", "loop_guard", top.source, top.score, guard.reason(), eventPayload);
            return;
        }

        String reply = buildAutoReply(t, m, top, retrievalResult.context().intentPolicy(), true);
        DialogReplyService.DialogReplyResult result = dialogReplyService.sendReply(t, reply, null, null, "ai_agent");
        if (!result.success()) {
            markProcessing(t, "send_failed", top, result.error(), "escalate", "send_failed", sourceHits, mode);
            notifyOperatorsEscalation(t, m, "Failed to send AI reply: " + result.error());
            Map<String, Object> eventPayload = buildRetrievalPayload(sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult);
            eventPayload.put("auto_reply_threshold", autoReplyThreshold);
            eventPayload.put("suggest_threshold", suggestThreshold);
            eventPayload.put("policy_stage", "8_auto_reply_allowed");
            eventPayload.put("policy_outcome", "send_failed");
            recordAiEvent(t, "ai_agent_escalated", null, "escalate", "send_failed", top.source, top.score, result.error(), eventPayload);
            return;
        }

        if (top.memoryKey != null) {
            dialogAiSolutionMemoryService.markMemoryUsage(top.memoryKey);
        }
        markProcessing(t, "auto_replied", top, null, "auto_reply", "score_above_threshold", sourceHits, mode);
        Map<String, Object> eventPayload = buildRetrievalPayload(sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult);
        eventPayload.put("reply_preview", cut(reply, 300));
        eventPayload.put("auto_reply_threshold", autoReplyThreshold);
        eventPayload.put("suggest_threshold", suggestThreshold);
        eventPayload.put("policy_stage", "8_auto_reply_allowed");
        eventPayload.put("policy_outcome", "auto_reply");
        recordAiEvent(t, "ai_agent_auto_reply_sent", "ai_agent", "auto_reply", "score_above_threshold", top.source, top.score, "Auto reply sent", eventPayload);
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
        for (AiSuggestion s : findSuggestions(t, lastClient, safeLimit)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("source", s.source);
            item.put("title", s.title);
            item.put("score", s.score);
            item.put("score_label", formatScore(s.score));
            item.put("snippet", s.snippet);
            item.put("reply", buildOperatorReplySuggestion(t, lastClient, s));
            item.put("explain", buildSuggestionExplain(t, lastClient, s));
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

    private void markProcessing(String ticketId, String action, AiSuggestion suggestion, String error, String decisionType, String decisionReason, String sourceHits, String mode) {
        dialogAiAssistantStateService.markProcessing(
                ticketId,
                action,
                error,
                suggestion != null ? suggestion.source : null,
                suggestion != null ? suggestion.score : null,
                suggestion != null ? trim(buildDeterministicReply(suggestion)) : null,
                decisionType,
                decisionReason,
                sourceHits,
                mode
        );
    }

    private List<AiSuggestion> findSuggestions(String ticketId, String query, int limit) {
        return mapSuggestions(aiRetrievalService.findSuggestions(ticketId, query, limit));
    }

    private List<AiSuggestion> mapSuggestions(List<AiRetrievalService.Candidate> candidates) {
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

    private String buildDeterministicReply(AiSuggestion s) {
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

    private String buildAutoReply(String ticketId,
                                  String clientMessage,
                                  AiSuggestion suggestion,
                                  AiIntentService.IntentPolicy intentPolicy,
                                  boolean autoReplyRequested) {
        String fallback = buildDeterministicReply(suggestion);
        if (suggestion == null) {
            return fallback;
        }
        if (intentPolicy != null && intentPolicy.requiresOperator() && autoReplyRequested) {
            return fallback;
        }
        String intentKey = firstNonBlank(suggestion.intentKey, intentPolicy != null ? intentPolicy.intentKey() : null);
        AiControlledLlmService.TextResult composed = aiControlledLlmService.composeReply(
                ticketId,
                clientMessage,
                suggestion.snippet,
                suggestion.sourceRef,
                intentKey,
                autoReplyRequested
        );
        return StringUtils.hasText(composed.text()) ? composed.text() : fallback;
    }

    private String buildOperatorReplySuggestion(String ticketId, String clientMessage, AiSuggestion s) {
        String sourceLabel = switch (s.source) {
            case "memory" -> "память подтвержденных решений";
            case "knowledge" -> "база знаний";
            case "tasks" -> "связанные задачи";
            case "history" -> "история похожих диалогов";
            case "applicant_history" -> "история заявителя";
            default -> "доступные данные";
        };
        AiIntentService.IntentPolicy intentPolicy = aiIntentService.resolvePolicy(s.intentKey);
        String prepared = buildAutoReply(ticketId, clientMessage, s, intentPolicy, false);
        return "Подсказка на основе источника \"" + sourceLabel + "\":\n\n" + prepared;
    }

    private String buildSuggestionExplain(String ticketId, String clientMessage, AiSuggestion s) {
        String sourceExplain = switch (String.valueOf(s.source).toLowerCase(Locale.ROOT)) {
            case "memory" -> "Подсказка выбрана из подтвержденной памяти решений.";
            case "knowledge" -> "Подсказка собрана из базы знаний.";
            case "tasks" -> "Подсказка опирается на связанные задачи и инструкции.";
            case "history" -> "Подсказка основана на похожих диалогах.";
            case "applicant_history" -> "Подсказка учитывает историю заявителя.";
            default -> "Подсказка сформирована на основе доступных данных.";
        };
        AiControlledLlmService.TextResult explained = aiControlledLlmService.explainSuggestion(
                ticketId,
                clientMessage,
                s.snippet,
                s.source,
                s.trustLevel,
                s.intentKey,
                s.evidenceCount
        );
        if (StringUtils.hasText(explained.text())) {
            return explained.text();
        }
        String trust = StringUtils.hasText(s.trustLevel) ? s.trustLevel : "unknown";
        return sourceExplain + " Итоговый confidence: " + formatScore(s.score)
                + ". trust=" + trust
                + ", intent=" + firstNonBlank(s.intentKey, "unknown")
                + ", evidence=" + Math.max(1, s.evidenceCount) + ".";
    }

    private void notifyOperatorsEscalation(String ticketId, String msg, String reason) {
        String text = "AI-агент эскалировал обращение " + ticketId + ". " + reason;
        if (StringUtils.hasText(msg)) {
            text += " Вопрос клиента: " + cut(msg, 140);
        }
        notificationService.notifyAllOperators(text, "/dialogs?ticketId=" + ticketId, null);
    }

    private boolean requiresHumanImmediately(String m) {
        String n = normalize(m);
        return n.contains("оператор")
                || n.contains("человек")
                || n.contains("менеджер")
                || n.contains("позвон")
                || n.contains("свяж")
                || n.contains("живой");
    }

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
    private PolicyOrderDecision evaluatePreRoutingPolicy(String ticketId,
                                                         String message,
                                                         String baseMode,
                                                         DialogAiAssistantStateService.DialogAiControl control,
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
            row.put("source_ref", s.sourceRef);
            row.put("intent_key", s.intentKey);
            row.put("slot_signature", s.slotSignature);
            row.put("canonical_key", s.canonicalKey);
            row.put("evidence_count", s.evidenceCount);
            row.put("trace", s.trace);
            row.put("updated_at", s.updatedAt);
            row.put("stale", s.stale ? 1 : 0);
            payload.add(row);
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return null;
        }
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

    private Map<String, Object> buildRetrievalPayload(String sourceHits,
                                                      AiSuggestion top,
                                                      AiRetrievalService.RetrievalResult retrievalResult,
                                                      boolean sensitiveTopic,
                                                      AiControlledLlmService.RewriteResult rewriteResult) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source_hits", sourceHits);
        payload.put("sensitive_topic", sensitiveTopic ? 1 : 0);
        if (rewriteResult != null) {
            payload.put("llm_rewrite_used", rewriteResult.usedLlm() ? 1 : 0);
            payload.put("llm_rewrite_reason", rewriteResult.reason());
            payload.put("llm_rewrite_query", rewriteResult.rewrittenQuery());
            payload.put("llm_variant", rewriteResult.variant());
        }
        if (retrievalResult != null) {
            payload.put("support_count", retrievalResult.consistency().supportCount());
            payload.put("evidence_conflict", retrievalResult.consistency().hasConflict() ? 1 : 0);
            payload.put("retrieval_consistency_reason", retrievalResult.consistency().reason());
            if (retrievalResult.context() != null && retrievalResult.context().intentMatch() != null) {
                String intentKey = trim(retrievalResult.context().intentMatch().intentKey());
                if (intentKey != null) {
                    payload.put("intent_key", intentKey);
                }
                payload.put("intent_confidence", retrievalResult.context().intentMatch().confidence());
                payload.put("intent_schema_valid", retrievalResult.context().intentMatch().schemaValid() ? 1 : 0);
            }
        }
        if (top != null) {
            payload.put("top_candidate_trust", top.trustLevel);
            payload.put("top_candidate_source_type", top.sourceType);
            payload.put("top_candidate_source_ref", top.sourceRef);
            payload.put("top_candidate_intent", top.intentKey);
            payload.put("top_candidate_slot_signature", top.slotSignature);
            payload.put("top_candidate_canonical_key", top.canonicalKey);
            payload.put("top_candidate_evidence_count", top.evidenceCount);
            payload.put("top_candidate_trace", top.trace);
            payload.put("top_candidate_updated_at", top.updatedAt);
            payload.put("top_candidate_is_stale", top.stale ? 1 : 0);
        }
        return payload;
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
        final String sourceRef;
        final String intentKey;
        final String slotSignature;
        final String canonicalKey;
        final int evidenceCount;
        final String trace;
        final String updatedAt;
        final boolean stale;

        AiSuggestion(String source,
                     String title,
                     String snippet,
                     double score,
                     String memoryKey,
                     String status,
                     String trustLevel,
                     String sourceType,
                     String safetyLevel,
                     String sourceRef,
                     String intentKey,
                     String slotSignature,
                     String canonicalKey,
                     int evidenceCount,
                     String trace,
                     String updatedAt,
                     boolean stale) {
            this.source = source;
            this.title = title;
            this.snippet = snippet;
            this.score = score;
            this.memoryKey = memoryKey;
            this.status = status;
            this.trustLevel = trustLevel;
            this.sourceType = sourceType;
            this.safetyLevel = safetyLevel;
            this.sourceRef = sourceRef;
            this.intentKey = intentKey;
            this.slotSignature = slotSignature;
            this.canonicalKey = canonicalKey;
            this.evidenceCount = evidenceCount;
            this.trace = trace;
            this.updatedAt = updatedAt;
            this.stale = stale;
        }
    }
}



