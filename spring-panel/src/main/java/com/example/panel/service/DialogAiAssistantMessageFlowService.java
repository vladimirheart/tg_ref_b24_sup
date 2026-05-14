package com.example.panel.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DialogAiAssistantMessageFlowService {
    private static final int DEFAULT_SUGGESTION_LIMIT = 3;
    private static final String MODE_ASSIST_ONLY = "assist_only";
    private static final String MODE_ESCALATE_ONLY = "escalate_only";

    private final DialogReplyService dialogReplyService;
    private final AiPolicyService aiPolicyService;
    private final AiRetrievalService aiRetrievalService;
    private final AiDecisionService aiDecisionService;
    private final AiInputNormalizerService aiInputNormalizerService;
    private final AiControlledLlmService aiControlledLlmService;
    private final DialogAiSolutionMemoryService dialogAiSolutionMemoryService;
    private final DialogAiAssistantStateService dialogAiAssistantStateService;
    private final DialogAiAssistantConfigService dialogAiAssistantConfigService;
    private final DialogAiAssistantPolicyService dialogAiAssistantPolicyService;
    private final DialogAiAssistantSuggestionService dialogAiAssistantSuggestionService;
    private final DialogAiAssistantEventService dialogAiAssistantEventService;
    private final DialogAiAssistantEscalationService dialogAiAssistantEscalationService;

    public DialogAiAssistantMessageFlowService(DialogReplyService dialogReplyService,
                                               AiPolicyService aiPolicyService,
                                               AiRetrievalService aiRetrievalService,
                                               AiDecisionService aiDecisionService,
                                               AiInputNormalizerService aiInputNormalizerService,
                                               AiControlledLlmService aiControlledLlmService,
                                               DialogAiSolutionMemoryService dialogAiSolutionMemoryService,
                                               DialogAiAssistantStateService dialogAiAssistantStateService,
                                               DialogAiAssistantConfigService dialogAiAssistantConfigService,
                                               DialogAiAssistantPolicyService dialogAiAssistantPolicyService,
                                               DialogAiAssistantSuggestionService dialogAiAssistantSuggestionService,
                                               DialogAiAssistantEventService dialogAiAssistantEventService,
                                               DialogAiAssistantEscalationService dialogAiAssistantEscalationService) {
        this.dialogReplyService = dialogReplyService;
        this.aiPolicyService = aiPolicyService;
        this.aiRetrievalService = aiRetrievalService;
        this.aiDecisionService = aiDecisionService;
        this.aiInputNormalizerService = aiInputNormalizerService;
        this.aiControlledLlmService = aiControlledLlmService;
        this.dialogAiSolutionMemoryService = dialogAiSolutionMemoryService;
        this.dialogAiAssistantStateService = dialogAiAssistantStateService;
        this.dialogAiAssistantConfigService = dialogAiAssistantConfigService;
        this.dialogAiAssistantPolicyService = dialogAiAssistantPolicyService;
        this.dialogAiAssistantSuggestionService = dialogAiAssistantSuggestionService;
        this.dialogAiAssistantEventService = dialogAiAssistantEventService;
        this.dialogAiAssistantEscalationService = dialogAiAssistantEscalationService;
    }

    public void processIncomingClientMessage(String ticketId, String message, String messageType, String attachment) {
        String normalizedTicketId = trim(ticketId);
        if (normalizedTicketId == null) {
            return;
        }
        AiInputNormalizerService.IncomingPayload payload = aiInputNormalizerService.normalizeIncomingPayload(
                normalizedTicketId,
                message,
                messageType,
                attachment
        );
        if (payload == null) {
            clearProcessing(normalizedTicketId, "ignored_media_noise", null, "ignored", "junk_or_noise_media", null);
            recordAiEvent(normalizedTicketId, "ai_agent_message_ignored", null, "ignored", "junk_or_noise_media", null, null,
                    "Ignored non-actionable media/noise message",
                    Map.of("policy_stage", "normalize_input", "policy_outcome", "ignored", "sensitive_topic", 0));
            return;
        }
        String clientMessage = payload.message();

        DialogAiAssistantStateService.DialogAiControl control = dialogAiAssistantStateService.loadDialogControl(normalizedTicketId);
        String mode = dialogAiAssistantConfigService.resolveAgentMode();
        boolean agentEnabled = dialogAiAssistantConfigService.isAgentEnabled();
        DialogAiAssistantPolicyService.PreRoutingDecision preRouting =
                dialogAiAssistantPolicyService.evaluatePreRoutingPolicy(clientMessage, mode, control, agentEnabled);
        if (preRouting.stopProcessing()) {
            clearProcessing(normalizedTicketId, preRouting.action(), preRouting.detail(), preRouting.decisionType(),
                    preRouting.decisionReason(), null, preRouting.effectiveMode());
            if ("escalate".equals(preRouting.decisionType())) {
                dialogAiAssistantEscalationService.notifyOperatorsEscalation(normalizedTicketId, clientMessage, preRouting.detail());
            }
            recordAiEvent(
                    normalizedTicketId,
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
                                    "message_preview", cut(clientMessage, 200),
                                    "ai_disabled", control.aiDisabled(),
                                    "auto_reply_blocked", control.autoReplyBlocked()
                            )
                    )
            );
            return;
        }

        mode = preRouting.effectiveMode();
        markProcessing(normalizedTicketId, "processing", null, null, "processing", "incoming_message", null, mode);
        AiControlledLlmService.RewriteResult rewriteResult = aiControlledLlmService.rewriteQuery(normalizedTicketId, clientMessage);
        String retrievalQuery = firstNonBlank(trim(rewriteResult.effectiveQuery()), clientMessage);
        AiRetrievalService.RetrievalResult retrievalResult = aiRetrievalService.retrieve(normalizedTicketId, retrievalQuery, DEFAULT_SUGGESTION_LIMIT);
        mode = applyIntentModeOverride(mode, retrievalResult.context().intentPolicy());
        List<DialogAiAssistantSuggestionCandidate> suggestions = mapSuggestions(retrievalResult.candidates());
        String sourceHits = dialogAiAssistantEventService.encodeSourceHits(suggestions);

        if (suggestions.isEmpty()) {
            markProcessing(normalizedTicketId, "no_match", null, "No relevant sources found.", "escalate", "no_match", sourceHits, mode);
            dialogAiAssistantEscalationService.notifyOperatorsEscalation(normalizedTicketId, clientMessage, "AI agent did not find a relevant answer.");
            Map<String, Object> eventPayload = dialogAiAssistantEventService.buildRetrievalPayload(
                    sourceHits, null, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult
            );
            eventPayload.put("policy_stage", "4_evidence");
            eventPayload.put("policy_outcome", "insufficient_evidence");
            recordAiEvent(normalizedTicketId, "ai_agent_escalated", null, "escalate", "no_match", null, null, "No relevant sources", eventPayload);
            return;
        }

        DialogAiAssistantSuggestionCandidate top = suggestions.get(0);
        double autoReplyThreshold = dialogAiAssistantConfigService.resolveAutoReplyThreshold();
        double suggestThreshold = dialogAiAssistantConfigService.resolveSuggestThreshold();
        boolean sourceEligibleForAutoReply = aiPolicyService.isAutoReplyEligibleSource(
                top.source(), top.status(), top.trustLevel(), top.sourceType(), top.safetyLevel()
        ) && retrievalResult.context().intentPolicy().autoReplyAllowed()
                && !retrievalResult.context().intentPolicy().requiresOperator();
        AiDecisionService.Decision decision = aiDecisionService.evaluateCandidateDecision(
                mode, top.score(), suggestThreshold, autoReplyThreshold, control.autoReplyBlocked(), sourceEligibleForAutoReply
        );

        if (decision.action() == AiDecisionService.DecisionAction.ESCALATE) {
            markProcessing(normalizedTicketId, decision.processingAction(), top, decision.detail(),
                    decision.decisionType(), decision.decisionReason(), sourceHits, mode);
            dialogAiAssistantEscalationService.notifyOperatorsEscalation(
                    normalizedTicketId,
                    clientMessage,
                    "mode_escalate_only".equals(decision.decisionReason())
                            ? "AI mode is escalate_only."
                            : ("below_suggest_threshold".equals(decision.decisionReason())
                            ? "Low confidence score: " + formatScore(top.score())
                            : "Escalated by decision policy.")
            );
            Map<String, Object> eventPayload = dialogAiAssistantEventService.buildRetrievalPayload(
                    sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult
            );
            eventPayload.put("suggest_threshold", suggestThreshold);
            eventPayload.put("auto_reply_threshold", autoReplyThreshold);
            eventPayload.put("policy_stage", decision.policyStage());
            eventPayload.put("policy_outcome", decision.policyOutcome());
            recordAiEvent(normalizedTicketId, "ai_agent_escalated", null, decision.decisionType(),
                    decision.decisionReason(), top.source(), top.score(), decision.detail(), eventPayload);
            return;
        }
        if (decision.action() == AiDecisionService.DecisionAction.SUGGEST_ONLY) {
            markProcessing(normalizedTicketId, decision.processingAction(), top, null,
                    decision.decisionType(), decision.decisionReason(), sourceHits, mode);
            Map<String, Object> eventPayload = dialogAiAssistantEventService.buildRetrievalPayload(
                    sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult
            );
            eventPayload.put("auto_reply_threshold", autoReplyThreshold);
            eventPayload.put("suggest_threshold", suggestThreshold);
            eventPayload.put("policy_stage", decision.policyStage());
            eventPayload.put("policy_outcome", decision.policyOutcome());
            recordAiEvent(normalizedTicketId, "ai_agent_suggestion_shown", null, decision.decisionType(),
                    decision.decisionReason(), top.source(), top.score(), "Suggestion shown to operator", eventPayload);
            return;
        }

        if (!retrievalResult.consistency().autoReplyAllowed()) {
            String detail = "evidence_conflict".equals(retrievalResult.consistency().reason())
                    ? "Conflicting evidence across top candidates."
                    : "Not enough independent confirmations for auto-reply.";
            String decisionType = retrievalResult.consistency().hasConflict() ? "escalate" : "suggest_only";
            String decisionReason = retrievalResult.consistency().reason();
            String action = retrievalResult.consistency().hasConflict() ? "escalated" : "suggest_only";
            markProcessing(normalizedTicketId, action, top, detail, decisionType, decisionReason, sourceHits, mode);
            if (retrievalResult.consistency().hasConflict()) {
                dialogAiAssistantEscalationService.notifyOperatorsEscalation(normalizedTicketId, clientMessage, detail);
            }
            Map<String, Object> eventPayload = dialogAiAssistantEventService.buildRetrievalPayload(
                    sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult
            );
            eventPayload.put("auto_reply_threshold", autoReplyThreshold);
            eventPayload.put("suggest_threshold", suggestThreshold);
            eventPayload.put("policy_stage", "6_consistency");
            eventPayload.put("policy_outcome", retrievalResult.consistency().hasConflict()
                    ? "blocked_by_conflict" : "blocked_by_insufficient_confirmation");
            recordAiEvent(
                    normalizedTicketId,
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

        DialogAiAssistantConfigService.AutoReplyGuard guard = dialogAiAssistantConfigService.evaluateAutoReplyGuard(normalizedTicketId);
        if (!guard.allowed()) {
            markProcessing(normalizedTicketId, "auto_reply_suppressed", top, guard.reason(), "suppressed", "loop_guard", sourceHits, mode);
            dialogAiAssistantEscalationService.notifyOperatorsEscalation(normalizedTicketId, clientMessage, "Auto-reply suppressed by loop guard: " + guard.reason());
            Map<String, Object> eventPayload = dialogAiAssistantEventService.buildRetrievalPayload(
                    sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult
            );
            eventPayload.put("auto_reply_threshold", autoReplyThreshold);
            eventPayload.put("suggest_threshold", suggestThreshold);
            eventPayload.put("policy_stage", "7_loop_guard");
            eventPayload.put("policy_outcome", "suppressed");
            recordAiEvent(normalizedTicketId, "ai_agent_decision_made", null, "suppressed", "loop_guard",
                    top.source(), top.score(), guard.reason(), eventPayload);
            return;
        }

        String reply = dialogAiAssistantSuggestionService.buildAutoReply(
                normalizedTicketId, clientMessage, top, retrievalResult.context().intentPolicy(), true
        );
        DialogReplyService.DialogReplyResult result = dialogReplyService.sendReply(normalizedTicketId, reply, null, null, "ai_agent");
        if (!result.success()) {
            markProcessing(normalizedTicketId, "send_failed", top, result.error(), "escalate", "send_failed", sourceHits, mode);
            dialogAiAssistantEscalationService.notifyOperatorsEscalation(normalizedTicketId, clientMessage, "Failed to send AI reply: " + result.error());
            Map<String, Object> eventPayload = dialogAiAssistantEventService.buildRetrievalPayload(
                    sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult
            );
            eventPayload.put("auto_reply_threshold", autoReplyThreshold);
            eventPayload.put("suggest_threshold", suggestThreshold);
            eventPayload.put("policy_stage", "8_auto_reply_allowed");
            eventPayload.put("policy_outcome", "send_failed");
            recordAiEvent(normalizedTicketId, "ai_agent_escalated", null, "escalate", "send_failed",
                    top.source(), top.score(), result.error(), eventPayload);
            return;
        }

        if (top.memoryKey() != null) {
            dialogAiSolutionMemoryService.markMemoryUsage(top.memoryKey());
        }
        markProcessing(normalizedTicketId, "auto_replied", top, null, "auto_reply", "score_above_threshold", sourceHits, mode);
        Map<String, Object> eventPayload = dialogAiAssistantEventService.buildRetrievalPayload(
                sourceHits, top, retrievalResult, preRouting.sensitiveMatch().matched(), rewriteResult
        );
        eventPayload.put("reply_preview", cut(reply, 300));
        eventPayload.put("auto_reply_threshold", autoReplyThreshold);
        eventPayload.put("suggest_threshold", suggestThreshold);
        eventPayload.put("policy_stage", "8_auto_reply_allowed");
        eventPayload.put("policy_outcome", "auto_reply");
        recordAiEvent(normalizedTicketId, "ai_agent_auto_reply_sent", "ai_agent", "auto_reply",
                "score_above_threshold", top.source(), top.score(), "Auto reply sent", eventPayload);
    }

    private void clearProcessing(String ticketId, String action, String error, String decisionType, String decisionReason, String sourceHits) {
        clearProcessing(ticketId, action, error, decisionType, decisionReason, sourceHits, dialogAiAssistantConfigService.resolveAgentMode());
    }

    private void clearProcessing(String ticketId, String action, String error, String decisionType, String decisionReason, String sourceHits, String mode) {
        dialogAiAssistantStateService.clearProcessing(ticketId, action, error, decisionType, decisionReason, sourceHits, mode);
    }

    private void markProcessing(String ticketId,
                                String action,
                                DialogAiAssistantSuggestionCandidate suggestion,
                                String error,
                                String decisionType,
                                String decisionReason,
                                String sourceHits,
                                String mode) {
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

    private List<DialogAiAssistantSuggestionCandidate> mapSuggestions(List<AiRetrievalService.Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
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

    private String cut(String text, int len) {
        String normalized = trim(text);
        if (normalized == null) {
            return "";
        }
        String compact = normalized.replaceAll("\\s+", " ").trim();
        return compact.length() <= len ? compact : compact.substring(0, Math.max(0, len - 3)) + "...";
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String formatScore(double score) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0d, Math.min(1d, score)));
    }
}
